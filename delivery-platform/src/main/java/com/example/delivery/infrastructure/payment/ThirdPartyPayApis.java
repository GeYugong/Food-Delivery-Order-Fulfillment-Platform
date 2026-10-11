package com.example.delivery.infrastructure.payment;

import java.math.BigDecimal;

/**
 * 「第三方渠道原始接口」的模拟类集合（适配器模式中的被适配者 Adaptee）。
 *
 * <p><b>为什么把它们放在一个文件里：</b>它们只是为了演示适配器模式而存在的
 * 「异构第三方 SDK」替身，方法签名刻意各不相同（参数单位不同、返回类型不同、
 * 成功判定方式不同），单独成文件反而割裂阅读。</p>
 *
 * <p>三个渠道的差异正是适配器模式要解决的问题：</p>
 * <table border="1">
 *   <caption>异构接口对照</caption>
 *   <tr><th>渠道</th><th>方法签名</th><th>成功判定</th><th>麻烦之处</th></tr>
 *   <tr><td>微信支付</td><td>{@code unifiedOrder(outTradeNo, totalFen)} → Map 报文</td>
 *       <td>{@code return_code=SUCCESS}</td><td>返回报文需要解析字符串</td></tr>
 *   <tr><td>支付宝</td><td>{@code trade(subject, outBizNo, yuan)} → String</td>
 *       <td>{@code "TRADE_SUCCESS"}</td><td>金额单位是元，返回状态字符串</td></tr>
 *   <tr><td>银行网关</td><td>{@code debit(accountRef, amountInCent)} → int</td>
 *       <td>{@code 0}</td><td>返回数字错误码，需要映射为布尔</td></tr>
 * </table>
 *
 * @author 课程大作业
 */
public final class ThirdPartyPayApis {

    /** 工具类不允许实例化。 */
    private ThirdPartyPayApis() {
    }

    /**
     * 模拟微信支付 SDK：入参单位是「分」，返回 Map 报文。
     */
    public static class WechatSdk {

        /**
         * 统一下单。
         *
         * @param outTradeNo 商户订单号
         * @param totalFen   金额（分）
         * @return 模拟报文，含 {@code return_code} 与 {@code transaction_id}
         */
        public java.util.Map<String, Object> unifiedOrder(String outTradeNo, int totalFen) {
            java.util.Map<String, Object> response = new java.util.LinkedHashMap<>();
            response.put("return_code", totalFen > 0 ? "SUCCESS" : "FAIL");
            response.put("return_msg", totalFen > 0 ? "OK" : "金额非法");
            response.put("transaction_id", "WX" + Math.abs(outTradeNo.hashCode()));
            response.put("total_fee", totalFen);
            System.out.printf("    [微信SDK] unifiedOrder(outTradeNo=%s, totalFen=%d) → return_code=%s%n",
                    outTradeNo, totalFen, response.get("return_code"));
            return response;
        }
    }

    /**
     * 模拟支付宝客户端：入参单位是「元」，返回状态字符串。
     */
    public static class AliPayClient {

        /**
         * 发起交易。
         *
         * @param subject  交易标题
         * @param outBizNo 商户业务号
         * @param yuan     金额（元）
         * @return 状态字符串，成功为 {@code "TRADE_SUCCESS"}
         */
        public String trade(String subject, String outBizNo, double yuan) {
            String status = yuan > 0 ? "TRADE_SUCCESS" : "TRADE_CLOSED";
            System.out.printf("    [支付宝SDK] trade(subject=%s, outBizNo=%s, yuan=%.2f) → %s%n",
                    subject, outBizNo, yuan, status);
            return status;
        }
    }

    /**
     * 模拟银行网关：入参单位是「分」，返回数字错误码。
     */
    public static class BankGateway {

        /**
         * 借记（扣款）。
         *
         * @param accountRef    账户引用
         * @param amountInCent  金额（分）
         * @return 0 表示成功，非 0 为错误码
         */
        public int debit(String accountRef, long amountInCent) {
            int code = amountInCent > 0 ? 0 : 1001;
            System.out.printf("    [银行网关] debit(accountRef=%s, amountInCent=%d) → code=%d%n",
                    accountRef, amountInCent, code);
            return code;
        }
    }
}
