package com.example.delivery.domain.pricing;

import com.example.delivery.domain.order.Order;
import com.example.delivery.domain.user.User;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 计价的上下文环境（策略模式 Strategy 的「上下文 Context」角色）。
 *
 * <p><b>设计模式：策略模式（Strategy）</b></p>
 *
 * <p><b>为什么要有上下文：</b>调用方（订单外观）不应该知道「现在该用哪条规则」。
 * 由上下文持有当前策略，并把「按订单算配送费」这一请求委托给策略对象；
 * 运行期只需 {@link #useStrategy(PricingStrategy)} 就能整体切换算法。</p>
 *
 * <p>本类同时承担「策略自动选择」的职责：根据订单上下文（是否高峰、是否雨天、是否会员）
 * 挑选合适的策略。这属于「简单工厂 + 策略」的组合用法，
 * 好处是业务代码不必写 {@code if-else} 去挑策略。</p>
 *
 * @author 课程大作业
 */
public class PricingContext {

    /** 基础阶梯计价策略（兜底策略，永远可用）。 */
    private final PricingStrategy baseStrategy = new BasePricingStrategy();

    /** 会员免配送费策略。 */
    private final PricingStrategy memberFreeStrategy = new MemberFreeDeliveryStrategy();

    /** 雨天溢价策略。 */
    private final PricingStrategy rainyStrategy = new RainyDayPricingStrategy();

    /** 高峰时段加价策略。 */
    private final PricingStrategy peakHourStrategy = new PeakHourPricingStrategy();

    /** 当前生效的策略，默认使用基础策略。 */
    private PricingStrategy current = baseStrategy;

    /**
     * 是否由调用方显式指定了策略。
     *
     * <p><b>为什么需要这个标记（策略模式的关键细节）：</b>
     * 本上下文有两种设置策略的方式——{@link #useStrategy(PricingStrategy)}（人工指定，
     * 例如运营为某个商家单独配置雨天价）与自动选择（依据会员/天气/时段）。
     * 如果每次计算都无条件重跑自动选择，那么手工设置的策略会在第一次计价后就被悄悄覆盖，
     * 「运行期可切换」这一策略模式的核心能力形同虚设。
     * 因此用本标记区分：<b>显式设置优先，自动选择只在其未被显式设置时生效</b>。</p>
     */
    private boolean strategyOverridden;

    /** 当前环境是否雨天。 */
    private boolean rainy;

    /** 当前是否为用餐高峰期。 */
    private boolean peakHour;

    /** 当前每公里加价（元），由外部配置注入，便于演示不同城市费率。 */
    private BigDecimal pricePerKm = new BigDecimal("1.00");

    /** 起步价（元）。 */
    private BigDecimal startPrice = new BigDecimal("5.00");

    /**
     * 手动切换策略（策略模式最核心的能力：运行期可换算法）。
     *
     * <p>调用后策略被「锁定」，后续 {@link #deliveryFeeFor(Order)} 不会再用自动选择覆盖它；
     * 若要交还给自动选择，调用 {@link #useAutoSelection()}。</p>
     *
     * @param strategy 新策略，不可为 null
     * @return this，便于链式调用
     */
    public PricingContext useStrategy(PricingStrategy strategy) {
        if (strategy == null) {
            throw new IllegalArgumentException("计价策略不能为空");
        }
        this.current = strategy;
        this.strategyOverridden = true;
        return this;
    }

    /**
     * 交还策略选择权给「自动选择」（按会员、天气、时段挑选）。
     *
     * @return this，便于链式调用
     */
    public PricingContext useAutoSelection() {
        this.strategyOverridden = false;
        return this;
    }

    /** @return 当前策略是否由调用方显式指定 */
    public boolean isStrategyOverridden() {
        return strategyOverridden;
    }

    /**
     * 设置天气与时段，并按「运营优先级」自动挑选策略。
     *
     * <p>优先级：会员免配送费 &gt; 雨天溢价 &gt; 高峰加价 &gt; 基础计价。
     * 这里的优先级是业务决策，集中写在方法里，避免散落到各处。</p>
     *
     * @param rainy     是否雨天
     * @param peakHour  是否高峰时段
     * @return this，便于链式调用
     */
    public PricingContext withEnvironment(boolean rainy, boolean peakHour) {
        this.rainy = rainy;
        this.peakHour = peakHour;
        return this;
    }

    /**
     * 设置费率参数（起步价与每公里单价）。
     *
     * @param startPrice 起步价（元）
     * @param pricePerKm 每公里单价（元）
     * @return this
     */
    public PricingContext withRate(String startPrice, String pricePerKm) {
        this.startPrice = new BigDecimal(startPrice);
        this.pricePerKm = new BigDecimal(pricePerKm);
        return this;
    }

    /**
     * 按订单计算配送费（未显式指定策略时自动选策略 → 交给策略计算）。
     *
     * @param order 订单
     * @return 配送费
     */
    public BigDecimal deliveryFeeFor(Order order) {
        if (!strategyOverridden) {
            // 只有未被显式指定时才自动挑选，避免覆盖运营的手工配置
            this.current = selectStrategy(order);
        }
        return current.calculateDeliveryFee(order).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 自动挑选策略：会员优先，其次天气与时段。
     *
     * @param order 订单
     * @return 选中的策略
     */
    private PricingStrategy selectStrategy(Order order) {
        boolean isMember = order.customer().memberLevel() != User.MemberLevel.NORMAL;
        boolean memberReachThreshold = order.subtotal().compareTo(new BigDecimal("30.00")) >= 0;
        if (isMember && memberReachThreshold) {
            return memberFreeStrategy;
        }
        if (rainy) {
            return rainyStrategy;
        }
        if (peakHour) {
            return peakHourStrategy;
        }
        return baseStrategy;
    }

    /** @return 当前生效的策略 */
    public PricingStrategy currentStrategy() {
        return current;
    }

    /** @return 起步价（元） */
    public BigDecimal startPrice() {
        return startPrice;
    }

    /** @return 每公里单价（元） */
    public BigDecimal pricePerKm() {
        return pricePerKm;
    }

    /** @return 是否雨天 */
    public boolean rainy() {
        return rainy;
    }

    /** @return 是否高峰 */
    public boolean peakHour() {
        return peakHour;
    }

    /**
     * 基础阶梯计价：起步价 + 距离 × 每公里单价。
     *
     * <p>距离这里用「商家到用户的估算距离」，控制台演示时从订单备注外的字段取；
     * 为保持领域模型简单，本实现用订单字符数派生一个稳定的模拟距离（3~6 公里）。</p>
     */
    public class BasePricingStrategy implements PricingStrategy {

        @Override
        public String name() {
            return "基础阶梯计价";
        }

        @Override
        public String rule() {
            return String.format("起步 %s 元 + 距离 × %s 元/公里",
                    startPrice.toPlainString(), pricePerKm.toPlainString());
        }

        @Override
        public BigDecimal calculateDeliveryFee(Order order) {
            return startPrice.add(pricePerKm.multiply(BigDecimal.valueOf(estimatedDistanceKm(order))));
        }
    }

    /**
     * 雨天溢价：基础价 × 1.5（恶劣天气骑手供给紧张，用价格调节）。
     */
    public class RainyDayPricingStrategy implements PricingStrategy {

        /** 雨天倍率。 */
        private final BigDecimal multiplier = new BigDecimal("1.5");

        @Override
        public String name() {
            return "雨天溢价计价";
        }

        @Override
        public String rule() {
            return "基础价 × 1.5（恶劣天气补贴骑手）";
        }

        @Override
        public BigDecimal calculateDeliveryFee(Order order) {
            return baseStrategy.calculateDeliveryFee(order).multiply(multiplier);
        }
    }

    /**
     * 高峰时段加价：基础价 + 2 元。
     */
    public class PeakHourPricingStrategy implements PricingStrategy {

        /** 高峰加价金额。 */
        private final BigDecimal extra = new BigDecimal("2.00");

        @Override
        public String name() {
            return "高峰时段加价";
        }

        @Override
        public String rule() {
            return "基础价 + 2.00 元（11:30-13:00 / 17:30-19:00）";
        }

        @Override
        public BigDecimal calculateDeliveryFee(Order order) {
            return baseStrategy.calculateDeliveryFee(order).add(extra);
        }
    }

    /**
     * 会员免配送费：会员且小计满 30 元时配送费为 0。
     */
    public class MemberFreeDeliveryStrategy implements PricingStrategy {

        @Override
        public String name() {
            return "会员免配送费";
        }

        @Override
        public String rule() {
            return "会员且菜品小计满 30.00 元，配送费全免";
        }

        @Override
        public BigDecimal calculateDeliveryFee(Order order) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
    }

    /**
     * 估算配送距离（公里）。
     *
     * <p>为让控制台演示可复现，这里用订单号与地址长度派生一个稳定值，
     * 真实系统应调用地图服务（属于基础设施层，不影响策略本身）。</p>
     *
     * @param order 订单
     * @return 距离，3.0 ~ 6.0 公里
     */
    public static double estimatedDistanceKm(Order order) {
        int seed = Math.abs(order.orderId().hashCode() % 31);
        return 3.0 + seed / 10.0;
    }
}
