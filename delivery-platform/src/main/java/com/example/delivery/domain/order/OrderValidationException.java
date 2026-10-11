package com.example.delivery.domain.order;

/**
 * 订单业务校验失败异常。
 *
 * <p><b>设计模式关联：职责链模式（Chain of Responsibility）的统一出口</b></p>
 * <p><b>为什么需要它：</b>下单前要经过「非空 → 起送价 → 库存 → 地址 → 风控 → 限购」等一串校验器，
 * 每个校验器都可能否决订单。统一抛这一个异常，调用方（外观/应用服务）只需 catch 一次，
 * 就能把「被哪一环、因为什么否决」原样告诉用户，控制台演示时也能打印出完整的否决链。</p>
 *
 * @author 课程大作业
 */
public class OrderValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 否决该订单的校验器名称（便于定位是链条中的哪一环）。 */
    private final String validatorName;

    /**
     * 构造异常。
     *
     * @param validatorName 校验器名称，例如 {@code "起送价校验"}
     * @param message       否决原因
     */
    public OrderValidationException(String validatorName, String message) {
        super(String.format("订单校验未通过[%s]：%s", validatorName, message));
        this.validatorName = validatorName;
    }

    /** @return 校验器名称 */
    public String validatorName() {
        return validatorName;
    }
}
