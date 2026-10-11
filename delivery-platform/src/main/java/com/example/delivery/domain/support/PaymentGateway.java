package com.example.delivery.domain.support;

import com.example.delivery.domain.payment.Payment;

/**
 * 支付网关（适配器模式 Adapter 的「目标接口 Target」角色）。
 *
 * <p><b>设计模式：适配器模式（Adapter）</b></p>
 *
 * <p><b>问题：</b>真实世界里微信支付返回 XML/JSON 报文、支付宝返回状态字符串、
 * 银行网关返回数字错误码、余额支付则完全在本地完成——接口签名、参数单位（元/分）、
 * 返回语义全都不一样。业务代码若直接调用它们，会被「渠道差异」污染。</p>
 *
 * <p><b>解法：</b>定义本接口作为平台统一的支付契约（元为单位、布尔表达成功与否、
 * 统一返回 {@link Payment} 记录），每个渠道写一个适配器把异构接口翻译过来。
 * 之后订单服务只依赖本接口，新增渠道 = 新增一个适配器类 + 注册，零业务改动。</p>
 *
 * @author 课程大作业
 */
public interface PaymentGateway {

    /**
     * 渠道标识，例如 {@code "WALLET"}、{@code "WECHAT_PAY"}。
     *
     * @return 渠道标识（与 {@code PaymentMethod#name()} 对应）
     */
    String channel();

    /**
     * 渠道中文名，用于控制台与报表展示。
     *
     * @return 例如 {@code "微信支付"}
     */
    String channelLabel();

    /**
     * 执行支付。
     *
     * <p>实现不应抛出异常表示支付失败，而应返回失败的 {@link Payment}，
     * 因为「支付失败」是正常业务结果，不是程序错误（异常只用于程序性故障）。</p>
     *
     * @param orderId 订单号
     * @param amount  支付金额（元）
     * @param payerRef 付款方引用（余额支付用用户编号，第三方渠道用模拟账号）
     * @return 支付结果记录
     */
    Payment pay(String orderId, java.math.BigDecimal amount, String payerRef);

    /**
     * 是否支持该金额的支付（例如余额支付要检查余额是否充足）。
     *
     * @param amount   金额
     * @param payerRef 付款方引用
     * @return 支持返回 {@code true}
     */
    default boolean supports(java.math.BigDecimal amount, String payerRef) {
        return true;
    }
}
