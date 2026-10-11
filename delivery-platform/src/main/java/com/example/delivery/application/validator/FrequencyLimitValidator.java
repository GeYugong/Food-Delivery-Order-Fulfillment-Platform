package com.example.delivery.application.validator;

import com.example.delivery.application.OrderValidationContext;

/**
 * 下单频控校验：同一用户短时间内下单次数过多时拦截。
 *
 * <p><b>职责链中的位置：第五环。</b>用于防刷单、防误操作、防脚本攻击。
 * 真实系统会把计数放进 Redis 并做滑动窗口，这里由上下文携带计数
 * （{@link OrderValidationContext#recentOrderCount()}），
 * 使校验器本身保持无状态、可重复测试。</p>
 *
 * @author 课程大作业
 */
public class FrequencyLimitValidator extends OrderValidator {

    /** 一分钟内允许的最大下单次数。 */
    public static final int LIMIT_PER_MINUTE = 3;

    @Override
    public String name() {
        return "下单频控校验";
    }

    @Override
    protected String check(OrderValidationContext context) {
        if (context.recentOrderCount() >= LIMIT_PER_MINUTE) {
            return String.format("下单过于频繁：最近 1 分钟已下 %d 单（上限 %d 单）",
                    context.recentOrderCount(), LIMIT_PER_MINUTE);
        }
        return String.format("通过：最近 1 分钟下单 %d 单（上限 %d 单）",
                context.recentOrderCount(), LIMIT_PER_MINUTE);
    }
}
