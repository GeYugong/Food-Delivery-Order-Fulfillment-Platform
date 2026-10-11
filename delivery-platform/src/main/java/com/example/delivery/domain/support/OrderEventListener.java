package com.example.delivery.domain.support;

/**
 * 订单事件监听器（观察者模式 Observer 的「观察者 / Observer」角色）。
 *
 * <p><b>设计模式：观察者模式（Observer）</b></p>
 *
 * <p>实现类代表一个个下游系统：用户短信、商家端、骑手端、统计报表、审计日志。
 * 它们彼此不知道对方存在，只依赖本接口与 {@link OrderEvent}，
 * 因此可以随时插拔（订阅 / 退订）。</p>
 *
 * @author 课程大作业
 */
@FunctionalInterface
public interface OrderEventListener {

    /**
     * 处理事件。
     *
     * <p>实现内部不应抛出异常来中断通知链——虽然
     * {@link DomainEventPublisher#publish(OrderEvent)} 会隔离异常，
     * 但主动吞掉并记录日志更符合「通知失败不影响主业务」的语义。</p>
     *
     * @param event 订单事件
     */
    void onEvent(OrderEvent event);

    /**
     * 监听器名称。
     *
     * @return 例如 {@code "用户短信通知"}
     */
    default String name() {
        return getClass().getSimpleName();
    }
}
