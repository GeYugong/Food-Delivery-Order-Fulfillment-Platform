package com.example.delivery.application;

import com.example.delivery.bootstrap.PlatformBootstrap;
import com.example.delivery.domain.order.IllegalOrderStateException;
import com.example.delivery.domain.order.Order;
import com.example.delivery.domain.order.OrderState;
import com.example.delivery.domain.order.OrderStatus;
import com.example.delivery.domain.payment.PaymentMethod;
import com.example.delivery.domain.rider.Rider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 订单状态流转的单元测试（状态模式 + 备忘录模式）。
 *
 * <p><b>覆盖的设计模式集成点：</b></p>
 * <ul>
 *   <li><b>状态模式</b>：{@link OrderState} 按状态提供行为，非法操作由状态对象白名单统一拒绝，
 *       {@code allowedOperations()} 随状态变化；</li>
 *   <li><b>迁移矩阵</b>：{@link OrderStatus#canTransferTo(OrderStatus)} 是唯一的合法性判定入口，
 *       终态无出边、配送中不可取消；</li>
 *   <li><b>备忘录模式</b>：{@code facade.undo(orderId)} 回退到上一个快照（例如取餐前）；</li>
 *   <li><b>中介者 + 命令</b>：派单/取餐/送达这条链路由 {@code DispatchCenter} 与应用服务协同完成。</li>
 * </ul>
 *
 * @author 课程大作业
 */
@DisplayName("订单状态流转（状态模式 + 备忘录）")
class OrderStateTransitionTest {

    /** 装配好的平台（每个用例一份新数据）。 */
    private PlatformBootstrap.Platform platform;

    /** 被测门面。 */
    private OrderFacade facade;

    /** 每个用例前重新装配平台，保证订单、库存、骑手都是初始状态。 */
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
     * 造一张已支付订单。
     *
     * @return 已支付订单
     */
    private Order paidOrder() {
        Order order = submittedOrder();
        facade.pay(order.orderId(), PaymentMethod.ALIPAY);
        return order;
    }

    /**
     * 造一张已接单订单。
     *
     * @return 已接单订单
     */
    private Order assignedOrder() {
        Order order = paidOrder();
        facade.dispatch(order.orderId());
        return order;
    }

    // ==================================================================
    //                          用例分组
    // ==================================================================

    @Nested
    @DisplayName("正向履约链路")
    class HappyPath {

        @Test
        @DisplayName("草稿 → 待支付 → 已支付 → 已接单 → 配送中 → 已完成，每步状态都要正确")
        void shouldWalkThroughWholeLifecycle() {
            Order order = facade.create("U-1001", "M-2001");
            facade.addDish(order.orderId(), "DISH-001", 2);
            assertEquals(OrderStatus.DRAFT, order.status(), "第 0 步：新订单应为草稿");

            facade.submit(order.orderId(), null);
            assertEquals(OrderStatus.PENDING_PAYMENT, order.status(), "第 1 步：提交后应为待支付");
            assertTrue(order.submittedAt().isPresent(), "提交后应记录提交时间");

            facade.pay(order.orderId(), PaymentMethod.ALIPAY);
            assertEquals(OrderStatus.PAID, order.status(), "第 2 步：支付后应为已支付");
            assertTrue(order.paidAt().isPresent(), "支付后应记录支付时间");

            DispatchOutcome outcome = facade.dispatch(order.orderId());
            assertTrue(outcome.success(), "第 3 步：已支付订单应能派单成功");
            assertEquals(OrderStatus.ASSIGNED, order.status(), "派单成功后应为已接单");
            assertTrue(order.assignedAt().isPresent(), "接单后应记录接单时间");
            assertTrue(order.rider().isPresent(), "接单后订单应绑定骑手");

            facade.startDelivery(order.orderId());
            assertEquals(OrderStatus.DELIVERING, order.status(), "第 4 步：取餐后应为配送中");

            facade.complete(order.orderId());
            assertAll(
                    () -> assertEquals(OrderStatus.COMPLETED, order.status(), "第 5 步：送达后应为已完成"),
                    () -> assertTrue(order.completedAt().isPresent(), "完成后应记录完成时间"),
                    () -> assertTrue(order.status().isFinalState(), "已完成应为终态"));
        }

        @Test
        @DisplayName("每次流转都会留下领域事件（观察者模式的输入）")
        void shouldPublishEventOnEachTransition() {
            Order order = submittedOrder();
            facade.pay(order.orderId(), PaymentMethod.ALIPAY);
            facade.dispatch(order.orderId());
            facade.startDelivery(order.orderId());
            facade.complete(order.orderId());

            List<String> types = platform.publisher().publishedEvents().stream()
                    .filter(event -> order.orderId().equals(event.orderId()))
                    .map(event -> event.type())
                    .toList();

            assertAll(
                    () -> assertTrue(types.contains("OrderSubmitted"), "应发布提交事件"),
                    () -> assertTrue(types.contains("OrderPaid"), "应发布支付事件"),
                    () -> assertTrue(types.contains("OrderDispatched"), "应发布派单事件"),
                    () -> assertTrue(types.contains("OrderDelivering"), "应发布取餐事件"),
                    () -> assertTrue(types.contains("OrderCompleted"), "应发布送达事件"));
        }
    }

    @Nested
    @DisplayName("非法流转一律被拒")
    class IllegalTransitions {

        @Test
        @DisplayName("草稿直接 complete 被拒")
        void shouldRejectDraftToCompleted() {
            Order order = facade.create("U-1001", "M-2001");
            facade.addDish(order.orderId(), "DISH-001", 2);

            IllegalOrderStateException ex = assertThrows(IllegalOrderStateException.class,
                    () -> facade.complete(order.orderId()), "草稿不允许直接完成");

            assertAll(
                    () -> assertEquals(OrderStatus.DRAFT, ex.current(), "异常应带出当前状态"),
                    () -> assertEquals(OrderStatus.DRAFT, ex.target(),
                            "状态对象否决时 target 即为当前状态（未发生迁移）"),
                    () -> assertEquals(OrderStatus.DRAFT, order.status(), "失败后状态不应改变"),
                    () -> assertTrue(ex.getMessage().contains("不允许执行「完成」操作"),
                            "状态对象应说明是哪个操作被拒绝"),
                    () -> assertTrue(ex.getMessage().contains("加菜"),
                            "状态对象应列出本状态允许的操作（白名单式提示）"));
        }

        @Test
        @DisplayName("草稿直接 startDelivery 被拒")
        void shouldRejectDraftToDelivering() {
            Order order = facade.create("U-1001", "M-2001");
            facade.addDish(order.orderId(), "DISH-001", 2);

            assertThrows(IllegalOrderStateException.class,
                    () -> facade.startDelivery(order.orderId()), "草稿不允许直接配送");

            assertEquals(OrderStatus.DRAFT, order.status(), "失败后状态不应改变");
        }

        @Test
        @DisplayName("待支付订单直接派单：门面返回失败结果，状态对象直接调用则抛异常")
        void shouldRejectAssignRiderBeforePaid() {
            Order order = submittedOrder();
            Rider rider = platform.actorStore().riders().findById("R-3001").orElseThrow();

            // 实测行为：门面走的是「中介者前置校验」，非法状态返回 DispatchOutcome.failed()
            // 而不是抛异常（派单失败是正常业务结果，需要携带原因供重试决策）。
            DispatchOutcome outcome = facade.assignRider(order.orderId(), rider.riderId());

            assertAll(
                    () -> assertTrue(outcome.failed(), "待支付订单指派骑手应失败"),
                    () -> assertEquals(OrderStatus.PENDING_PAYMENT, order.status(), "失败后状态不应改变"),
                    () -> assertTrue(outcome.message().contains("只有[已支付]的订单可以派单"),
                            "失败说明应指出前置状态条件"),
                    // 状态对象这一层则是「白名单拒绝」：直接调用必须抛异常
                    () -> assertThrows(IllegalOrderStateException.class,
                            () -> OrderState.of(order).assignRider(order, rider),
                            "状态对象不允许待支付订单派单"));
        }

        @Test
        @DisplayName("已完成订单再 cancel / 再 complete 都被拒（终态无出边）")
        void shouldRejectAnyTransitionFromFinalState() {
            Order order = assignedOrder();
            facade.startDelivery(order.orderId());
            facade.complete(order.orderId());
            assertEquals(OrderStatus.COMPLETED, order.status(), "前置条件：订单已完成");

            assertAll(
                    () -> assertThrows(IllegalOrderStateException.class,
                            () -> facade.cancel(order.orderId(), "送达后取消"),
                            "终态不应允许取消"),
                    () -> assertThrows(IllegalOrderStateException.class,
                            () -> facade.complete(order.orderId()),
                            "终态不应允许再次完成"),
                    () -> assertThrows(IllegalOrderStateException.class,
                            () -> facade.startDelivery(order.orderId()),
                            "终态不应允许取餐配送"),
                    () -> assertEquals(OrderStatus.COMPLETED, order.status(), "失败后状态不应改变"));
        }
    }

    @Nested
    @DisplayName("状态对象行为（allowedOperations 随状态变化）")
    class StateObjectBehaviour {

        @Test
        @DisplayName("allowedOperations 随状态变化且与业务预期一致")
        void shouldExposeAllowedOperationsPerStatus() {
            Order order = facade.create("U-1001", "M-2001");

            List<String> draftOps = OrderState.of(order).allowedOperations();
            facade.addDish(order.orderId(), "DISH-001", 2);
            facade.submit(order.orderId(), null);
            List<String> pendingOps = OrderState.of(order).allowedOperations();
            facade.pay(order.orderId(), PaymentMethod.ALIPAY);
            List<String> paidOps = OrderState.of(order).allowedOperations();
            facade.dispatch(order.orderId());
            List<String> assignedOps = OrderState.of(order).allowedOperations();
            facade.startDelivery(order.orderId());
            List<String> deliveringOps = OrderState.of(order).allowedOperations();
            facade.complete(order.orderId());
            List<String> completedOps = OrderState.of(order).allowedOperations();

            assertAll(
                    () -> assertEquals(List.of("加菜", "提交订单", "取消订单"), draftOps,
                            "草稿态允许加菜/提交/取消"),
                    () -> assertEquals(List.of("支付", "取消订单"), pendingOps,
                            "待支付态只允许支付或取消"),
                    () -> assertEquals(List.of("派单", "取消订单"), paidOps,
                            "已支付态允许派单或取消"),
                    () -> assertEquals(List.of("骑手取餐", "取消订单"), assignedOps,
                            "已接单态允许取餐或取消"),
                    () -> assertEquals(List.of("送达完成"), deliveringOps,
                            "配送中只允许送达完成（不允许取消）"),
                    () -> assertTrue(completedOps.size() == 1 && completedOps.get(0).contains("终态"),
                            "终态只提供查询与统计，没有可执行操作"));
        }

        @Test
        @DisplayName("状态对象与订单状态保持一致，并且是注册表里的单例")
        void shouldResolveStateObjectFromRegistry() {
            Order order = facade.create("U-1001", "M-2001");

            assertAll(
                    () -> assertEquals(OrderStatus.DRAFT, OrderState.of(order).status(),
                            "状态对象应反映订单当前状态"),
                    () -> assertEquals("草稿", OrderState.of(order).name(), "状态对象应输出中文名"),
                    () -> assertTrue(OrderState.of(order) == OrderState.of(OrderStatus.DRAFT),
                            "状态对象来自注册表，同一状态不应重复创建"),
                    () -> assertEquals("配送中", OrderState.of(OrderStatus.DELIVERING).name(),
                            "按枚举也应能取到状态对象"));
        }
    }

    @Nested
    @DisplayName("备忘录撤销")
    class Memento {

        @Test
        @DisplayName("startDelivery 之后 undo 应回到 ASSIGNED")
        void shouldUndoAfterStartDelivery() {
            Order order = assignedOrder();
            assertEquals(OrderStatus.ASSIGNED, order.status(), "前置条件：订单应为已接单");

            facade.startDelivery(order.orderId());
            assertEquals(OrderStatus.DELIVERING, order.status(), "取餐后应为配送中");

            String message = facade.undo(order.orderId());

            assertAll(
                    () -> assertTrue(message.contains("已撤销"), "撤销应返回说明：" + message),
                    () -> assertEquals(OrderStatus.ASSIGNED, order.status(), "撤销后应回到已接单"),
                    () -> assertEquals(OrderStatus.ASSIGNED, facade.getOrder(order.orderId()).status(),
                            "撤销结果应已落库"),
                    () -> assertTrue(message.contains("骑手取餐前"),
                            "撤销说明应包含快照标签「骑手取餐前」：" + message));
        }

        @Test
        @DisplayName("complete 之后 undo 应回到 DELIVERING（完成时间一并回滚）")
        void shouldUndoAfterComplete() {
            Order order = assignedOrder();
            facade.startDelivery(order.orderId());
            facade.complete(order.orderId());
            assertEquals(OrderStatus.COMPLETED, order.status(), "前置条件：订单已完成");

            facade.undo(order.orderId());

            assertAll(
                    () -> assertEquals(OrderStatus.DELIVERING, order.status(), "撤销后应回到配送中"),
                    () -> assertTrue(order.completedAt().isEmpty(), "完成时间应一并回滚"));
        }

        @Test
        @DisplayName("没有历史快照时 undo 不报错，只返回无可撤销说明")
        void shouldReportNothingToUndo() {
            Order order = facade.create("U-1001", "M-2001");

            String message = facade.undo(order.orderId());

            assertAll(
                    () -> assertTrue(message.contains("没有可撤销的快照"), "应返回无可撤销说明：" + message),
                    () -> assertEquals(OrderStatus.DRAFT, order.status(), "状态不应改变"));
        }
    }

    @Nested
    @DisplayName("状态迁移矩阵")
    class TransitionMatrix {

        @Test
        @DisplayName("两个终态没有任何出边")
        void shouldHaveNoOutgoingEdgeFromFinalStates() {
            assertAll(
                    () -> assertTrue(OrderStatus.COMPLETED.nextStatuses().isEmpty(), "已完成无出边"),
                    () -> assertTrue(OrderStatus.CANCELLED.nextStatuses().isEmpty(), "已取消无出边"),
                    () -> assertTrue(OrderStatus.COMPLETED.isFinalState(), "已完成是终态"),
                    () -> assertTrue(OrderStatus.CANCELLED.isFinalState(), "已取消是终态"),
                    () -> assertFalse(OrderStatus.COMPLETED.canTransferTo(OrderStatus.CANCELLED),
                            "终态不能再取消"),
                    () -> assertFalse(OrderStatus.CANCELLED.canTransferTo(OrderStatus.PENDING_PAYMENT),
                            "终态不能复活"));
        }

        @Test
        @DisplayName("DELIVERING 只能走向 COMPLETED，且不可取消")
        void shouldRestrictDeliveringTransitions() {
            assertAll(
                    () -> assertEquals(Set.of(OrderStatus.COMPLETED), OrderStatus.DELIVERING.nextStatuses(),
                            "配送中的唯一出边是已完成"),
                    () -> assertFalse(OrderStatus.DELIVERING.canTransferTo(OrderStatus.CANCELLED),
                            "迁移矩阵不允许配送中取消"),
                    () -> assertFalse(OrderStatus.DELIVERING.cancellable(), "配送中不可取消"),
                    () -> assertTrue(OrderStatus.DELIVERING.canTransferTo(OrderStatus.COMPLETED),
                            "配送中可以送达完成"));
        }

        @Test
        @DisplayName("非终态的关键迁移边与业务预期一致")
        void shouldMatchKeyTransitionEdges() {
            assertAll(
                    () -> assertTrue(OrderStatus.DRAFT.canTransferTo(OrderStatus.PENDING_PAYMENT),
                            "草稿可提交"),
                    () -> assertTrue(OrderStatus.DRAFT.canTransferTo(OrderStatus.CANCELLED), "草稿可取消"),
                    () -> assertFalse(OrderStatus.DRAFT.canTransferTo(OrderStatus.PAID),
                            "草稿不能跳过提交直接支付"),
                    () -> assertTrue(OrderStatus.PENDING_PAYMENT.canTransferTo(OrderStatus.PAID),
                            "待支付可支付"),
                    () -> assertFalse(OrderStatus.PENDING_PAYMENT.canTransferTo(OrderStatus.ASSIGNED),
                            "待支付不能直接派单"),
                    () -> assertTrue(OrderStatus.PAID.canTransferTo(OrderStatus.ASSIGNED), "已支付可派单"),
                    () -> assertTrue(OrderStatus.ASSIGNED.canTransferTo(OrderStatus.DELIVERING),
                            "已接单可取餐"),
                    () -> assertFalse(OrderStatus.ASSIGNED.canTransferTo(OrderStatus.COMPLETED),
                            "已接单不能直接完成"),
                    () -> assertFalse(OrderStatus.DRAFT.canTransferTo(null), "目标状态为空应判定为非法"),
                    () -> assertTrue(OrderStatus.DRAFT.canAddItems(), "只有草稿允许加菜"),
                    () -> assertFalse(OrderStatus.PENDING_PAYMENT.canAddItems(), "待支付不允许加菜"));
        }
    }
}
