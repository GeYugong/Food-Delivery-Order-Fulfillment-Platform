package com.example.delivery.domain.pricing;

import com.example.delivery.domain.merchant.Dish;
import com.example.delivery.domain.merchant.Merchant;
import com.example.delivery.domain.order.Order;
import com.example.delivery.domain.order.OrderItem;
import com.example.delivery.domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 配送费计价策略的单元测试（策略模式 Strategy）。
 *
 * <p><b>为什么策略一定要有独立单元测试：</b>策略模式的卖点就是「每条规则独立成类、可单独测试」。
 * 如果把这些断言塞进业务集成测试里，就只能通过「下一单」来间接验证价格，
 * 既慢又难以覆盖雨天、高峰、会员三者的组合。</p>
 *
 * <p><b>本测试的构造方式：</b>直接用 {@link PricingContext} + 手工构造的订单对象
 * （不经门面、不碰数据库），因此是纯粹的算法测试。</p>
 *
 * @author 课程大作业
 */
@DisplayName("配送费计价策略（策略模式）")
class PricingContextTest {

    /** 非会员用户。 */
    private User normalUser;

    /** 黄金会员用户。 */
    private User goldUser;

    /** 商家。 */
    private Merchant merchant;

    /** 计价上下文。 */
    private PricingContext pricing;

    /** 每个用例前准备数据。 */
    @BeforeEach
    void setUp() {
        normalUser = new User("U-N", "普通用户", "13800000000", "杭州市西湖区文一西路 1 号",
                new BigDecimal("100.00"), User.MemberLevel.NORMAL);
        goldUser = new User("U-G", "黄金用户", "13800000001", "杭州市西湖区文一西路 1 号",
                new BigDecimal("100.00"), User.MemberLevel.GOLD);
        merchant = new Merchant("M-T", "测试商家", "杭州市西湖区文三路 1 号",
                List.of(Dish.available("D-T", "测试菜", "30.00", 100, "主食")),
                new BigDecimal("10.00"), new BigDecimal("5.00"));
        pricing = new PricingContext().withRate("5.00", "1.00");
    }

    /**
     * 构造一张指定用户与菜品数量的订单。
     *
     * @param user     下单用户
     * @param quantity 菜品数量
     * @return 订单（草稿态，未提交）
     */
    private Order orderOf(User user, int quantity) {
        Order order = new Order.OrderBuilder()
                .customer(user)
                .merchant(merchant)
                .createdAt(Instant.now())
                .build();
        order.addItem(new OrderItem("D-T", "测试菜", new BigDecimal("30.00"), quantity));
        return order;
    }

    @Test
    @DisplayName("非会员走基础阶梯计价：起步价 5.00 + 距离 × 1.00")
    void shouldUseBaseStrategyForNormalUser() {
        Order order = orderOf(normalUser, 1); // 小计 30.00

        BigDecimal fee = pricing.deliveryFeeFor(order);

        double distance = PricingContext.estimatedDistanceKm(order);
        BigDecimal expected = new BigDecimal("5.00")
                .add(new BigDecimal("1.00").multiply(BigDecimal.valueOf(distance)))
                .setScale(2, java.math.RoundingMode.HALF_UP);
        assertEquals(0, fee.compareTo(expected), "基础阶梯价应为 起步价 + 距离 × 单价");
        assertEquals("基础阶梯计价", pricing.currentStrategy().name(), "非会员应命中基础策略");
    }

    @Test
    @DisplayName("会员且小计满 30 元走「会员免配送费」策略")
    void shouldUseMemberFreeStrategyForGoldMember() {
        Order order = orderOf(goldUser, 1); // 小计 30.00

        BigDecimal fee = pricing.deliveryFeeFor(order);

        assertEquals(0, fee.compareTo(BigDecimal.ZERO.setScale(2)), "会员满 30 元免配送费");
        assertEquals("会员免配送费", pricing.currentStrategy().name(), "应命中会员免运费策略");
    }

    @Test
    @DisplayName("会员但小计不足 30 元不享受免运费，回退到基础策略")
    void shouldFallbackWhenMemberBelowThreshold() {
        Order order = orderOf(goldUser, 1);
        // 通过移除明细把订单变成「小计 0 元」，制造会员但未达门槛的场景
        order.removeItem("D-T");

        BigDecimal fee = pricing.deliveryFeeFor(order);

        assertNotEquals("会员免配送费", pricing.currentStrategy().name(),
                "小计不足 30 元时不应命中会员免运费");
        assertTrue(fee.compareTo(BigDecimal.ZERO) > 0, "此时仍应收取配送费：" + fee);
    }

    @Test
    @DisplayName("雨天环境自动切换到雨天溢价策略（基础价 × 1.5）")
    void shouldSwitchToRainyStrategy() {
        Order order = orderOf(normalUser, 1);
        BigDecimal baseFee = new PricingContext().withRate("5.00", "1.00").deliveryFeeFor(order);

        pricing.withEnvironment(true, false);
        BigDecimal rainyFee = pricing.deliveryFeeFor(order);

        assertEquals("雨天溢价计价", pricing.currentStrategy().name(), "应命中雨天策略");
        assertEquals(0, rainyFee.compareTo(baseFee.multiply(new BigDecimal("1.5"))
                .setScale(2, java.math.RoundingMode.HALF_UP)), "雨天配送费应为基础价 × 1.5");
    }

    @Test
    @DisplayName("高峰环境自动切换到高峰加价策略（基础价 + 2.00）")
    void shouldSwitchToPeakHourStrategy() {
        Order order = orderOf(normalUser, 1);
        BigDecimal baseFee = new PricingContext().withRate("5.00", "1.00").deliveryFeeFor(order);

        pricing.withEnvironment(false, true);
        BigDecimal peakFee = pricing.deliveryFeeFor(order);

        assertEquals("高峰时段加价", pricing.currentStrategy().name(), "应命中高峰策略");
        assertEquals(0, peakFee.compareTo(baseFee.add(new BigDecimal("2.00"))
                .setScale(2, java.math.RoundingMode.HALF_UP)), "高峰配送费应为基础价 + 2.00");
    }

    @Test
    @DisplayName("会员免运费的优先级高于雨天与高峰")
    void memberFreeShouldWinOverRainyAndPeak() {
        Order order = orderOf(goldUser, 1);
        pricing.withEnvironment(true, true);

        BigDecimal fee = pricing.deliveryFeeFor(order);

        assertEquals(0, fee.compareTo(BigDecimal.ZERO.setScale(2)),
                "会员满额时即便雨天+高峰也应免配送费");
    }

    @Test
    @DisplayName("可手动指定策略（策略模式运行期切换）")
    void shouldAllowManualStrategySwitch() {
        Order order = orderOf(normalUser, 1);

        pricing.useStrategy(pricing.new RainyDayPricingStrategy());
        BigDecimal rainy = pricing.deliveryFeeFor(order);

        assertEquals("雨天溢价计价", pricing.currentStrategy().name(), "手动切换后应为雨天策略");
        assertTrue(rainy.compareTo(BigDecimal.ZERO) > 0, "雨天配送费应为正数");
    }
}
