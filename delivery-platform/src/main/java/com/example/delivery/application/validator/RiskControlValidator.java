package com.example.delivery.application.validator;

import com.example.delivery.application.OrderValidationContext;

import java.math.BigDecimal;

/**
 * 风控校验：大额订单需要人工审核。
 *
 * <p><b>职责链中的位置：最后一环。</b>简化规则为「单笔实付 ≥ 500 元拦截」；
 * 真实风控会综合用户历史、设备指纹、支付渠道、收货地址等做评分。</p>
 *
 * <p>放在最后一环的原因：风控需要用到前面环节的结论（例如地址是否有效、
 * 库存是否满足），它是最「重」的一环，只有前面的廉价校验全部通过才值得执行。</p>
 *
 * @author 课程大作业
 */
public class RiskControlValidator extends OrderValidator {

    /** 需要人工审核的金额阈值。 */
    private static final BigDecimal HIGH_RISK_AMOUNT = new BigDecimal("500.00");

    @Override
    public String name() {
        return "风控校验";
    }

    @Override
    protected String check(OrderValidationContext context) {
        BigDecimal total = context.order().totalAmount();
        if (total.compareTo(HIGH_RISK_AMOUNT) >= 0) {
            return String.format("触发风控：单笔 %s 元 ≥ %s 元，需人工审核后放行",
                    total.toPlainString(), HIGH_RISK_AMOUNT.toPlainString());
        }
        return String.format("通过：单笔 %s 元未触及风控阈值 %s 元",
                total.toPlainString(), HIGH_RISK_AMOUNT.toPlainString());
    }
}
