package com.example.delivery.domain.support;

import com.example.delivery.domain.order.Order;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 订单领域事件发布器（观察者模式 Observer 的「主题 / Subject」角色）。
 *
 * <p><b>设计模式：观察者模式（Observer）</b></p>
 *
 * <p><b>为什么需要它：</b>订单状态每变一次，都要通知多个互不相关的下游：
 * 用户短信、商家端、骑手端、统计报表、审计日志、风控。
 * 如果把这些调用写在订单服务里，订单服务会被迫依赖所有下游模块，
 * 每加一个通知渠道就要改一次订单服务。</p>
 *
 * <p>用发布/订阅后：订单服务只做「发布事件」这一件事，
 * 谁关心谁自己订阅；新增渠道 = 新增一个 {@link OrderEventListener} 并注册。</p>
 *
 * <p><b>并发细节：</b>{@link #publish(OrderEvent)} 遍历订阅者列表的**快照副本**，
 * 避免监听器在回调里订阅/退订导致 {@code ConcurrentModificationException}；
 * 单个监听器抛异常不会影响其他监听器（隔离故障，通知失败不能拖垮主业务）。</p>
 *
 * @author 课程大作业
 */
public class DomainEventPublisher {

    /** 监听器列表（用写时复制语义的同步列表，读多写少场景足够）。 */
    private final List<OrderEventListener> listeners = Collections.synchronizedList(new ArrayList<>());

    /** 事件流水，便于控制台回放「事件通知」环节。 */
    private final List<OrderEvent> published = Collections.synchronizedList(new ArrayList<>());

    /**
     * 订阅订单事件。
     *
     * @param listener 监听器
     */
    public void subscribe(OrderEventListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * 退订。
     *
     * @param listener 监听器
     * @return 是否成功移除
     */
    public boolean unsubscribe(OrderEventListener listener) {
        return listeners.remove(listener);
    }

    /**
     * 发布事件。
     *
     * @param event 事件
     */
    public void publish(OrderEvent event) {
        published.add(event);
        List<OrderEventListener> snapshot;
        synchronized (listeners) {
            snapshot = new ArrayList<>(listeners);
        }
        for (OrderEventListener listener : snapshot) {
            try {
                listener.onEvent(event);
            } catch (RuntimeException e) {
                // 通知失败不影响主业务：记录后继续通知其他订阅者
                System.err.printf("[事件通知] 监听器[%s]处理事件[%s]失败：%s%n",
                        listener.name(), event.type(), e.getMessage());
            }
        }
    }

    /**
     * 便捷方法：订单状态变化时发布事件。
     *
     * @param order   订单
     * @param eventType 事件类型
     * @param payload 附加说明
     */
    public void publishFor(Order order, String eventType, String payload) {
        publish(OrderEvent.of(eventType, order.orderId(), order.status().name(), payload));
    }

    /** @return 当前订阅者数量 */
    public int listenerCount() {
        return listeners.size();
    }

    /** @return 订阅者名称列表 */
    public List<String> listenerNames() {
        synchronized (listeners) {
            return listeners.stream().map(OrderEventListener::name).toList();
        }
    }

    /** @return 只读的事件流水 */
    public List<OrderEvent> publishedEvents() {
        synchronized (published) {
            return List.copyOf(published);
        }
    }
}
