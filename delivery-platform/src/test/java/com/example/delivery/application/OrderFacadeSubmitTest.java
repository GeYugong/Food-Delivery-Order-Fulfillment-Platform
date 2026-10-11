package com.example.delivery.application;

import com.example.delivery.bootstrap.PlatformBootstrap;
import com.example.delivery.application.validator.AddressValidator;
import com.example.delivery.application.validator.OrderValidator;
import com.example.delivery.application.validator.ValidatorChainFactory;
import com.example.delivery.domain.merchant.Merchant;
import com.example.delivery.domain.order.Order;
import com.example.delivery.domain.order.OrderItem;
import com.example.delivery.domain.order.OrderStatus;
import com.example.delivery.domain.order.OrderValidationException;
import com.example.delivery.domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 下单与提交的业务层单元测试（{@link OrderFacade}）。
 *
 * <p><b>覆盖的设计模式集成点：</b></p>
 * <ul>
 *   <li><b>职责链</b>：空单 / 起送价 / 库存 / 地址 / 频控 / 风控 六环校验与轨迹收集；</li>
 *   <li><b>策略</b>：配送费按「基础阶梯计价」或「会员免配送费」自动选择；</li>
 *   <li><b>解释器</b>：优惠券规则文本被解析成 AST 后求值；</li>
 *   <li><b>建造者</b>：{@code OrderFacade.create} 内部用 {@code OrderBuilder} 组装订单。</li>
 * </ul>
 *
 * <p><b>测试数据说明：</b>每个用例都在 {@link BeforeEach} 里调用
 * {@link PlatformBootstrap#assemble()}，它会清空订单仓储、参与者仓储与库存，
 * 因此每个用例都拿到一份干净的演示数据（U-1001 黄金会员 / U-1002 普通会员、
 * M-2001 起送价 20 元、M-2002 起送价 30 元）。</p>
 *
 * @author 课程大作业
 */
@DisplayName("下单与提交（校验链 + 计价策略 + 解释器）")
class OrderFacadeSubmitTest {

    /** 默认完整校验链的六环名称，顺序与 {@code ValidatorChainFactory.defaultChain} 一致。 */
    private static final List<String> ALL_VALIDATOR_NAMES = List.of(
            "订单非空校验", "起送价校验", "库存校验", "收货地址校验", "下单频控校验", "风控校验");
    /** 装配好的平台（每个用例一份新数据）。 */
    private PlatformBootstrap.Platform platform;

    /** 被测门面。 */
    private OrderFacade facade;

    /** 每个用例前重新装配平台：assemble() 会清空仓储与库存，保证用例之间互不影响。 */
    @BeforeEach
    void setUp() {
        platform = PlatformBootstrap.assemble();
        facade = platform.facade();
    }

    // ==================================================================
    //                          用例分组
    // ==================================================================

    @Nested
    @DisplayName("草稿订单与菜品明细")
    class DraftAndItems {

        @Test
        @DisplayName("创建草稿订单 → 加菜 → 同名菜品合并为一行且小计正确")
        void shouldMergeSameDishAndComputeSubtotal() {
            Order order = facade.create("U-1001", "M-2001");

            facade.addDish(order.orderId(), "DISH-001", 1);
            facade.addDish(order.orderId(), "DISH-001", 2);
            facade.addDish(order.orderId(), "DISH-002", 1);

            assertAll(
                    () -> assertEquals(OrderStatus.DRAFT, order.status(), "新建订单应为草稿态"),
                    () -> assertTrue(order.status().canAddItems(), "草稿态应允许继续加菜"),
                    () -> assertEquals(2, order.items().size(),
                            "同名菜品（DISH-001）应合并为一行，共 2 行明细"),
                    () -> assertEquals(4, order.totalQuantity(), "总份数应为 3 + 1 = 4 份"),
                    () -> assertEquals(new BigDecimal("69.30"), order.subtotal(),
                            "小计应为 19.80 × 3 + 9.90 = 69.30 元"));
        }

        @Test
        @DisplayName("订单提交后不允许再加菜（状态守卫）")
        void shouldRejectAddDishAfterSubmit() {
            Order order = facade.create("U-1002", "M-2002");
            facade.addDish(order.orderId(), "DISH-101", 2);
            facade.submit(order.orderId(), null);

            assertThrows(com.example.delivery.domain.order.IllegalOrderStateException.class,
                    () -> facade.addDish(order.orderId(), "DISH-102", 1),
                    "已提交订单再加菜应被状态守卫拒绝");
        }
    }

    @Nested
    @DisplayName("校验链（职责链模式）")
    class ValidationChain {

        @Test
        @DisplayName("未达起送价提交被拒，异常指出被拦在「起送价校验」")
        void shouldRejectBelowMinimumAmount() {
            Order order = facade.create("U-1001", "M-2001");
            // 冰镇可乐 6.50 元 < M-2001 起送价 20.00 元
            facade.addDish(order.orderId(), "DISH-003", 1);

            OrderValidationException ex = assertThrows(OrderValidationException.class,
                    () -> facade.submit(order.orderId(), null), "未达起送价应被校验链拦下");

            assertAll(
                    () -> assertEquals("起送价校验", ex.validatorName(),
                            "异常应指明否决环节为「起送价校验」"),
                    () -> assertTrue(ex.getMessage().contains("未达起送价"), "异常消息应说明未达起送价"),
                    () -> assertEquals(OrderStatus.DRAFT, order.status(),
                            "被拦下的订单应保持草稿态（不产生半成品订单）"));
        }

        @Test
        @DisplayName("空订单提交被拒，异常指出被拦在「订单非空校验」")
        void shouldRejectEmptyOrder() {
            Order order = facade.create("U-1001", "M-2001");

            OrderValidationException ex = assertThrows(OrderValidationException.class,
                    () -> facade.submit(order.orderId(), null), "空订单应被校验链首环拦下");

            assertAll(
                    () -> assertEquals("订单非空校验", ex.validatorName(),
                            "空订单应被第一环「订单非空校验」拦下"),
                    () -> assertTrue(ex.getMessage().contains("没有任何菜品"), "异常消息应说明订单没有菜品"));
        }

        @Test
        @DisplayName("地址过短提交被拒（用短地址用户走完整门面链路）")
        void shouldRejectTooShortAddressThroughFacade() {
            // 演示数据里的用户地址都足够长，这里注册一个地址只有 1 个字的用户，
            // 让 facade.create(...) 把「短」写进订单的收货地址快照，从而真实触发第四环。
            platform.actorStore().users().save(new User("U-9001", "短地址用户", "13800000000",
                    "短", new BigDecimal("200.00"), User.MemberLevel.NORMAL));
            Order order = facade.create("U-9001", "M-2001");
            facade.addDish(order.orderId(), "DISH-001", 2);

            OrderValidationException ex = assertThrows(OrderValidationException.class,
                    () -> facade.submit(order.orderId(), null), "地址过短应被校验链拦下");

            assertAll(
                    () -> assertEquals("收货地址校验", ex.validatorName(),
                            "异常应指明否决环节为「收货地址校验」"),
                    () -> assertTrue(ex.getMessage().contains("地址过短"), "异常消息应说明地址过短"));
        }

        @Test
        @DisplayName("单环链直接测试 AddressValidator（职责链可裁剪）")
        void shouldTestAddressValidatorWithSingleRingChain() {
            User user = new User("U-9002", "短地址用户", "13800000001",
                    "短", new BigDecimal("200.00"), User.MemberLevel.NORMAL);
            Merchant merchant = facade.merchants().findById("M-2001").orElseThrow();
            Order order = new Order.OrderBuilder()
                    .customer(user)
                    .merchant(merchant)
                    .deliveryAddress("短")
                    .build();
            order.addItem(OrderItem.of("DISH-001", "香辣鸡腿堡", "19.80", 2));

            // 职责链的价值之一：可以只装配需要的那一环单独测试，不必跑完整链
            OrderValidator chain = ValidatorChainFactory.chainOf(new AddressValidator());
            OrderValidationContext context = OrderValidationContext.of(order, user, merchant);

            OrderValidationException ex = assertThrows(OrderValidationException.class,
                    () -> chain.validate(context), "单环地址链也应否决过短地址");

            assertAll(
                    () -> assertEquals("收货地址校验", ex.validatorName(), "单环链的否决者就是地址校验"),
                    () -> assertTrue(chain.collectTrace().stream()
                                    .anyMatch(line -> line.contains("收货地址校验") && line.contains("地址过短")),
                            "单环链轨迹应记录被拦原因"));
        }
    }

    @Nested
    @DisplayName("计价策略与优惠券解释器")
    class PricingAndCoupon {

        @Test
        @DisplayName("正常提交成功：状态为待支付，且实付 = 小计 − 优惠 + 配送费")
        void shouldSubmitSuccessfullyAndKeepAmountRelation() {
            Order order = facade.create("U-1002", "M-2002");
            // 鸡胸肉沙拉 28.00 × 2 = 56.00 元 ≥ M-2002 起送价 30.00 元
            facade.addDish(order.orderId(), "DISH-101", 2);

            facade.submit(order.orderId(), null);

            assertAll(
                    () -> assertEquals(OrderStatus.PENDING_PAYMENT, order.status(), "提交后应为待支付"),
                    () -> assertTrue(order.submittedAt().isPresent(), "应记录提交时间"),
                    () -> assertEquals(new BigDecimal("56.00"), order.subtotal(), "小计应为 56.00 元"),
                    () -> assertEquals(new BigDecimal("0.00"), order.discountAmount(), "未用券时优惠应为 0.00"),
                    () -> assertTrue(order.deliveryFee().signum() > 0,
                            "普通会员未满免配送费条件，配送费应大于 0"),
                    () -> assertEquals(
                            order.subtotal().subtract(order.discountAmount()).add(order.deliveryFee()),
                            order.totalAmount(),
                            "实付应等于 小计 − 优惠 + 配送费"),
                    () -> assertEquals(order, facade.getOrder(order.orderId()),
                            "提交后订单应已落库，可按订单号重新查回"));
        }

        @Test
        @DisplayName("使用优惠券 C-9001（满30减5）后优惠为 5.00 元")
        void shouldApplyCouponDiscount() {
            Order order = facade.create("U-1002", "M-2002");
            facade.addDish(order.orderId(), "DISH-101", 2);

            facade.submit(order.orderId(), "C-9001");

            assertAll(
                    () -> assertEquals(new BigDecimal("56.00"), order.subtotal(), "小计应为 56.00 元"),
                    () -> assertEquals(new BigDecimal("5.00"), order.discountAmount(),
                            "满30减5 券在 56.00 元小计下应抵扣 5.00 元"),
                    () -> assertEquals("C-9001", order.couponId().orElseThrow(), "订单应记录所用券号"),
                    () -> assertTrue(facade.lastCouponExplanation().contains("C-9001"),
                            "优惠说明应包含券号（解释器模式的可解释性）"));
        }

        @Test
        @DisplayName("不使用优惠券时优惠为 0.00 元")
        void shouldKeepZeroDiscountWithoutCoupon() {
            Order order = facade.create("U-1002", "M-2002");
            facade.addDish(order.orderId(), "DISH-101", 2);

            facade.submit(order.orderId(), null);

            assertAll(
                    () -> assertEquals(new BigDecimal("0.00"), order.discountAmount(), "未用券时优惠应为 0.00 元"),
                    () -> assertTrue(order.couponId().isEmpty(), "未用券时订单不应绑定券号"),
                    () -> assertTrue(facade.lastCouponExplanation().contains("未使用优惠券"),
                            "优惠说明应标注未使用优惠券"));
        }

        @Test
        @DisplayName("黄金会员小计满 30 元享受「会员免配送费」，配送费为 0.00 元")
        void shouldFreeDeliveryFeeForMember() {
            Order order = facade.create("U-1001", "M-2001");
            // 香辣鸡腿堡 19.80 × 2 = 39.60 元 ≥ 30.00 元，且 U-1001 是 GOLD 会员
            facade.addDish(order.orderId(), "DISH-001", 2);

            facade.submit(order.orderId(), null);

            assertAll(
                    () -> assertEquals(new BigDecimal("39.60"), order.subtotal(), "小计应为 39.60 元"),
                    () -> assertEquals(new BigDecimal("0.00"), order.deliveryFee(),
                            "会员且小计满 30 元时配送费应全免"),
                    () -> assertEquals(new BigDecimal("39.60"), order.totalAmount(),
                            "免配送费后实付应等于小计"),
                    () -> assertEquals("会员免配送费", facade.pricing().currentStrategy().name(),
                            "计价上下文应自动选中「会员免配送费」策略"));
        }

        @Test
        @DisplayName("普通会员同样条件下不享受免配送费（策略按会员等级分流）")
        void shouldNotFreeDeliveryFeeForNormalMember() {
            Order order = facade.create("U-1002", "M-2002");
            facade.addDish(order.orderId(), "DISH-101", 2);

            facade.submit(order.orderId(), null);

            assertAll(
                    () -> assertTrue(order.deliveryFee().signum() > 0, "普通会员应照常收取配送费"),
                    () -> assertEquals("基础阶梯计价", facade.pricing().currentStrategy().name(),
                            "普通会员应走基础阶梯计价策略"));
        }

        @Test
        @DisplayName("优惠券不存在时提交被拒")
        void shouldRejectUnknownCoupon() {
            Order order = facade.create("U-1002", "M-2002");
            facade.addDish(order.orderId(), "DISH-101", 2);

            assertThrows(IllegalArgumentException.class,
                    () -> facade.submit(order.orderId(), "C-NOT-EXIST"), "券号不存在应被拒绝");
        }
    }

    @Nested
    @DisplayName("校验链轨迹（被拦下时也要有轨迹）")
    class ValidationTrace {

        @Test
        @DisplayName("提交成功时轨迹应包含六环名称且全为通过")
        void shouldCollectFullTraceOnSuccess() {
            Order order = facade.create("U-1002", "M-2002");
            facade.addDish(order.orderId(), "DISH-101", 2);

            facade.submit(order.orderId(), null);
            String traceText = String.join("\n", facade.lastValidationTrace());

            assertAll(
                    () -> assertFalse(facade.lastValidationTrace().isEmpty(), "提交后轨迹不应为空"),
                    () -> assertEquals(ALL_VALIDATOR_NAMES.size(), facade.lastValidationTrace().size(),
                            "完整链应留下六条轨迹"),
                    () -> ALL_VALIDATOR_NAMES.forEach(name -> assertTrue(traceText.contains(name),
                            "轨迹应包含环节名称：" + name)));
        }

        @Test
        @DisplayName("被拦下时仍应留下「通过部分 + 否决环」的完整轨迹")
        void shouldCollectTraceEvenWhenBlocked() {
            Order order = facade.create("U-1001", "M-2001");
            facade.addDish(order.orderId(), "DISH-003", 1);

            assertThrows(OrderValidationException.class, () -> facade.submit(order.orderId(), null),
                    "未达起送价应被拦下");
            String traceText = String.join("\n", facade.lastValidationTrace());

            assertAll(
                    () -> assertFalse(facade.lastValidationTrace().isEmpty(),
                            "被拦下时轨迹也必须非空（这是刚修的行为）"),
                    () -> assertEquals(2, facade.lastValidationTrace().size(),
                            "第二环即被否决，轨迹应有 2 条（非空校验通过 + 起送价校验否决）"),
                    () -> assertTrue(traceText.contains("订单非空校验"), "轨迹应记录已经通过的环节"),
                    () -> assertTrue(traceText.contains("起送价校验"), "轨迹应记录否决的环节"));
        }

        @Test
        @DisplayName("校验失败不应扣减库存（校验阶段失败回滚代价为零）")
        void shouldNotReserveStockWhenValidationFails() {
            int before = facade.inventory().remaining("DISH-003");
            Order order = facade.create("U-1001", "M-2001");
            facade.addDish(order.orderId(), "DISH-003", 1);

            assertThrows(OrderValidationException.class, () -> facade.submit(order.orderId(), null),
                    "未达起送价应被拦下");

            assertEquals(before, facade.inventory().remaining("DISH-003"),
                    "校验失败时不应预占库存");
        }
    }
}
