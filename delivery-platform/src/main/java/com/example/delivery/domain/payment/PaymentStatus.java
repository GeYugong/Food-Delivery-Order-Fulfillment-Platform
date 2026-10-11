package com.example.delivery.domain.payment;

/**
 * 支付状态枚举。
 *
 * <p><b>设计模式关联：状态模式（State）思想在支付子域的复用</b></p>
 * <p>支付是异步过程（发起 → 处理中 → 成功/失败/已退款），
 * 用枚举把状态显式化，避免用 {@code boolean paid} 压扁语义
 * （布尔无法表达「已退款」与「失败」的区别）。</p>
 *
 * @author 课程大作业
 */
public enum PaymentStatus {

    /** 已发起，渠道处理中 */
    PENDING("处理中"),

    /** 支付成功 */
    SUCCESS("支付成功"),

    /** 支付失败（余额不足、渠道拒付等） */
    FAILED("支付失败"),

    /** 已退款（订单取消时触发） */
    REFUNDED("已退款");

    private final String label;

    PaymentStatus(String label) {
        this.label = label;
    }

    /** @return 中文显示名 */
    public String label() {
        return label;
    }
}
