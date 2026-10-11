package com.example.delivery.infrastructure.event;

import com.example.delivery.domain.support.OrderEvent;
import com.example.delivery.domain.support.OrderEventListener;

import java.util.ArrayList;
import java.util.List;

/**
 * 订单事件监听器集合（观察者模式 Observer 的「具体观察者」实现）。
 *
 * <p><b>设计模式：观察者模式（Observer）</b></p>
 *
 * <p><b>为什么把它们放在一个文件里：</b>它们是同一类角色的 5 个短实现
 * （用户短信、商家端、骑手端、统计报表、审计日志）。放在一起便于一眼看清
 * 「同一个事件被多少个互不相关的下游消费」，这正是观察者模式要表达的价值。</p>
 *
 * <p><b>设计要点：</b></p>
 * <ul>
 *   <li>每个监听器只关心自己感兴趣的事件类型（如骑手端只关心派单/取餐/送达），
 *       其余事件直接忽略——「按需订阅」让新增事件类型不会打扰老监听器；</li>
 *   <li>监听器之间<b>零引用</b>，彼此不知道对方存在；</li>
 *   <li>监听器内部不抛异常（通知失败不能拖垮主业务），需要时只记录日志。</li>
 * </ul>
 *
 * @author 课程大作业
 */
public final class OrderEventListeners {

    /** 工具类不允许实例化。 */
    private OrderEventListeners() {
    }

    /**
     * 用户短信通知：只关心自己订单的关键节点。
     *
     * <p>真实系统会调用短信网关，这里打印模拟短信内容。</p>
     */
    public static class UserSmsListener implements OrderEventListener {

        /** 已发送的短信内容（便于控制台回放）。 */
        private final List<String> sentMessages = new ArrayList<>();

        @Override
        public String name() {
            return "用户短信通知";
        }

        @Override
        public void onEvent(OrderEvent event) {
            String template = switch (event.type()) {
                case OrderEvent.ORDER_SUBMITTED -> "【外卖平台】您的订单 %s 已提交，请尽快完成支付。";
                case OrderEvent.ORDER_PAID -> "【外卖平台】订单 %s 支付成功，商家正在备餐。";
                case OrderEvent.ORDER_DISPATCHED -> "【外卖平台】订单 %s 已由骑手接单，正在赶来。";
                case OrderEvent.ORDER_COMPLETED -> "【外卖平台】订单 %s 已送达，感谢您的使用。";
                case OrderEvent.ORDER_CANCELLED -> "【外卖平台】订单 %s 已取消，如已支付将原路退款。";
                default -> null;
            };
            if (template != null) {
                sentMessages.add(String.format(template, event.orderId()));
            }
        }

        /** @return 已发送短信列表 */
        public List<String> sentMessages() {
            return List.copyOf(sentMessages);
        }
    }

    /**
     * 商家端提醒：只关心需要商家动作的事件（新订单、取消、派单）。
     */
    public static class MerchantPortalListener implements OrderEventListener {

        /** 商家端消息列表。 */
        private final List<String> messages = new ArrayList<>();

        @Override
        public String name() {
            return "商家端提醒";
        }

        @Override
        public void onEvent(OrderEvent event) {
            switch (event.type()) {
                case OrderEvent.ORDER_SUBMITTED ->
                        messages.add("【商家端】新订单 " + event.orderId() + "，等待支付");
                case OrderEvent.ORDER_PAID ->
                        messages.add("【商家端】订单 " + event.orderId() + " 已支付，请开始备餐");
                case OrderEvent.ORDER_CANCELLED ->
                        messages.add("【商家端】订单 " + event.orderId() + " 已取消，请停止备餐");
                case OrderEvent.ORDER_DISPATCH_FAILED ->
                        messages.add("【商家端】订单 " + event.orderId() + " 暂无骑手，请耐心等待");
                default -> {
                    // 其他事件与商家无关
                }
            }
        }

        /** @return 商家端消息列表 */
        public List<String> messages() {
            return List.copyOf(messages);
        }
    }

    /**
     * 骑手端推送：只关心派单、取餐、送达。
     */
    public static class RiderAppListener implements OrderEventListener {

        /** 骑手端推送列表。 */
        private final List<String> pushes = new ArrayList<>();

        @Override
        public String name() {
            return "骑手端推送";
        }

        @Override
        public void onEvent(OrderEvent event) {
            switch (event.type()) {
                case OrderEvent.ORDER_DISPATCHED ->
                        pushes.add("【骑手端】您有新订单 " + event.orderId() + "，" + event.payload());
                case OrderEvent.ORDER_DELIVERING ->
                        pushes.add("【骑手端】订单 " + event.orderId() + " 已取餐，请安全配送");
                case OrderEvent.ORDER_COMPLETED ->
                        pushes.add("【骑手端】订单 " + event.orderId() + " 已送达，负载已释放");
                default -> {
                    // 其他事件与骑手无关
                }
            }
        }

        /** @return 推送列表 */
        public List<String> pushes() {
            return List.copyOf(pushes);
        }
    }

    /**
     * 统计报表监听器：把所有事件按类型累计，实时维护报表口径。
     */
    public static class ReportListener implements OrderEventListener {

        /** 事件类型 → 次数。 */
        private final java.util.Map<String, Integer> counters = new java.util.LinkedHashMap<>();

        @Override
        public String name() {
            return "统计报表采集";
        }

        @Override
        public void onEvent(OrderEvent event) {
            counters.merge(event.type(), 1, Integer::sum);
        }

        /** @return 事件计数快照 */
        public java.util.Map<String, Integer> counters() {
            return java.util.Collections.unmodifiableMap(counters);
        }

        /** @return 一行式摘要 */
        public String summary() {
            StringBuilder sb = new StringBuilder("事件统计：");
            counters.forEach((type, count) -> sb.append(type).append("=").append(count).append("  "));
            return sb.toString().trim();
        }
    }

    /**
     * 审计日志监听器：把所有事件按时间顺序完整留痕（合规要求）。
     *
     * <p>同时依赖 {@link OrderStore} 之外的独立存储即可；这里只保留内存列表，
     * 演示「审计与业务解耦」：审计监听器挂掉不影响下单。</p>
     */
    public static class AuditLogListener implements OrderEventListener {

        /** 审计日志（按发生顺序）。 */
        private final List<String> logs = new ArrayList<>();

        @Override
        public String name() {
            return "审计日志";
        }

        @Override
        public void onEvent(OrderEvent event) {
            logs.add(String.format("%d | %s | %s | %s | %s",
                    event.occurredAtMillis(), event.type(), event.orderId(),
                    event.orderStatus(), event.payload()));
        }

        /** @return 审计日志列表 */
        public List<String> logs() {
            return List.copyOf(logs);
        }
    }

    /**
     * 控制台回显监听器：把事件实时打印出来，便于课堂演示「事件通知」环节。
     *
     * <p>它是最典型的「新增一个观察者只需一个类」的例子：
     * 不修改任何已有代码，就多了一个通知渠道。</p>
     */
    public static class ConsoleEchoListener implements OrderEventListener {

        @Override
        public String name() {
            return "控制台事件回显";
        }

        @Override
        public void onEvent(OrderEvent event) {
            System.out.println("      ↳ [事件] " + event.describe());
        }
    }
}
