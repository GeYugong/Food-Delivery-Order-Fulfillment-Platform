package com.example.delivery.domain.order;

import com.example.delivery.domain.merchant.Dish;
import com.example.delivery.domain.merchant.Merchant;
import com.example.delivery.domain.rider.Rider;
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
 * {@link Order} 聚合的单元测试（阶段一冒烟版，阶段四会继续扩充）。
 *
 * <p><b>设计模式关联：</b></p>
 * <ul>
 *   <li><b>状态模式</b>：用参数化思路覆盖「非法流转必须抛异常」，
 *       保证迁移矩阵与业务预期完全一致；</li>
 *   <li><b>建造者模式</b>：测试用建造者准备订单，断言更聚焦业务结果；</li>
 *   <li><b>备忘录 / 原型</b>：分别验证「回滚」与「再来一单」两条支线。</li>
 * </ul>
 *
 * @author 课程大作业
 */
@DisplayName("Order 订单聚合单元测试")
class OrderTest {

    /** 测试用骑手。 */
    private Rider rider;

    /** 测试用订单。 */
    private Order order;

    /** 每个用例前准备干净的数据，避免用例之间互相影响。 */
    @BeforeEach
    void setUp() {
        User user = new User("U-1001", "张三", "13812348888",
                "杭州市西湖区文一西路 969 号", new BigDecimal("200.00"), User.MemberLevel.NORMAL);
        Merchant merchant = new Merchant("M-2001", "川味小厨", "杭州市西湖区文三路 100 号",
                List.of(
                        Dish.available("DISH-001", "香辣鸡腿堡", "19.80", 50, "主食"),
                        Dish.available("DISH-002", "黄金薯条", "9.90", 100, "小食")),
                new BigDecimal("20.00"), new BigDecimal("5.00"));
        rider = Rider.idle("R-3001", "李骑手", "13900001111", "1.20", "4.90");

        order = new Order.OrderBuilder()
                .customer(user)
                .merchant(merchant)
                .deliveryAddress(user.address())
                .build();
    }

    /** 往订单里加一份鸡腿堡 + 一份薯条（小计 29.70）。 */
    private void fillWithStandardDishes() {
        order.addItem(OrderItem.of("DISH-001", "香辣鸡腿堡", "19.80", 1));
        order.addItem(OrderItem.of("DISH-002", "黄金薯条", "9.90", 1));
    }

    @Nested
    @DisplayName("下单与金额计算")
    class CreateAndPrice {

        @Test
        @DisplayName("新订单应为草稿态且明细为空")
        void shouldStartAsDraft() {
            assertAll(
                    () -> assertEquals(OrderStatus.DRAFT, order.status(), "初始状态应为草稿"),
                    () -> assertTrue(order.items().isEmpty(), "初始明细应为空"),
                    () -> assertTrue(order.status().canAddItems(), "草稿态应允许加菜"));
        }

        @Test
        @DisplayName("同名菜品应自动合并为一行且数量累加")
        void shouldMergeSameDish() {
            order.addItem(OrderItem.of("DISH-001", "香辣鸡腿堡", "19.80", 1));
            order.addItem(OrderItem.of("DISH-001", "香辣鸡腿堡", "19.80", 2));

            assertAll(
                    () -> assertEquals(1, order.items().size(), "同名菜品应合并为一行"),
                    () -> assertEquals(3, order.totalQuantity(), "数量应累加为 3"),
                    () -> assertEquals(new BigDecimal("59.40"), order.subtotal(), "小计应为 59.40"));
        }

        @Test
        @DisplayName("实付金额 = 小计 − 优惠 + 配送费")
        void shouldCalculateTotalAmount() {
            fillWithStandardDishes();
            order.applyCoupon("C-9001", new BigDecimal("5.00"));
            order.setDeliveryFee(new BigDecimal("5.00"));

            assertAll(
                    () -> assertEquals(new BigDecimal("29.70"), order.subtotal(), "小计应为 29.70"),
                    () -> assertEquals(new BigDecimal("29.70"), order.totalAmount(),
                            "29.70 - 5.00 + 5.00 应为 29.70"));
        }

        @Test
        @DisplayName("优惠金额不得超过菜品小计")
        void shouldRejectOverDiscount() {
            fillWithStandardDishes();
            assertThrows(IllegalArgumentException.class,
                    () -> order.applyCoupon("C-9001", new BigDecimal("100.00")),
                    "超额优惠应被拒绝");
        }

        @Test
        @DisplayName("提交空订单应被校验拦下")
        void shouldRejectEmptyOrderOnSubmit() {
            assertThrows(OrderValidationException.class, () -> order.submit(), "空订单不允许提交");
        }
    }

    @Nested
    @DisplayName("状态流转")
    class StateTransition {

        @Test
        @DisplayName("正常履约链路：草稿 → 待支付 → 已支付 → 已接单 → 配送中 → 已完成")
        void shouldFollowHappyPath() {
            fillWithStandardDishes();
            order.submit();
            order.markPaid("WALLET", "WX-0001");
            order.assignRider(rider);
            order.startDelivering();
            order.complete();

            assertAll(
                    () -> assertEquals(OrderStatus.COMPLETED, order.status(), "终态应为已完成"),
                    () -> assertTrue(order.status().isFinalState(), "已完成应判定为终态"),
                    () -> assertTrue(order.submittedAt().isPresent(), "应有提交时间"),
                    () -> assertTrue(order.paidAt().isPresent(), "应有支付时间"),
                    () -> assertTrue(order.assignedAt().isPresent(), "应有接单时间"),
                    () -> assertTrue(order.completedAt().isPresent(), "应有完成时间"));
        }

        @Test
        @DisplayName("草稿不能直接跳到已完成")
        void shouldRejectJumpFromDraftToCompleted() {
            IllegalOrderStateException ex = assertThrows(IllegalOrderStateException.class,
                    () -> order.complete(), "草稿直接完成应被拒绝");
            assertAll(
                    () -> assertEquals(OrderStatus.DRAFT, ex.current(), "异常应带出当前状态"),
                    () -> assertEquals(OrderStatus.COMPLETED, ex.target(), "异常应带出目标状态"));
        }

        @Test
        @DisplayName("订单提交后不允许再加菜")
        void shouldRejectAddItemAfterSubmit() {
            fillWithStandardDishes();
            order.submit();
            assertThrows(IllegalOrderStateException.class,
                    () -> order.addItem(OrderItem.of("DISH-001", "香辣鸡腿堡", "19.80", 1)),
                    "提交后加菜应被拒绝");
        }

        @Test
        @DisplayName("迁移矩阵应只允许合法的下一状态")
        void shouldExposeLegalNextStatuses() {
            assertAll(
                    () -> assertTrue(OrderStatus.DRAFT.canTransferTo(OrderStatus.PENDING_PAYMENT)),
                    () -> assertTrue(OrderStatus.DRAFT.canTransferTo(OrderStatus.CANCELLED)),
                    () -> assertFalse(OrderStatus.DRAFT.canTransferTo(OrderStatus.COMPLETED)),
                    () -> assertFalse(OrderStatus.COMPLETED.canTransferTo(OrderStatus.CANCELLED)),
                    () -> assertTrue(OrderStatus.COMPLETED.nextStatuses().isEmpty(), "终态不应有出边"),
                    () -> assertFalse(OrderStatus.DELIVERING.cancellable(), "配送中不允许取消"));
        }
    }

    @Nested
    @DisplayName("取消订单")
    class CancelOrder {

        @Test
        @DisplayName("待支付订单可以取消并记录原因")
        void shouldCancelPendingOrder() {
            fillWithStandardDishes();
            order.submit();
            order.cancel("用户不想吃了");

            assertAll(
                    () -> assertEquals(OrderStatus.CANCELLED, order.status(), "状态应为已取消"),
                    () -> assertEquals("用户不想吃了", order.cancelReason().orElseThrow(), "应记录取消原因"),
                    () -> assertTrue(order.cancelledAt().isPresent(), "应记录取消时间"));
        }

        @Test
        @DisplayName("配送中的订单不允许取消")
        void shouldRejectCancelWhileDelivering() {
            fillWithStandardDishes();
            order.submit();
            order.markPaid("WALLET", "WX-0002");
            order.assignRider(rider);
            order.startDelivering();

            assertThrows(IllegalOrderStateException.class,
                    () -> order.cancel("骑手已在路上还想取消"), "配送中不应允许取消");
        }

        @Test
        @DisplayName("已完成的订单不允许取消")
        void shouldRejectCancelAfterCompleted() {
            fillWithStandardDishes();
            order.submit();
            order.markPaid("WALLET", "WX-0003");
            order.assignRider(rider);
            order.startDelivering();
            order.complete();

            assertThrows(IllegalOrderStateException.class,
                    () -> order.cancel("送达后取消"), "已完成订单不应允许取消");
        }

        @Test
        @DisplayName("取消后可以回滚（备忘录模式）")
        void shouldRestoreAfterCancel() {
            fillWithStandardDishes();
            order.submit();
            OrderSnapshot snapshot = order.snapshot();
            order.cancel("误操作");
            order.restore(snapshot);

            assertAll(
                    () -> assertEquals(OrderStatus.PENDING_PAYMENT, order.status(), "应回滚到快照状态"),
                    () -> assertEquals(2, order.items().size(), "明细行数应回滚"),
                    () -> assertTrue(order.cancelReason().isEmpty(), "取消原因应被清空"),
                    () -> assertTrue(order.cancelledAt().isEmpty(), "取消时间应被清空"),
                    () -> assertTrue(order.submittedAt().isPresent(), "提交时间应保留"),
                    () -> assertEquals(snapshot.submittedAt(), order.submittedAt().orElseThrow(),
                            "提交时间应与快照完全一致"));
        }

        @Test
        @DisplayName("快照应完整覆盖可变状态（含金额、渠道、骑手）")
        void snapshotShouldCoverAllMutableState() {
            fillWithStandardDishes();
            order.submit();
            order.markPaid("WALLET", "WX-0009");
            order.assignRider(rider);
            OrderSnapshot snapshot = order.snapshot();
            order.startDelivering();
            order.restore(snapshot);

            assertAll(
                    () -> assertEquals(OrderStatus.ASSIGNED, order.status(), "应回到已接单"),
                    () -> assertEquals("WALLET", order.paymentMethod().orElseThrow(), "支付方式应还原"),
                    () -> assertEquals("WX-0009", order.paymentTradeNo().orElseThrow(), "流水号应还原"),
                    () -> assertEquals(rider.riderId(), order.rider().orElseThrow().riderId(),
                            "骑手应还原为同一人"),
                    () -> assertTrue(order.completedAt().isEmpty(), "完成时间应为空"));
        }

        @Test
        @DisplayName("快照与订单不匹配时应拒绝恢复")
        void shouldRejectMismatchedSnapshot() {
            fillWithStandardDishes();
            OrderSnapshot snapshot = order.snapshot();
            Order other = order.copyAsDraft("ORD-OTHER-001");

            assertThrows(IllegalArgumentException.class, () -> other.restore(snapshot),
                    "非同号订单不应接受该快照");
        }
    }

    @Nested
    @DisplayName("原型与访问者")
    class PrototypeAndVisitor {

        @Test
        @DisplayName("再来一单：新订单复用明细但状态回到草稿，原订单不受影响")
        void shouldCopyAsDraft() {
            fillWithStandardDishes();
            order.submit();
            order.markPaid("WALLET", "WX-0004");

            Order reorder = order.copyAsDraft("ORD-NEW-001");

            assertAll(
                    () -> assertEquals("ORD-NEW-001", reorder.orderId(), "应使用新的订单号"),
                    () -> assertEquals(OrderStatus.DRAFT, reorder.status(), "新订单应为草稿态"),
                    () -> assertEquals(2, reorder.items().size(), "应复用原订单明细"),
                    () -> assertEquals(OrderStatus.PAID, order.status(), "原订单状态不受影响"),
                    () -> assertFalse(reorder.paidAt().isPresent(), "新订单不应带上支付时间"));
        }

        @Test
        @DisplayName("访问者可对订单做横向统计（营业额）")
        void shouldSupportVisitor() {
            fillWithStandardDishes();

            BigDecimal revenue = order.accept(o -> o.subtotal());

            assertEquals(new BigDecimal("29.70"), revenue, "访问者应能读出订单小计");
        }
    }
}
