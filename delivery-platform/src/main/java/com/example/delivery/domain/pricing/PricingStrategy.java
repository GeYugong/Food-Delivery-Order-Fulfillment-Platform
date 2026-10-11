package com.example.delivery.domain.pricing;

import com.example.delivery.domain.order.Order;

import java.math.BigDecimal;

/**
 * 配送费计价策略（策略模式 Strategy 的「抽象策略」角色）。
 *
 * <p><b>设计模式：策略模式（Strategy）</b></p>
 *
 * <p><b>为什么用：</b>配送费规则会随运营不断变化——基础阶梯价、会员免配送费、
 * 雨天溢价、高峰加价、满额减免。如果写成 {@code if (isMember) ... else if (isRainy) ...}，
 * 每加一条规则都要改动这个类并重新测试全部分支。</p>
 *
 * <p>抽成策略后：</p>
 * <ol>
 *   <li>每条规则独立成一个类，互不影响，可单独单元测试；</li>
 *   <li>运行期可切换（例如检测到雨天就 {@code setStrategy(rainy)}）；</li>
 *   <li>新增规则不需要修改任何已有代码（开闭原则）。</li>
 * </ol>
 *
 * @author 课程大作业
 */
@FunctionalInterface
public interface PricingStrategy {

    /**
     * 计算配送费。
     *
     * @param order 订单（提供小计、用户会员等级、商家基础配送费等上下文）
     * @return 配送费，保留 2 位小数且不小于 0
     */
    BigDecimal calculateDeliveryFee(Order order);

    /**
     * 策略名称，用于日志、报表与控制台展示。
     *
     * @return 例如 {@code "基础阶梯计价"}
     */
    default String name() {
        return getClass().getSimpleName();
    }

    /**
     * 一句话说明本策略的规则，便于控制台打印「为什么是这个价格」。
     *
     * @return 规则说明
     */
    default String rule() {
        return name();
    }
}
