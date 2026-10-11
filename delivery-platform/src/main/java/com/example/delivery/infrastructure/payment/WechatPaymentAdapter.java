package com.example.delivery.infrastructure.payment;

import com.example.delivery.domain.payment.Payment;
import com.example.delivery.domain.payment.PaymentMethod;
import com.example.delivery.domain.support.PaymentGateway;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * 微信支付适配器（对象适配器：持有被适配者实例，而不是继承它）。
 *
 * <p><b>设计模式：适配器模式（Adapter）</b></p>
 *
 * <p><b>本类要翻译的三处差异：</b></p>
 * <ol>
 *   <li><b>参数单位：</b>平台内部统一用「元」的 {@link BigDecimal}，
 *       微信 SDK 需要「分」的 {@code int}，因此做 {@code × 100} 并取整；</li>
 *   <li><b>返回结构：</b>微信返回 {@code Map} 报文，需要取出 {@code return_code} 判断成败、</li>
 *   <li><b>流水号：</b>从报文中提取 {@code transaction_id} 作为平台统一流水号。</li>
 * </ol>
 *
 * <p><b>为什么用组合（持有 SDK）而不是继承 SDK 类：</b>继承会把第三方类的所有方法
 * 都暴露给平台，造成接口污染；组合只暴露我们翻译后的能力，
 * 且 SDK 升级时改动被限制在本类内。</p>
 *
 * @author 课程大作业
 */
public class WechatPaymentAdapter implements PaymentGateway {

    /** 被适配者：第三方微信 SDK。 */
    private final ThirdPartyPayApis.WechatSdk sdk;

    /**
     * @param sdk 微信 SDK
     */
    public WechatPaymentAdapter(ThirdPartyPayApis.WechatSdk sdk) {
        this.sdk = sdk;
    }

    @Override
    public String channel() {
        return PaymentMethod.WECHAT_PAY.name();
    }

    @Override
    public String channelLabel() {
        return PaymentMethod.WECHAT_PAY.label();
    }

    @Override
    public Payment pay(String orderId, BigDecimal amount, String payerRef) {
        // 差异 1：元 → 分（先乘 100 再四舍五入，避免 19.99*100=1998.9999 的浮点误差）
        int totalFen = amount.multiply(new BigDecimal("100"))
                .setScale(0, RoundingMode.HALF_UP)
                .intValueExact();

        // 调用异构接口
        Map<String, Object> response = sdk.unifiedOrder(orderId, totalFen);

        // 差异 2：报文 → 布尔
        String returnCode = String.valueOf(response.get("return_code"));
        // 差异 3：从报文里提取统一流水号
        String tradeNo = String.valueOf(response.get("transaction_id"));
        if (!"SUCCESS".equals(returnCode)) {
            return Payment.failure(tradeNo, orderId, amount, PaymentMethod.WECHAT_PAY,
                    "微信返回 return_code=" + returnCode);
        }
        return Payment.success(tradeNo, orderId, amount, PaymentMethod.WECHAT_PAY);
    }

    @Override
    public boolean supports(BigDecimal amount, String payerRef) {
        // 微信单笔上限 50000 元（模拟限制）
        return amount != null && amount.compareTo(new BigDecimal("50000.00")) <= 0;
    }
}
