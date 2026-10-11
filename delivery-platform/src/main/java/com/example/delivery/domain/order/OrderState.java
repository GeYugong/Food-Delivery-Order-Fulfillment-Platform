package com.example.delivery.domain.order;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 订单状态对象（状态模式 State 的「状态」角色）。
 *
 * <p><b>设计模式：状态模式（State）</b></p>
 *
 * <p><b>为什么在 {@link OrderStatus} 之外还要有本类：</b>
 * {@code OrderStatus} 是「数据」（订单现在处于什么状态，供持久化/报表使用），
 * 而本类族是「行为」（在这个状态下，哪些操作被允许、执行后迁移到哪个状态）。</p>
 *
 * <p>如果不用状态模式，这些判断会以 {@code switch (order.status()) { case PAID: ... case ASSIGNED: ... }}
 * 的形式散落在应用服务、控制台、报表等多处；每新增一个状态就要修改所有 switch。
 * 用状态模式后：<b>每个状态只关心自己的合法行为</b>，新增状态只需新增一个类并注册进
 * {@link #of(OrderStatus)}，符合开闭原则。</p>
 *
 * <p>默认实现把「不允许的操作」统一抛 {@link IllegalOrderStateException}，
 * 每个具体状态只覆盖它真正允许的操作（白名单式写法，比黑名单安全）。</p>
 *
 * @author 课程大作业
 */
public abstract class OrderState {

    /** 状态注册表：枚举 → 状态对象，避免每次调用都 new。 */
    private static final Map<OrderStatus, OrderState> REGISTRY = new EnumMap<>(OrderStatus.class);

    static {
        REGISTRY.put(OrderStatus.DRAFT, new DraftState());
        REGISTRY.put(OrderStatus.PENDING_PAYMENT, new PendingPaymentState());
        REGISTRY.put(OrderStatus.PAID, new PaidState());
        REGISTRY.put(OrderStatus.ASSIGNED, new AssignedState());
        REGISTRY.put(OrderStatus.DELIVERING, new DeliveringState());
        REGISTRY.put(OrderStatus.COMPLETED, new CompletedState());
        REGISTRY.put(OrderStatus.CANCELLED, new CancelledState());
    }

    /**
     * 按枚举取出状态对象（工厂方法思想：把「枚举 → 对象」的映射收敛在一处）。
     *
     * @param status 订单状态枚举
     * @return 状态对象
     */
    public static OrderState of(OrderStatus status) {
        OrderState state = REGISTRY.get(status);
        if (state == null) {
            throw new IllegalArgumentException("未注册的订单状态：" + status);
        }
        return state;
    }

    /**
     * 组合版入口：按订单当前状态取出状态对象。
     *
     * @param order 订单
     * @return 状态对象
     */
    public static OrderState of(Order order) {
        return of(order.status());
    }

    /** @return 本状态对应的枚举，供校验与持久化使用 */
    public abstract OrderStatus status();

    /** @return 中文名，例如「配送中」 */
    public String name() {
        return status().label();
    }

    /**
     * 本状态允许执行的操作名列表（用于控制台菜单与文档，而不是用来做判断）。
     *
     * @return 操作名列表
     */
    public abstract List<String> allowedOperations();

    /**
     * 提交订单。
     *
     * @param order 订单
     * @throws IllegalOrderStateException 本状态不允许提交时抛出
     */
    public void submit(Order order) {
        reject(order, "提交");
    }

    /**
     * 支付。
     *
     * @param order         订单
     * @param paymentMethod 支付方式
     * @param tradeNo       支付流水号
     * @throws IllegalOrderStateException 本状态不允许支付时抛出
     */
    public void pay(Order order, String paymentMethod, String tradeNo) {
        reject(order, "支付");
    }

    /**
     * 派单（骑手接单）。
     *
     * @param order 订单
     * @param rider 骑手
     * @throws IllegalOrderStateException 本状态不允许派单时抛出
     */
    public void assignRider(Order order, com.example.delivery.domain.rider.Rider rider) {
        reject(order, "派单");
    }

    /**
     * 骑手取餐，开始配送。
     *
     * @param order 订单
     * @throws IllegalOrderStateException 本状态不允许取餐时抛出
     */
    public void startDelivery(Order order) {
        reject(order, "取餐配送");
    }

    /**
     * 送达完成。
     *
     * @param order 订单
     * @throws IllegalOrderStateException 本状态不允许完成时抛出
     */
    public void complete(Order order) {
        reject(order, "完成");
    }

    /**
     * 取消订单。
     *
     * @param order  订单
     * @param reason 取消原因
     * @throws IllegalOrderStateException 本状态不允许取消时抛出
     */
    public void cancel(Order order, String reason) {
        reject(order, "取消");
    }

    /**
     * 统一拒绝入口。
     *
     * @param order      订单
     * @param operation  操作名
     * @throws IllegalOrderStateException 总是抛出
     */
    protected void reject(Order order, String operation) {
        throw new IllegalOrderStateException(order.orderId(), status(), status(),
                String.format("当前状态[%s]不允许执行「%s」操作；本状态允许的操作：%s",
                        name(), operation, allowedOperations()));
    }

    @Override
    public String toString() {
        return name();
    }

    // ==================================================================
    //                         具体状态实现
    // ==================================================================

    /**
     * 草稿状态：允许继续加菜、提交、取消。
     *
     * <p>此状态是唯一允许修改菜品明细的状态。</p>
     */
    public static final class DraftState extends OrderState {

        @Override
        public OrderStatus status() {
            return OrderStatus.DRAFT;
        }

        @Override
        public List<String> allowedOperations() {
            return List.of("加菜", "提交订单", "取消订单");
        }

        @Override
        public void submit(Order order) {
            order.submit();
        }

        @Override
        public void cancel(Order order, String reason) {
            order.cancel(reason);
        }
    }

    /**
     * 待支付状态：允许支付，或取消（超时未支付 / 用户主动放弃）。
     */
    public static final class PendingPaymentState extends OrderState {

        @Override
        public OrderStatus status() {
            return OrderStatus.PENDING_PAYMENT;
        }

        @Override
        public List<String> allowedOperations() {
            return List.of("支付", "取消订单");
        }

        @Override
        public void pay(Order order, String paymentMethod, String tradeNo) {
            order.markPaid(paymentMethod, tradeNo);
        }

        @Override
        public void cancel(Order order, String reason) {
            order.cancel(reason);
        }
    }

    /**
     * 已支付状态：等待派单；允许取消（商家拒单、无骑手可派，走退款）。
     */
    public static final class PaidState extends OrderState {

        @Override
        public OrderStatus status() {
            return OrderStatus.PAID;
        }

        @Override
        public List<String> allowedOperations() {
            return List.of("派单", "取消订单");
        }

        @Override
        public void assignRider(Order order, com.example.delivery.domain.rider.Rider rider) {
            order.assignRider(rider);
        }

        @Override
        public void cancel(Order order, String reason) {
            order.cancel(reason);
        }
    }

    /**
     * 已接单状态：骑手前往商家；允许取餐与取消（骑手异常时由中介者改派）。
     */
    public static final class AssignedState extends OrderState {

        @Override
        public OrderStatus status() {
            return OrderStatus.ASSIGNED;
        }

        @Override
        public List<String> allowedOperations() {
            return List.of("骑手取餐", "取消订单");
        }

        @Override
        public void startDelivery(Order order) {
            order.startDelivering();
        }

        @Override
        public void cancel(Order order, String reason) {
            order.cancel(reason);
        }
    }

    /**
     * 配送中状态：骑手已在路上，只允许送达（业务上不允许取消）。
     */
    public static final class DeliveringState extends OrderState {

        @Override
        public OrderStatus status() {
            return OrderStatus.DELIVERING;
        }

        @Override
        public List<String> allowedOperations() {
            return List.of("送达完成");
        }

        @Override
        public void complete(Order order) {
            order.complete();
        }
    }

    /**
     * 已完成状态（终态）：任何修改类操作都不允许。
     */
    public static final class CompletedState extends OrderState {

        @Override
        public OrderStatus status() {
            return OrderStatus.COMPLETED;
        }

        @Override
        public List<String> allowedOperations() {
            return List.of("（终态，无可用操作；仅可查询与访问者统计）");
        }
    }

    /**
     * 已取消状态（终态）：任何修改类操作都不允许。
     */
    public static final class CancelledState extends OrderState {

        @Override
        public OrderStatus status() {
            return OrderStatus.CANCELLED;
        }

        @Override
        public List<String> allowedOperations() {
            return List.of("（终态，无可用操作；仅可查询与访问者统计）");
        }
    }
}
