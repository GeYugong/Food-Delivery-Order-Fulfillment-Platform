package com.example.delivery.infrastructure.payment;

import com.example.delivery.domain.payment.Payment;
import com.example.delivery.domain.payment.PaymentMethod;
import com.example.delivery.domain.support.PaymentGateway;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * 银行卡支付适配器（类适配器风格的说明载体）。
 *
 * <p><b>设计模式：适配器模式（Adapter）</b></p>
 *
 * <p><b>本渠道的特点：</b>银行网关用<b>数字错误码</b>表示成败（0 为成功），
 * 与前面的布尔/字符串语义都不同。适配器需要维护一张「错误码 → 中文原因」的映射表，
 * 这体现了适配器的另一项职责：<b>把异构系统的错误语义翻译成平台统一的业务失败原因</b>，
 * 否则上层拿到的就只是一个莫名其妙的数字。</p>
 *
 * <p><b>对象适配器 vs 类适配器：</b>Java 单继承，类适配器（同时继承被适配者并实现目标接口）
 * 会占用唯一的继承位；因此工程实践中优先用<b>对象适配器</b>（组合，如本类）。
 * 这里保留类适配器的对比说明，便于课程报告讨论两种写法的取舍。</p>
 *
 * @author 课程大作业
 */
public class BankCardPaymentAdapter implements PaymentGateway {

    /** 被适配者：银行网关。 */
    private final ThirdPartyPayApis.BankGateway gateway;

    /** 错误码字典：把网关的数字码翻译成可读原因。 */
    private static final Map<Integer, String> ERROR_MESSAGES = Map.of(
            1001, "金额非法",
            1002, "卡号不存在",
            1003, "单笔限额超限",
            1004, "银行系统维护中");

    /**
     * @param gateway 银行网关
     */
    public BankCardPaymentAdapter(ThirdPartyPayApis.BankGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public String channel() {
        return PaymentMethod.BANK_CARD.name();
    }

    @Override
    public String channelLabel() {
        return PaymentMethod.BANK_CARD.label();
    }

    @Override
    public Payment pay(String orderId, BigDecimal amount, String payerRef) {
        // 银行网关同样以「分」为单位
        long cent = amount.multiply(new BigDecimal("100"))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();

        int code = gateway.debit(payerRef == null ? "ANONYMOUS" : payerRef, cent);
        String tradeNo = "BANK" + Math.abs(orderId.hashCode());

        if (code != 0) {
            String reason = ERROR_MESSAGES.getOrDefault(code, "未知错误码 " + code);
            return Payment.failure(tradeNo, orderId, amount, PaymentMethod.BANK_CARD, reason);
        }
        return Payment.success(tradeNo, orderId, amount, PaymentMethod.BANK_CARD);
    }
}
