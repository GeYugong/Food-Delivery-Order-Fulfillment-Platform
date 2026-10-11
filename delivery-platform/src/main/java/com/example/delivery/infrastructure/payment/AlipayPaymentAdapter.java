package com.example.delivery.infrastructure.payment;

import com.example.delivery.domain.payment.Payment;
import com.example.delivery.domain.payment.PaymentMethod;
import com.example.delivery.domain.support.PaymentGateway;

import java.math.BigDecimal;

/**
 * 支付宝适配器。
 *
 * <p><b>设计模式：适配器模式（Adapter）</b></p>
 *
 * <p><b>与微信适配器的差异对比（这正是适配器模式要吸收的「变化点」）：</b></p>
 * <ul>
 *   <li>支付宝入参金额单位是「元」，因此<b>不需要</b>单位换算——说明适配是「按渠道各写各的」，
 *       而不是一套固定模板；</li>
 *   <li>返回的是状态字符串 {@code "TRADE_SUCCESS"}，需要映射为布尔；</li>
 *   <li>「商品标题（subject）」这一参数平台侧没有，由适配器自行补默认值，
 *       这体现适配器还能「填补双方的能力差」。</li>
 * </ul>
 *
 * @author 课程大作业
 */
public class AlipayPaymentAdapter implements PaymentGateway {

    /** 被适配者：支付宝客户端。 */
    private final ThirdPartyPayApis.AliPayClient client;

    /**
     * @param client 支付宝客户端
     */
    public AlipayPaymentAdapter(ThirdPartyPayApis.AliPayClient client) {
        this.client = client;
    }

    @Override
    public String channel() {
        return PaymentMethod.ALIPAY.name();
    }

    @Override
    public String channelLabel() {
        return PaymentMethod.ALIPAY.label();
    }

    @Override
    public Payment pay(String orderId, BigDecimal amount, String payerRef) {
        // 支付宝按「元」计价，直接用 double；标题由适配器补默认值
        String subject = "外卖订单-" + orderId;
        String status = client.trade(subject, orderId, amount.doubleValue());

        String tradeNo = "ALI" + Math.abs(orderId.hashCode());
        if (!"TRADE_SUCCESS".equals(status)) {
            return Payment.failure(tradeNo, orderId, amount, PaymentMethod.ALIPAY,
                    "支付宝返回状态=" + status);
        }
        return Payment.success(tradeNo, orderId, amount, PaymentMethod.ALIPAY);
    }
}
