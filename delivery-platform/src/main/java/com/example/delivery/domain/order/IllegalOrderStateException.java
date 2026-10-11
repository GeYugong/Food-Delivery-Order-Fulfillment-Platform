package com.example.delivery.domain.order;

/**
 * 订单状态非法流转异常。
 *
 * <p><b>设计模式关联：状态模式（State）的错误语义</b></p>
 * <p><b>为什么需要它：</b>状态机把「非法流转」变成了可预期的业务错误，
 * 而不是任状态被随意赋值的隐性 Bug。它在职责链（校验链）之外单独存在，
 * 因为「状态不对」属于聚合自身的不变量，不应该由外部校验器负责。</p>
 *
 * @author 课程大作业
 */
public class IllegalOrderStateException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 发生异常时的当前状态。 */
    private final OrderStatus current;

    /** 期望迁移到的目标状态。 */
    private final OrderStatus target;

    /**
     * 构造异常。
     *
     * @param orderId 订单号
     * @param current 当前状态
     * @param target  目标状态
     * @param message 补充说明
     */
    public IllegalOrderStateException(String orderId, OrderStatus current, OrderStatus target, String message) {
        super(String.format("订单[%s] 状态非法流转：%s -> %s（%s）",
                orderId, current == null ? "未知" : current.label(),
                target == null ? "未知" : target.label(), message));
        this.current = current;
        this.target = target;
    }

    /** @return 当前状态 */
    public OrderStatus current() {
        return current;
    }

    /** @return 目标状态 */
    public OrderStatus target() {
        return target;
    }
}
