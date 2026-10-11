package com.example.delivery.application.validator;

import com.example.delivery.application.OrderValidationContext;

import java.math.BigDecimal;

/**
 * 起送价校验：订单小计必须达到商家起送价。
 *
 * <p><b>职责链中的位置：第二环。</b>外卖平台的典型规则，
 * 与商家强相关（不同商家起送价不同），因此必须读商家的 {@code minimumOrder} 而不是全局常量。</p>
 *
 * <p>放在库存查询之前，是因为它只做一次金额比较（零成本），
 * 而库存查询可能涉及远程调用。</p>
 *
 * @author 课程大作业
 */
public class MinimumAmountValidator extends OrderValidator {

    @Override
    public String name() {
        return "起送价校验";
    }

    @Override
    protected String check(OrderValidationContext context) {
        BigDecimal subtotal = context.order().subtotal();
        BigDecimal minimum = context.merchant().minimumOrder();
        if (!context.merchant().reachesMinimum(subtotal)) {
            return String.format("未达起送价：当前 %s 元，商家[%s]起送价 %s 元，还差 %s 元",
                    subtotal.toPlainString(), context.merchant().name(),
                    minimum.toPlainString(), minimum.subtract(subtotal).toPlainString());
        }
        return String.format("通过：小计 %s 元 ≥ 起送价 %s 元",
                subtotal.toPlainString(), minimum.toPlainString());
    }
}
