package com.example.patterns.behavioral.observer;

import com.example.patterns.PatternDemo;

import java.util.ArrayList;
import java.util.List;

/**
 * 观察者模式（Observer）——订单事件的多渠道通知。
 *
 * <p><b>用了什么模式：</b>{@link OrderEventPublisher} 是「主题 / 被观察者」，内部维护一份订阅者名单；
 * {@link OrderEventListener} 是「观察者」接口，五个具体监听器各自负责一个通知渠道。
 * 主题在事件发生时遍历订阅者名单逐个回调，回调时遍历的是名单<b>副本</b>，
 * 因此监听器在回调里退订自己也不会抛 {@code ConcurrentModificationException}。</p>
 *
 * <p><b>为什么用：</b>一次「支付成功」要同时通知用户（短信）、商家（商家端）、骑手（骑手端），
 * 还要写报表、写审计日志。这些通知彼此独立、失败互不影响，而且渠道还会不断增加
 * （后面可能加微信推送、加数据仓库同步）。观察者模式让订单服务只负责「发布一个事件」，
 * 至于谁关心、怎么处理，全部交给订阅关系决定。</p>
 *
 * <p><b>不用会怎样：</b>很多课程项目会直接在订单服务里写死：
 * {@code if (smsEnabled) smsService.send(); merchantPortal.push(); riderApp.push(); report.count(); audit.log();}
 * —— 订单服务被迫认识所有下游，任何新渠道都要改订单服务并重新测试全链路，
 * 这就是典型的「坏味道」：一处业务改动牵动所有通知方，且通知异常会把下单主流程一起拖垮。</p>
 *
 * <p>本 Demo 手写极简事件总线，不使用任何第三方库，只依赖 JDK 标准库。</p>
 *
 * @author 课程大作业
 */
public class ObserverDemo implements PatternDemo {

    /** 菜单短编号。 */
    @Override
    public String code() {
        return "13";
    }

    /** 分类：行为型。 */
    @Override
    public String category() {
        return "行为型";
    }

    /** 模式中文名。 */
    @Override
    public String name() {
        return "观察者模式";
    }

    /** 对应外卖业务场景。 */
    @Override
    public String description() {
        return "外卖订单事件通知：一次支付成功同时通知用户、商家、骑手、报表与审计，渠道可随时订阅或退订";
    }

    /** 独立运行入口，满足「单独跑」与「菜单跑」结果一致。 */
    public static void main(String[] args) {
        new ObserverDemo().run();
    }

    @Override
    public void run() {
        printTitle();
        printRoles();
        runProcess();
        printConclusion();
    }

    // ------------------------------------------------------------------
    // 分节打印
    // ------------------------------------------------------------------

    private void printTitle() {
        System.out.println("==================================================");
        System.out.println("  [" + code() + "] " + name() + "（" + category() + "）");
        System.out.println("  " + description());
        System.out.println("==================================================");
    }

    private void printRoles() {
        System.out.println();
        System.out.println("【参与角色】");
        System.out.println("  1) 主题 / 被观察者 ：OrderEventPublisher —— subscribe / unsubscribe / publish");
        System.out.println("  2) 事件对象         ：OrderEvent —— eventType + orderId + payload");
        System.out.println("  3) 观察者接口       ：OrderEventListener —— name() + onEvent(OrderEvent)");
        System.out.println("  4) 具体观察者 ×5    ：UserSmsListener / MerchantPortalListener / RiderAppListener");
        System.out.println("                        / ReportListener / AuditLogListener");
        System.out.println();
    }

    private void printConclusion() {
        System.out.println();
        System.out.println("【结论】");
        System.out.println("  1) 观察者模式把「通知」与「业务」解耦：订单服务只管 publish，不知道也不需要知道有谁在听。");
        System.out.println("  2) 新增通知渠道只需新增一个监听器并 subscribe，订单服务代码一行都不用改，符合开闭原则。");
        System.out.println("  3) 对比坏味道：若把 5 个渠道写成 5 个 if 或 5 次直接调用，订单服务会认识所有下游，");
        System.out.println("     任何渠道变更都要改主流程、重测主流程，且一个渠道抛异常会连带影响下单结果。");
        System.out.println("  4) publish 遍历订阅者名单的副本，所以监听器在回调中退订自己也不会触发并发修改异常。");
    }

    // ------------------------------------------------------------------
    // 演示流程
    // ------------------------------------------------------------------

    private void runProcess() {
        System.out.println("【调用过程】");

        OrderEventPublisher publisher = new OrderEventPublisher();
        UserSmsListener sms = new UserSmsListener();
        MerchantPortalListener merchant = new MerchantPortalListener();
        RiderAppListener rider = new RiderAppListener();
        ReportListener report = new ReportListener();
        AuditLogListener audit = new AuditLogListener();

        System.out.println();
        System.out.println("  1) 注册 5 个订阅者：");
        publisher.subscribe(sms);
        publisher.subscribe(merchant);
        publisher.subscribe(rider);
        publisher.subscribe(report);
        publisher.subscribe(audit);
        System.out.println("     当前订阅者数量 = " + publisher.subscriberCount()
                + "，名单 = " + publisher.subscriberNames());

        System.out.println();
        System.out.println("  2) 发布事件「支付成功」，5 个订阅者都应收到：");
        publisher.publish(new OrderEvent("支付成功", "FD20240607001", "金额=￥38.50，微信支付"));

        System.out.println();
        System.out.println("  3) 退订 RiderAppListener（骑手端服务临时下线）：");
        publisher.unsubscribe(rider);
        System.out.println("     当前订阅者数量 = " + publisher.subscriberCount()
                + "，名单 = " + publisher.subscriberNames());
        boolean removedAgain = publisher.unsubscribe(rider);
        System.out.println("     再次退订同一个监听器是幂等的，不报错，返回 = " + removedAgain);

        System.out.println();
        System.out.println("  4) 再发布事件「订单已送达」，应只有 4 个订阅者收到：");
        publisher.publish(new OrderEvent("订单已送达", "FD20240607001", "签收人=本人，用时 28 分钟"));

        System.out.println();
        System.out.println("  5) 订阅者数量变化对比：");
        System.out.println("     - 支付成功：5 个订阅者（短信 / 商家端 / 骑手端 / 报表 / 审计）");
        System.out.println("     - 订单已送达：4 个订阅者（骑手端已退订，其余照旧）");
    }

    // ==================================================================
    // 角色实现（同文件包级类，避免影响其他同学正在写的包）
    // ==================================================================

    /**
     * 事件对象：一次订单事件的只读载体。
     *
     * <p>字段 final，事件在传播过程中不会被任何一个监听器改写，避免「前面的监听器改了事件、
     * 后面的监听器读到脏数据」这种隐蔽 bug。</p>
     */
    static final class OrderEvent {

        private final String eventType;
        private final String orderId;
        private final String payload;

        OrderEvent(String eventType, String orderId, String payload) {
            this.eventType = eventType;
            this.orderId = orderId;
            this.payload = payload;
        }

        String eventType() {
            return eventType;
        }

        String orderId() {
            return orderId;
        }

        String payload() {
            return payload;
        }

        @Override
        public String toString() {
            return "[" + eventType + "] 订单 " + orderId + " : " + payload;
        }
    }

    /**
     * 观察者接口：所有通知渠道统一实现它，主题只依赖这个抽象。
     *
     * <p>接口只有一个回调方法，因此可以用 Lambda 临时注册一个「匿名监听器」，
     * 演示时很方便，不需要为一次性通知单独建类。</p>
     */
    interface OrderEventListener {

        /** 监听器名称，用于演示时打印订阅者名单。 */
        String name();

        /** 事件回调：主题发布事件时逐个调用。 */
        void onEvent(OrderEvent event);
    }

    /**
     * 主题 / 被观察者：极简事件总线（手写，无第三方依赖）。
     *
     * <p>只做三件事：登记订阅者、摘除订阅者、把事件分发给名单副本。
     * 它完全不认识订单业务，因此可以被任何业务复用。</p>
     */
    static class OrderEventPublisher {

        /** 订阅者名单；用 List 保证通知顺序与注册顺序一致，便于课堂演示。 */
        private final List<OrderEventListener> listeners = new ArrayList<>();

        /** 订阅：重复订阅同一个监听器只保留一份，避免重复通知。 */
        void subscribe(OrderEventListener listener) {
            if (listener != null && !listeners.contains(listener)) {
                listeners.add(listener);
            }
        }

        /** 退订：返回是否真的移除了，便于演示幂等语义。 */
        boolean unsubscribe(OrderEventListener listener) {
            return listeners.remove(listener);
        }

        /**
         * 发布事件给所有订阅者。
         *
         * <p><b>关键实现细节：</b>遍历的是 {@code new ArrayList<>(listeners)} 这份副本。
         * 如果直接遍历原名单，某个监听器在回调里 {@code unsubscribe(自己)} 就会改变集合结构，
         * 立刻抛 {@code ConcurrentModificationException} 并中断后续通知。</p>
         */
        void publish(OrderEvent event) {
            if (event == null) {
                return;
            }
            for (OrderEventListener listener : new ArrayList<>(listeners)) {
                try {
                    listener.onEvent(event);
                } catch (RuntimeException ex) {
                    // 一个渠道失败不应影响其他渠道，也不应影响下单主流程
                    System.out.println("     [!] " + listener.name() + " 处理失败，已隔离：" + ex.getMessage());
                }
            }
        }

        /** 订阅者数量，用于演示退订前后的变化。 */
        int subscriberCount() {
            return listeners.size();
        }

        /** 订阅者名单，用于演示通知范围变化。 */
        List<String> subscriberNames() {
            List<String> names = new ArrayList<>();
            for (OrderEventListener listener : listeners) {
                names.add(listener.name());
            }
            return names;
        }
    }

    /** 具体观察者：用户短信通知。 */
    static class UserSmsListener implements OrderEventListener {

        @Override
        public String name() { return "用户短信"; }

        @Override
        public void onEvent(OrderEvent event) {
            System.out.println("     [用户短信] 尊敬的顾客，您的订单有新进展 → " + event);
        }
    }

    /** 具体观察者：商家端推送。 */
    static class MerchantPortalListener implements OrderEventListener {

        @Override
        public String name() { return "商家端"; }

        @Override
        public void onEvent(OrderEvent event) {
            System.out.println("     [商家端] 收到订单事件，请及时出餐/核对 → " + event);
        }
    }

    /** 具体观察者：骑手端推送（本 Demo 稍后会退订它）。 */
    static class RiderAppListener implements OrderEventListener {

        @Override
        public String name() { return "骑手端"; }

        @Override
        public void onEvent(OrderEvent event) {
            System.out.println("     [骑手端] 新任务提醒 → " + event);
        }
    }

    /** 具体观察者：统计报表，只关心怎么累加指标。 */
    static class ReportListener implements OrderEventListener {

        @Override
        public String name() { return "统计报表"; }

        @Override
        public void onEvent(OrderEvent event) {
            System.out.println("     [统计报表] 指标+1，事件类型=" + event.eventType());
        }
    }

    /** 具体观察者：审计日志，落库留痕。 */
    static class AuditLogListener implements OrderEventListener {

        @Override
        public String name() { return "审计日志"; }

        @Override
        public void onEvent(OrderEvent event) {
            System.out.println("     [审计日志] 已留痕 → " + event);
        }
    }
}
