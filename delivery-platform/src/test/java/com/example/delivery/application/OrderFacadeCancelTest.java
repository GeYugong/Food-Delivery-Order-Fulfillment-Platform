package com.example.delivery.application;

import com.example.delivery.bootstrap.PlatformBootstrap;
import com.example.delivery.domain.order.IllegalOrderStateException;
import com.example.delivery.domain.order.Order;
import com.example.delivery.domain.order.OrderStatus;
import com.example.delivery.domain.payment.PaymentMethod;
import com.example.delivery.domain.support.OrderEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 取消订单与库存回滚的业务层单元测试（{@link OrderFacade#cancel(String, String)}）。
 *
 * <p><b>覆盖的设计模式集成点：</b></p>
 * <ul>
 *   <li><b>状态模式</b>：能否取消由状态对象（{@link com.example.delivery.domain.order.OrderState}）的白名单决定，
 *       配送中与终态一律拒绝；</li>
 *   <li><b>备忘录模式</b>：取消前自动记一次快照（{@code facade.backup}），为「撤销」留后路；</li>
 *   <li><b>命令模式的接收者</b>：{@link InventoryService#releaseAll(Order)} 把预占的库存还回去；</li>
 *   <li><b>观察者模式</b>：取消后发布 {@code OrderCancelled}，短信/商家/报表/审计四类监听器各自消费。</li>
 * </ul>
 *
 * @author 课程大作业
 */
@DisplayName("取消订单与库存回滚")
class OrderFacadeCancelTest {

    /** 装配好的平台（每个用例一份新数据）。 */
    private PlatformBootstrap.Platform platform;

    /** 被测门面。 */
    private OrderFacade facade;

    /** 每个用例前重新装配平台：assemble() 会重置仓储与库存，避免用例间互相污染。 */
    @BeforeEach
    void setUp() {
        platform = PlatformBootstrap.assemble();
        facade = platform.facade();
    }

    /**
     * 造一张「已提交（待支付）」的订单。
     *
     * @param userId     用户编号
     * @param merchantId 商家编号
     * @param dishId     菜品编号
     * @param quantity   份数
     * @return 待支付订单
     */
    private Order submitOrder(String userId, String merchantId, String dishId, int quantity) {
        Order order = facade.create(userId, merchantId);
        facade.addDish(order.orderId(), dishId, quantity);
        facade.submit(order.orderId(), null);
        return order;
    }

    /**
     * 造一张「已支付」的订单（用支付宝渠道，避免演示余额不足干扰用例）。
     *
     * @param userId     用户编号
     * @param merchantId 商家编号
     * @param dishId     菜品编号
     * @param quantity   份数
     * @return 已支付订单
     */
    private Order paidOrder(String userId, String merchantId, String dishId, int quantity) {
        Order order = submitOrder(userId, merchantId, dishId, quantity);
        facade.pay(order.orderId(), PaymentMethod.ALIPAY);
        return order;
    }

    // ==================================================================
    //                          用例分组
    // ==================================================================

    @Nested
    @DisplayName("允许取消的场景")
    class CancellableCases {

        @Test
        @DisplayName("待支付订单可以取消，并记录取消原因与时间")
        void shouldCancelPendingPaymentOrder() {
            Order order = submitOrder("U-1002", "M-2001", "DISH-001", 2);
            assertEquals(OrderStatus.PENDING_PAYMENT, order.status(), "前置条件：订单应为待支付");

            facade.cancel(order.orderId(), "用户不想吃了");

            assertAll(
                    () -> assertEquals(OrderStatus.CANCELLED, order.status(), "取消后状态应为已取消"),
                    () -> assertEquals("用户不想吃了", order.cancelReason().orElseThrow(), "应记录取消原因"),
                    () -> assertTrue(order.cancelledAt().isPresent(), "应记录取消时间"),
                    () -> assertTrue(order.status().isFinalState(), "已取消应判定为终态"),
                    () -> assertEquals(OrderStatus.CANCELLED, facade.getOrder(order.orderId()).status(),
                            "取消结果应已落库"));
        }

        @Test
        @DisplayName("已支付订单可以取消（商家拒单/无骑手可派走退款流程）")
        void shouldCancelPaidOrder() {
            Order order = paidOrder("U-1002", "M-2001", "DISH-001", 2);
            assertEquals(OrderStatus.PAID, order.status(), "前置条件：订单应为已支付");

            facade.cancel(order.orderId(), "商家拒单");

            assertAll(
                    () -> assertEquals(OrderStatus.CANCELLED, order.status(), "已支付订单应可取消"),
                    () -> assertEquals("商家拒单", order.cancelReason().orElseThrow(), "应记录取消原因"),
                    () -> assertTrue(order.cancelledAt().isPresent(), "应记录取消时间"));
        }

        @Test
        @DisplayName("草稿订单也可以取消")
        void shouldCancelDraftOrder() {
            Order order = facade.create("U-1001", "M-2001");
            facade.addDish(order.orderId(), "DISH-001", 1);

            facade.cancel(order.orderId(), "还没想好吃什么");

            assertEquals(OrderStatus.CANCELLED, order.status(), "草稿态在迁移矩阵中允许取消");
        }
    }

    @Nested
    @DisplayName("拒绝取消的场景")
    class RejectedCases {

        @Test
        @DisplayName("配送中不可取消，抛 IllegalOrderStateException")
        void shouldRejectCancelWhileDelivering() {
            Order order = paidOrder("U-1001", "M-2001", "DISH-001", 2);
            facade.dispatch(order.orderId());
            facade.startDelivery(order.orderId());
            assertEquals(OrderStatus.DELIVERING, order.status(), "前置条件：订单应为配送中");

            IllegalOrderStateException ex = assertThrows(IllegalOrderStateException.class,
                    () -> facade.cancel(order.orderId(), "骑手已在路上还想取消"),
                    "配送中的订单不应允许取消");

            assertAll(
                    () -> assertEquals(OrderStatus.DELIVERING, ex.current(), "异常应带出当前状态"),
                    () -> assertEquals(OrderStatus.DELIVERING, ex.target(),
                            "状态对象否决时 target 即为当前状态（跨状态矩阵校验的目标由 OrderStatus 负责）"),
                    () -> assertEquals(OrderStatus.DELIVERING, order.status(), "失败后订单状态不应改变"),
                    () -> assertTrue(order.cancelReason().isEmpty(), "失败后不应写入取消原因"));
        }

        @Test
        @DisplayName("已完成不可取消，抛 IllegalOrderStateException")
        void shouldRejectCancelAfterCompleted() {
            Order order = paidOrder("U-1001", "M-2001", "DISH-001", 2);
            facade.dispatch(order.orderId());
            facade.startDelivery(order.orderId());
            facade.complete(order.orderId());
            assertEquals(OrderStatus.COMPLETED, order.status(), "前置条件：订单应为已完成");

            IllegalOrderStateException ex = assertThrows(IllegalOrderStateException.class,
                    () -> facade.cancel(order.orderId(), "送达后想取消"),
                    "已完成订单不应允许取消");

            assertAll(
                    () -> assertEquals(OrderStatus.COMPLETED, ex.current(), "异常应带出当前状态"),
                    () -> assertEquals(OrderStatus.COMPLETED, order.status(), "失败后订单状态不应改变"),
                    () -> assertTrue(order.cancelReason().isEmpty(), "失败后不应写入取消原因"));
        }

        @Test
        @DisplayName("已取消的订单不能再取消一次（终态无出边）")
        void shouldRejectDoubleCancel() {
            Order order = submitOrder("U-1002", "M-2001", "DISH-001", 2);
            facade.cancel(order.orderId(), "第一次取消");

            assertThrows(IllegalOrderStateException.class,
                    () -> facade.cancel(order.orderId(), "第二次取消"),
                    "终态订单不应允许再次取消");

            assertEquals("第一次取消", order.cancelReason().orElseThrow(),
                    "二次取消失败后原因应保持第一次的值");
        }
    }

    @Nested
    @DisplayName("库存回滚")
    class InventoryRollback {

        @Test
        @DisplayName("提交扣减库存，取消后库存回到取消前的值")
        void shouldRollbackStockAfterCancel() {
            int before = facade.inventory().remaining("DISH-001");

            Order order = submitOrder("U-1001", "M-2001", "DISH-001", 3);
            int afterSubmit = facade.inventory().remaining("DISH-001");
            assertEquals(before - 3, afterSubmit, "提交成功后应预占 3 份库存");

            facade.cancel(order.orderId(), "不要了，请退库存");

            assertAll(
                    () -> assertEquals(before, facade.inventory().remaining("DISH-001"),
                            "取消后库存应回滚到取消前的值"),
                    () -> assertTrue(facade.inventory().hasEnough("DISH-001", before),
                            "回滚后库存应重新充足"));
        }

        @Test
        @DisplayName("多菜品订单取消时全部明细的库存都要回滚")
        void shouldRollbackAllItemsStock() {
            int burgerBefore = facade.inventory().remaining("DISH-001");
            int friesBefore = facade.inventory().remaining("DISH-002");

            Order order = facade.create("U-1001", "M-2001");
            facade.addDish(order.orderId(), "DISH-001", 2);
            facade.addDish(order.orderId(), "DISH-002", 4);
            facade.submit(order.orderId(), null);

            assertAll(
                    () -> assertEquals(burgerBefore - 2, facade.inventory().remaining("DISH-001"),
                            "鸡腿堡库存应扣 2 份"),
                    () -> assertEquals(friesBefore - 4, facade.inventory().remaining("DISH-002"),
                            "薯条库存应扣 4 份"));

            facade.cancel(order.orderId(), "整单取消");

            assertAll(
                    () -> assertEquals(burgerBefore, facade.inventory().remaining("DISH-001"),
                            "鸡腿堡库存应回滚"),
                    () -> assertEquals(friesBefore, facade.inventory().remaining("DISH-002"),
                            "薯条库存应回滚"));
        }

        @Test
        @DisplayName("已支付订单取消同样回滚库存")
        void shouldRollbackStockForPaidOrder() {
            int before = facade.inventory().remaining("DISH-101");

            Order order = paidOrder("U-1002", "M-2002", "DISH-101", 2);
            assertEquals(before - 2, facade.inventory().remaining("DISH-101"), "提交后应扣 2 份库存");

            facade.cancel(order.orderId(), "商家缺货");

            assertEquals(before, facade.inventory().remaining("DISH-101"),
                    "已支付订单取消也应回滚库存");
        }
    }

    @Nested
    @DisplayName("取消事件（观察者模式）")
    class CancellationEvents {

        @Test
        @DisplayName("取消会发布 OrderCancelled 事件并计入报表监听器")
        void shouldPublishCancelledEvent() {
            Order order = submitOrder("U-1002", "M-2001", "DISH-001", 2);

            facade.cancel(order.orderId(), "临时有事");

            boolean hasCancelledEvent = platform.publisher().publishedEvents().stream()
                    .anyMatch(event -> OrderEvent.ORDER_CANCELLED.equals(event.type())
                            && order.orderId().equals(event.orderId()));

            assertAll(
                    () -> assertTrue(hasCancelledEvent,
                            "事件流水中应存在本订单的 OrderCancelled 事件"),
                    () -> assertEquals(1, platform.reportListener().counters()
                                    .getOrDefault(OrderEvent.ORDER_CANCELLED, 0).intValue(),
                            "报表监听器应统计到 1 次取消事件"),
                    () -> assertTrue(platform.smsListener().sentMessages().stream()
                                    .anyMatch(message -> message.contains("已取消")),
                            "短信监听器应发出取消通知"),
                    () -> assertTrue(platform.merchantListener().messages().stream()
                                    .anyMatch(message -> message.contains("已取消")),
                            "商家端监听器应收到停做提醒"));
        }
    }
}
