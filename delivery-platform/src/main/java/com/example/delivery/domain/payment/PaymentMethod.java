package com.example.delivery.domain.payment;

/**
 * 支付方式枚举。
 *
 * <p><b>设计模式关联：适配器模式（Adapter）的「被适配者」标识</b></p>
 * <p>每一种支付方式在真实系统里都对应一个不同协议的外部渠道，
 * 这里用枚举统一标识，由对应适配器实现 {@code PaymentGateway} 接口，
 * 上层只需传枚举，不需要知道渠道细节。</p>
 *
 * @author 课程大作业
 */
public enum PaymentMethod {

    /** 平台余额支付：无需第三方，直接扣减用户余额 */
    WALLET("余额支付", "WALLET"),

    /** 微信支付：模拟第三方 SDK 的异构接口 */
    WECHAT_PAY("微信支付", "WX"),

    /** 支付宝：模拟第三方 SDK 的异构接口 */
    ALIPAY("支付宝", "ALI"),

    /** 银行卡支付：模拟银行网关 */
    BANK_CARD("银行卡", "BANK");

    private final String label;
    private final String code;

    PaymentMethod(String label, String code) {
        this.label = label;
        this.code = code;
    }

    /** @return 中文显示名 */
    public String label() {
        return label;
    }

    /** @return 渠道编码，用于生成流水号前缀 */
    public String code() {
        return code;
    }

    /**
     * 根据标识解析支付方式（大小写不敏感，兼容中英文别名）。
     *
     * @param raw 用户输入，例如 {@code "wallet"}、{@code "WECHAT_PAY"}、{@code "微信支付"}
     * @return 支付方式
     * @throws IllegalArgumentException 无法识别时抛出
     */
    public static PaymentMethod parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("支付方式不能为空");
        }
        String normalized = raw.trim().toUpperCase().replace("-", "_");
        for (PaymentMethod method : values()) {
            if (method.name().equals(normalized) || method.label.equals(raw.trim())
                    || method.code.equalsIgnoreCase(raw.trim())) {
                return method;
            }
        }
        throw new IllegalArgumentException("不支持的支付方式：" + raw + "（可选：WALLET/WECHAT_PAY/ALIPAY/BANK_CARD）");
    }
}
