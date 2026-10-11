package com.example.delivery.application;

import com.example.delivery.bootstrap.PlatformBootstrap;
import com.example.delivery.domain.order.IllegalOrderStateException;
import com.example.delivery.domain.order.Order;
import com.example.delivery.domain.order.OrderStatus;
import com.example.delivery.domain.payment.Payment;
import com.example.delivery.domain.payment.PaymentMethod;
import com.example.delivery.domain.rider.Rider;
import com.example.delivery.domain.support.DispatchStrategies;
import com.example.delivery.domain.support.OrderEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 派单、骑手调度与支付适配的业务层单元测试。
 *
 * <p><b>覆盖的设计模式集成点：</b></p>
 * <ul>
 *   <li><b>中介者模式</b>：{@link DispatchCenter} 收集候选骑手、促成订单与骑手绑定、发布派单事件；</li>
 *   <li><b>策略模式</b>：{@code nearest/leastLoaded/highestRated} 可运行期切换，结果里回显策略名；</li>
 *   <li><b>适配器模式</b>：余额/微信/支付宝/银行卡四条渠道统一成 {@code PaymentGateway}，
 *       异构的失败语义（余额不足、第三方状态码）都被翻译成统一的 {@link Payment} 记录；</li>
 *   <li><b>状态模式</b>：支付、接单、完成都经由状态对象推进，重复支付被守卫拒绝。</li>
 * </ul>
 *
 * @author 课程大作业
 */
@DisplayName("派单中介 + 骑手调度 + 支付适配")
class DispatchAndPaymentTest {

    /** 装配好的平台（每个用例一份新数据）。 */
    private PlatformBootstrap.Platform platform;

    /** 被测门面。 */
    private OrderFacade facade;

    /** 每个用例前重新装配平台，保证骑手都在初始状态（R-3001/R-3002 空闲，R-3003 派送中）。 */
    @BeforeEach
    void setUp() {
        platform = PlatformBootstrap.assemble();
        facade = platform.facade();
    }

    /**
     * 造一张待支付订单（U-1001 + M-2001，香辣鸡腿堡 × 2 = 39.60 元）。
     *
     * @return 待支付订单
     */
    private Order submittedOrder() {
        Order order = facade.create("U-1001", "M-2001");
        facade.addDish(order.orderId(), "DISH-001", 2);
        facade.submit(order.orderId(), null);
        return order;
    }

    /**
     * 造一张已支付订单（用支付宝，避免余额限制干扰派单用例）。
     *
     * @return 已支付订单
     */
    private Order paidOrder() {
        Order order = submittedOrder();
        facade.pay(order.orderId(), PaymentMethod.ALIPAY);
        return order;
    }

    /**
     * 读取骑手最新状态。
     *
     * @param riderId 骑手编号
     * @return 骑手
     */
    private Rider rider(String riderId) {
        return platform.actorStore().riders().findById(riderId).orElseThrow();
    }

    // ==================================================================
    //                          用例分组
    // ==================================================================

    @Nested
    @DisplayName("派单（中介者模式）")
    class DispatchCases {

        @Test
        @DisplayName("未支付订单派单失败，且订单状态不变")
        void shouldFailDispatchForUnpaidOrder() {
            Order order = submittedOrder();
            assertEquals(OrderStatus.PENDING_PAYMENT, order.status(), "前置条件：订单应为待支付");

            DispatchOutcome outcome = facade.dispatch(order.orderId());

            assertAll(
                    () -> assertTrue(outcome.failed(), "未支付订单派单应失败"),
                    () -> assertTrue(outcome.success() == false, "失败结果的 success 应为 false"),
                    () -> assertEquals(OrderStatus.PENDING_PAYMENT, order.status(), "失败后订单状态不变"),
                    () -> assertTrue(order.rider().isEmpty(), "失败后订单不应绑定骑手"),
                    () -> assertEquals(order.orderId(), outcome.orderId(), "结果应回带订单号"),
                    () -> assertTrue(outcome.message().contains("只有[已支付]的订单可以派单"),
                            "失败原因应说明前置状态条件"),
                    () -> assertFalse(outcome.trace().isEmpty(), "即使失败也应留下派单轨迹"));
        }

        @Test
        @DisplayName("已支付订单派单成功：状态变为已接单、绑定骑手、骑手负载变为 1")
        void shouldDispatchPaidOrder() {
            Order order = paidOrder();

            DispatchOutcome outcome = facade.dispatch(order.orderId());

            assertAll(
                    () -> assertTrue(outcome.success(), "已支付订单应派单成功：" + outcome.message()),
                    () -> assertEquals(OrderStatus.ASSIGNED, order.status(), "派单成功后应为已接单"),
                    () -> assertTrue(order.rider().isPresent(), "订单应绑定接单骑手"),
                    () -> assertEquals(outcome.riderId(), order.rider().orElseThrow().riderId(),
                            "订单绑定的骑手应与派单结果一致"),
                    () -> assertEquals(1, rider(outcome.riderId()).currentLoad(), "骑手在手单量应变为 1"),
                    () -> assertEquals(Rider.RiderStatus.DELIVERING, rider(outcome.riderId()).status(),
                            "骑手状态应变为派送中"),
                    () -> assertEquals(DispatchStrategies.nearest().name(), outcome.strategy(),
                            "默认首选策略应为「最近优先」"),
                    () -> assertEquals(1, outcome.attempts(), "首选策略即命中，尝试次数应为 1"),
                    () -> assertNotNull(outcome.riderName(), "结果应带出骑手姓名"));
        }

        @Test
        @DisplayName("把全部骑手置为 RESTING 后派单失败，并发布 OrderDispatchFailed 事件")
        void shouldFailWhenNoRiderAvailable() {
            // 中介者只认识「可接单」的骑手：全部休息后候选集合为空
            platform.actorStore().riders().findAll().forEach(existing ->
                    platform.actorStore().riders().save(new Rider(existing.riderId(), existing.name(),
                            existing.phone(), Rider.RiderStatus.RESTING, existing.currentLoad(),
                            existing.maxLoad(), existing.currentDistanceKm(), existing.rating())));
            Order order = paidOrder();

            DispatchOutcome outcome = facade.dispatch(order.orderId());

            boolean publishedFailure = platform.publisher().publishedEvents().stream()
                    .anyMatch(event -> OrderEvent.ORDER_DISPATCH_FAILED.equals(event.type())
                            && order.orderId().equals(event.orderId()));

            assertAll(
                    () -> assertTrue(outcome.failed(), "无骑手可派时应派单失败"),
                    () -> assertTrue(outcome.message().contains("没有可接单的骑手"),
                            "失败原因应说明没有可接单骑手：" + outcome.message()),
                    () -> assertEquals(OrderStatus.PAID, order.status(), "派单失败后订单应保持已支付"),
                    () -> assertTrue(order.rider().isEmpty(), "派单失败后不应绑定骑手"),
                    () -> assertTrue(publishedFailure, "应发布 OrderDispatchFailed 事件"),
                    () -> assertTrue(platform.merchantListener().messages().stream()
                                    .anyMatch(message -> message.contains("暂无骑手")),
                            "商家端应收到「暂无骑手」提醒"),
                    () -> assertTrue(platform.actorStore().riders().findAll().stream()
                                    .noneMatch(Rider::canAccept),
                            "所有骑手都应处于不可接单状态"));
        }

        @Test
        @DisplayName("切换调度策略后派单成功，结果里的策略名反映所选策略")
        void shouldDispatchWithSwitchedStrategy() {
            Order order = paidOrder();

            facade.useDispatchStrategy(DispatchStrategies.leastLoaded());
            DispatchOutcome outcome = facade.dispatch(order.orderId());

            assertAll(
                    () -> assertEquals("最少负载优先",
                            facade.dispatchCenter().preferredStrategy().name(),
                            "首选策略应已切换"),
                    () -> assertTrue(outcome.success(), "切换策略后仍应派单成功：" + outcome.message()),
                    () -> assertEquals(DispatchStrategies.leastLoaded().name(), outcome.strategy(),
                            "派单结果应回显所选策略名"),
                    () -> assertEquals(OrderStatus.ASSIGNED, order.status(), "派单成功后应为已接单"),
                    () -> assertEquals(1, outcome.attempts(), "首选策略即命中，尝试次数应为 1"));
        }

        @Test
        @DisplayName("送达后骑手负载回落到 0 且状态回到 IDLE")
        void shouldReleaseRiderAfterComplete() {
            Order order = paidOrder();
            DispatchOutcome outcome = facade.dispatch(order.orderId());
            String riderId = outcome.riderId();
            assertEquals(1, rider(riderId).currentLoad(), "接单后负载应为 1");

            facade.startDelivery(order.orderId());
            facade.complete(order.orderId());

            assertAll(
                    () -> assertEquals(OrderStatus.COMPLETED, order.status(), "送达后订单应已完成"),
                    () -> assertEquals(0, rider(riderId).currentLoad(), "送达后骑手负载应回落到 0"),
                    () -> assertEquals(Rider.RiderStatus.IDLE, rider(riderId).status(),
                            "负载归零后骑手状态应回到空闲"),
                    () -> assertTrue(platform.riderListener().pushes().stream()
                                    .anyMatch(push -> push.contains("已送达")),
                            "骑手端应收到送达推送"));
        }

        @Test
        @DisplayName("无骑手可用时也可以人工指派（assignRider 走的同一条绑定链路）")
        void shouldAssignRiderManually() {
            Order order = paidOrder();

            DispatchOutcome outcome = facade.assignRider(order.orderId(), "R-3001");

            assertAll(
                    () -> assertTrue(outcome.success(), "人工指派应成功：" + outcome.message()),
                    () -> assertEquals("人工指派", outcome.strategy(), "人工指派的策略名应为「人工指派」"),
                    () -> assertEquals(OrderStatus.ASSIGNED, order.status(), "指派后应为已接单"),
                    () -> assertEquals(1, rider("R-3001").currentLoad(), "被指派骑手负载应为 1"));
        }
    }

    @Nested
    @DisplayName("支付（适配器模式）")
    class PaymentCases {

        @Test
        @DisplayName("支付成功：状态为已支付、流水号非空、支付流水可查")
        void shouldPaySuccessfully() {
            Order order = submittedOrder();

            facade.pay(order.orderId(), PaymentMethod.ALIPAY);

            List<Payment> payments = facade.paymentsOf(order.orderId());

            assertAll(
                    () -> assertEquals(OrderStatus.PAID, order.status(), "支付成功后应为已支付"),
                    () -> assertTrue(order.paidAt().isPresent(), "应记录支付时间"),
                    () -> assertEquals(PaymentMethod.ALIPAY.name(), order.paymentMethod().orElseThrow(),
                            "订单应记录支付方式"),
                    () -> assertTrue(order.paymentTradeNo().isPresent(), "应记录第三方支付流水号"),
                    () -> assertFalse(order.paymentTradeNo().orElseThrow().isBlank(), "流水号不应为空白"),
                    () -> assertFalse(payments.isEmpty(), "支付流水应可查到"),
                    () -> assertTrue(payments.get(0).succeeded(), "流水应为成功状态"),
                    () -> assertEquals(PaymentMethod.ALIPAY, payments.get(0).method(), "流水渠道应为支付宝"),
                    () -> assertFalse(facade.allPayments().isEmpty(), "全部支付流水应包含本笔"));
        }

        @Test
        @DisplayName("四个支付渠道都已注册（适配器注册表）")
        void shouldHaveAllChannelsRegistered() {
            List<String> channels = facade.gateways().registeredChannels();

            assertAll(
                    () -> assertTrue(channels.contains(PaymentMethod.WALLET.label()), "应注册余额支付"),
                    () -> assertTrue(channels.contains(PaymentMethod.WECHAT_PAY.label()), "应注册微信支付"),
                    () -> assertTrue(channels.contains(PaymentMethod.ALIPAY.label()), "应注册支付宝"),
                    () -> assertTrue(channels.contains(PaymentMethod.BANK_CARD.label()), "应注册银行卡"));
        }

        @Test
        @DisplayName("余额不足：余额支付失败且订单仍为待支付，换支付宝后支付成功")
        void shouldFailWalletPaymentWhenBalanceInsufficient() {
            // U-1002 余额仅 50.00 元，而 56.00 元小计 + 配送费必然超过余额
            Order order = facade.create("U-1002", "M-2002");
            facade.addDish(order.orderId(), "DISH-101", 2);
            facade.submit(order.orderId(), null);
            assertTrue(order.totalAmount().compareTo(new BigDecimal("50.00")) > 0,
                    "前置条件：实付应超过 U-1002 的余额");

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> facade.pay(order.orderId(), PaymentMethod.WALLET), "余额不足应支付失败");

            assertAll(
                    () -> assertTrue(ex.getMessage().contains("余额不足"),
                            "失败原因应说明余额不足：" + ex.getMessage()),
                    () -> assertEquals(OrderStatus.PENDING_PAYMENT, order.status(),
                            "支付失败后订单应保持待支付，允许换渠道重试"),
                    () -> assertTrue(order.paymentTradeNo().isEmpty(), "支付失败不应写入流水号"),
                    () -> assertEquals(new BigDecimal("50.00"),
                            platform.actorStore().users().findById("U-1002").orElseThrow().balance(),
                            "支付失败不应扣减余额"),
                    // 实测行为：失败流水同样入账（便于对账与排查重复支付），
                    // 并且门面会发出一条 OrderPaid 事件（payload 明确写着「支付失败」）。
                    () -> assertFalse(facade.paymentsOf(order.orderId()).isEmpty(),
                            "失败流水也应记录，便于对账"));

            facade.pay(order.orderId(), PaymentMethod.ALIPAY);

            assertAll(
                    () -> assertEquals(OrderStatus.PAID, order.status(), "换支付宝后应支付成功"),
                    () -> assertEquals(PaymentMethod.ALIPAY.name(), order.paymentMethod().orElseThrow(),
                            "订单支付方式应更新为支付宝"));
        }

        @Test
        @DisplayName("重复支付被拒（只有待支付订单可以支付）")
        void shouldRejectDuplicatePayment() {
            Order order = paidOrder();
            assertEquals(OrderStatus.PAID, order.status(), "前置条件：订单已支付");

            IllegalOrderStateException ex = assertThrows(IllegalOrderStateException.class,
                    () -> facade.pay(order.orderId(), PaymentMethod.ALIPAY), "已支付订单不应允许再次支付");

            assertAll(
                    () -> assertEquals(OrderStatus.PAID, ex.current(), "异常应带出当前状态"),
                    () -> assertTrue(ex.getMessage().contains("只有[待支付]订单可以支付"),
                            "异常消息应说明状态守卫规则"),
                    () -> assertEquals(1, facade.paymentsOf(order.orderId()).size(),
                            "重复支付被拒后不应新增流水"),
                    () -> assertEquals(OrderStatus.PAID, order.status(), "订单状态不应改变"));
        }

        @Test
        @DisplayName("未支付订单不能派单，支付成功后才进入派单前置条件")
        void shouldOnlyDispatchAfterPayment() {
            Order order = submittedOrder();

            assertTrue(facade.dispatch(order.orderId()).failed(), "待支付订单派单应失败");

            facade.pay(order.orderId(), PaymentMethod.WECHAT_PAY);
            DispatchOutcome outcome = facade.dispatch(order.orderId());

            assertAll(
                    () -> assertTrue(outcome.success(), "支付成功后应能派单：" + outcome.message()),
                    () -> assertEquals(OrderStatus.ASSIGNED, order.status(), "派单成功后应为已接单"),
                    () -> assertTrue(platform.riderListener().pushes().stream()
                                    .anyMatch(push -> push.contains("您有新订单")),
                            "骑手端应收到新订单推送"));
        }
    }
}
