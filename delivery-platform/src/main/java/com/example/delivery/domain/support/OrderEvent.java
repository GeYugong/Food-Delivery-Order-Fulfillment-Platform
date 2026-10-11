package com.example.delivery.domain.support;

/**
 * 订单事件（观察者模式 Observer 的「事件对象」角色）。
 *
 * <p><b>为什么事件要是不可变值对象：</b>同一个事件会被多个监听器并发处理，
 * 如果监听器能改动事件内容，后面的监听器看到的就是「被改过的历史」，
 * 排查问题时会非常痛苦。因此用 record 保证不可变。</p>
 *
 * <p>事件类型（{@link #type()}）与订单状态（{@link #orderStatus()}）分开记录，
 * 因为「一个状态可能对应多个事件」（例如已支付状态可能由支付成功或补单产生），
 * 事件类型才是下游真正关心的语义。</p>
 *
 * @param type        事件类型，例如 {@code "OrderPaid"}、{@code "OrderDispatched"}
 * @param orderId     订单号
 * @param orderStatus 事件发生时的订单状态枚举名
 * @param payload     附加说明（金额、骑手、原因等），可为空
 * @param occurredAtMillis 事件发生时刻（毫秒时间戳，便于排序与报表）
 * @author 课程大作业
 */
public record OrderEvent(String type, String orderId, String orderStatus,
                         String payload, long occurredAtMillis) {

    /** 事件类型：订单已提交。 */
    public static final String ORDER_SUBMITTED = "OrderSubmitted";
    /** 事件类型：支付成功。 */
    public static final String ORDER_PAID = "OrderPaid";
    /** 事件类型：派单成功。 */
    public static final String ORDER_DISPATCHED = "OrderDispatched";
    /** 事件类型：骑手取餐，开始配送。 */
    public static final String ORDER_DELIVERING = "OrderDelivering";
    /** 事件类型：订单送达完成。 */
    public static final String ORDER_COMPLETED = "OrderCompleted";
    /** 事件类型：订单取消。 */
    public static final String ORDER_CANCELLED = "OrderCancelled";
    /** 事件类型：派单失败。 */
    public static final String ORDER_DISPATCH_FAILED = "OrderDispatchFailed";

    /**
     * 便捷工厂：以当前时间创建事件。
     *
     * @param type        事件类型
     * @param orderId     订单号
     * @param orderStatus 订单状态名
     * @param payload     附加说明
     * @return 事件对象
     */
    public static OrderEvent of(String type, String orderId, String orderStatus, String payload) {
        return new OrderEvent(type, orderId, orderStatus, payload, System.currentTimeMillis());
    }

    /** @return 中文事件名，用于控制台打印 */
    public String label() {
        return switch (type) {
            case ORDER_SUBMITTED -> "订单已提交";
            case ORDER_PAID -> "支付成功";
            case ORDER_DISPATCHED -> "派单成功";
            case ORDER_DELIVERING -> "骑手已取餐";
            case ORDER_COMPLETED -> "订单已送达";
            case ORDER_CANCELLED -> "订单已取消";
            case ORDER_DISPATCH_FAILED -> "派单失败";
            default -> type;
        };
    }

    /** @return 人类可读描述 */
    public String describe() {
        return String.format("[%s] 订单=%s 状态=%s %s", label(), orderId, orderStatus,
                payload == null || payload.isBlank() ? "" : "｜" + payload);
    }
}
