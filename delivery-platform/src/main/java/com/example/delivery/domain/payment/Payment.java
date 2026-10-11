package com.example.delivery.domain.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * 支付记录（值对象）。
 *
 * <p><b>设计模式关联：适配器模式（Adapter）的产物对象</b></p>
 * <p><b>为什么需要它：</b>平台要对接多种支付渠道（余额、微信、支付宝、银行卡），
 * 每个渠道的接口签名、返回报文格式都不一样。适配器模式把它们统一成
 * {@code PaymentGateway#pay(order, amount)} 这一个方法，返回本对象作为「统一结果」。
 * 于是订单服务只依赖本对象与网关接口，完全不感知渠道差异。</p>
 *
 * <p>本对象同时是「对账」的最小单元：金额、渠道、流水号、时间四要素齐全。</p>
 *
 * @param tradeNo       支付流水号（第三方返回；余额支付由平台生成）
 * @param orderId       订单号
 * @param amount        支付金额
 * @param method        支付方式
 * @param status        支付状态
 * @param paidAt        支付完成时刻
 * @param failureReason 失败原因（成功时为 null）
 * @author 课程大作业
 */
public record Payment(
        String tradeNo,
        String orderId,
        BigDecimal amount,
        PaymentMethod method,
        PaymentStatus status,
        Instant paidAt,
        String failureReason) {

    /** 紧凑构造器：金额统一 2 位小数，缺省时间取当前时刻。 */
    public Payment {
        if (tradeNo == null || tradeNo.isBlank()) {
            throw new IllegalArgumentException("支付流水号不能为空");
        }
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("订单号不能为空");
        }
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("支付金额不能为空且不能为负数");
        }
        amount = amount.setScale(2, RoundingMode.HALF_UP);
        method = method == null ? PaymentMethod.WALLET : method;
        status = status == null ? PaymentStatus.PENDING : status;
        paidAt = paidAt == null ? Instant.now() : paidAt;
    }

    /**
     * 构造一笔成功的支付记录。
     *
     * @param tradeNo 流水号
     * @param orderId 订单号
     * @param amount  金额
     * @param method  支付方式
     * @return 成功记录
     */
    public static Payment success(String tradeNo, String orderId, BigDecimal amount, PaymentMethod method) {
        return new Payment(tradeNo, orderId, amount, method, PaymentStatus.SUCCESS, Instant.now(), null);
    }

    /**
     * 构造一笔失败的支付记录。
     *
     * @param tradeNo 流水号
     * @param orderId 订单号
     * @param amount  金额
     * @param method  支付方式
     * @param reason  失败原因
     * @return 失败记录
     */
    public static Payment failure(String tradeNo, String orderId, BigDecimal amount,
                                  PaymentMethod method, String reason) {
        return new Payment(tradeNo, orderId, amount, method, PaymentStatus.FAILED, Instant.now(), reason);
    }

    /**
     * 是否支付成功。
     *
     * @return 成功返回 {@code true}
     */
    public boolean succeeded() {
        return status == PaymentStatus.SUCCESS;
    }

    /** @return 人类可读描述 */
    public String describe() {
        return String.format("支付[%s] 订单=%s 金额=%s 元 渠道=%s 状态=%s%s",
                tradeNo, orderId, amount.toPlainString(), method.label(), status.label(),
                failureReason == null ? "" : " 原因=" + failureReason);
    }
}
