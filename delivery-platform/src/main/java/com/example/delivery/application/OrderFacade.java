package com.example.delivery.application;

import com.example.delivery.domain.coupon.Coupon;
import com.example.delivery.domain.merchant.Dish;
import com.example.delivery.domain.merchant.Merchant;
import com.example.delivery.domain.order.Order;
import com.example.delivery.domain.order.OrderItem;
import com.example.delivery.domain.order.OrderState;
import com.example.delivery.domain.order.OrderStatus;
import com.example.delivery.domain.payment.Payment;
import com.example.delivery.domain.payment.PaymentMethod;
import com.example.delivery.domain.pricing.PricingContext;
import com.example.delivery.domain.rider.Rider;
import com.example.delivery.domain.support.CouponEvaluator;
import com.example.delivery.domain.support.DomainEventPublisher;
import com.example.delivery.domain.support.OrderEvent;
import com.example.delivery.domain.support.OrderStore;
import com.example.delivery.domain.support.PaymentGateway;
import com.example.delivery.domain.user.User;
import com.example.delivery.infrastructure.payment.PaymentGatewayRegistry;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 订单业务门面（外观模式 Facade 的「外观」角色，同时是业务闭环的编排中心）。
 *
 * <p><b>设计模式：外观模式（Facade）</b></p>
 *
 * <p><b>它隐藏了什么：</b>调用一个「提交订单」需要顺序正确地对 6 个子系统做事——
 * 取用户与商家、跑校验链、算配送费（策略）、算优惠（解释器）、算实付、存订单、发事件。
 * 如果让控制台自己做这些，控制台就会变成「业务逻辑收集器」，且任何一步顺序错了都会出 Bug。</p>
 *
 * <p>门面提供的一条 {@link #submit(String, String)} 就是完整的业务事务边界：
 * 校验通过 → 计价 → 落库 → 发事件；任何一步失败都保持订单原状（不产生半成品订单）。</p>
 *
 * <p><b>本类编排的设计模式全景：</b></p>
 * <table border="1">
 *   <caption>方法 → 使用的模式</caption>
 *   <tr><th>方法</th><th>使用的设计模式</th></tr>
 *   <tr><td>{@link #create(String, String)}</td><td>建造者（OrderBuilder）</td></tr>
 *   <tr><td>{@link #addDish(String, String, int)}</td><td>—（聚合内聚，行为在 Order 内）</td></tr>
 *   <tr><td>{@link #submit(String, String)}</td><td>职责链（校验）、策略（配送费）、解释器（优惠券）</td></tr>
 *   <tr><td>{@link #pay(String, String)}</td><td>适配器（支付渠道）、观察者（支付成功事件）</td></tr>
 *   <tr><td>{@link #dispatch(String)} / {@link #assignRider}</td><td>中介者（派单中心）、策略（调度）、命令（可撤销）</td></tr>
 *   <tr><td>{@link #startDelivery} / {@link #complete} / {@link #cancel}</td><td>状态（状态对象）、观察者、备忘录（快照）</td></tr>
 *   <tr><td>{@link #undo(String)}</td><td>备忘录（回滚）</td></tr>
 *   <tr><td>{@link #archiveOrders()}</td><td>模板方法（归档骨架）</td></tr>
 * </table>
 *
 * <p><b>为什么门面不写成静态工具类：</b>它持有仓储、策略、事件总线等协作者，
 * 需要可替换（测试时注入内存仓储与假支付网关），因此用建造者装配实例。</p>
 *
 * @author 课程大作业
 */
public class OrderFacade {

    /** 订单仓储。 */
    private final OrderStore orderStore;

    /** 支付流水仓储。 */
    private final OrderStore.PaymentLedger paymentLedger;

    /** 用户仓储。 */
    private final OrderStore.UserStore userStore;

    /** 商家仓储。 */
    private final OrderStore.MerchantStore merchantStore;

    /** 骑手仓储。 */
    private final OrderStore.RiderStore riderStore;

    /** 库存服务。 */
    private final InventoryService inventory;

    /** 优惠券求值器（解释器模式的边界接口）。 */
    private final com.example.delivery.domain.support.CouponEvaluator couponEvaluator;

    /** 优惠券解释器（解释器模式的具体实现，用于按券对象求值并输出 AST 说明）。 */
    private final com.example.delivery.infrastructure.coupon.CouponRuleInterpreter couponInterpreter;

    /** 计价上下文（策略模式）。 */
    private final PricingContext pricing;

    /** 事件发布器（观察者模式）。 */
    private final DomainEventPublisher publisher;

    /** 派单中介者。 */
    private final DispatchCenter dispatchCenter;

    /** 备忘录管理器。 */
    private final OrderMemoManager memoManager;

    /** 支付网关注册表（适配器集合）。 */
    private final PaymentGatewayRegistry gatewayRegistry;

    /** 内置演示优惠券库（编号 → 券）。 */
    private final java.util.Map<String, Coupon> couponLibrary = new java.util.LinkedHashMap<>();

    /** 校验链轨迹（最近一次提交的完整轨迹，供控制台展示）。 */
    private List<String> lastValidationTrace = List.of();

    /** 最近一次计价说明。 */
    private String lastPricingExplanation = "";

    /** 最近一次优惠说明。 */
    private String lastCouponExplanation = "";

    /**
     * 私有构造器：强制通过 {@link Builder} 装配，避免参数顺序错乱（与 Order 的建造者思路一致）。
     *
     * @param builder 建造者
     */
    private OrderFacade(Builder builder) {
        this.orderStore = builder.orderStore;
        this.paymentLedger = builder.paymentLedger;
        this.userStore = builder.userStore;
        this.merchantStore = builder.merchantStore;
        this.riderStore = builder.riderStore;
        this.inventory = builder.inventory;
        this.couponEvaluator = builder.couponEvaluator;
        this.couponInterpreter = builder.couponInterpreter;
        this.pricing = builder.pricing;
        this.publisher = builder.publisher;
        this.dispatchCenter = builder.dispatchCenter;
        this.memoManager = builder.memoManager;
        this.gatewayRegistry = builder.gatewayRegistry;
        this.couponLibrary.putAll(builder.coupons);
    }

    // ==================================================================
    //                        创建与明细
    // ==================================================================

    /**
     * 创建草稿订单。
     *
     * @param userId     用户编号
     * @param merchantId 商家编号
     * @return 草稿订单
     */
    public Order create(String userId, String merchantId) {
        User user = requireUser(userId);
        Merchant merchant = requireMerchant(merchantId);
        // 建造者模式：组装 + build() 时统一校验不变量
        Order order = new Order.OrderBuilder()
                .customer(user)
                .merchant(merchant)
                .deliveryAddress(user.address())
                .build();
        orderStore.save(order);
        return order;
    }

    /**
     * 向订单追加菜品（同名菜品自动合并）。
     *
     * @param orderId  订单号
     * @param dishId   菜品编号
     * @param quantity 数量
     * @return 更新后的订单
     */
    public Order addDish(String orderId, String dishId, int quantity) {
        Order order = requireOrder(orderId);
        Dish dish = order.merchant().findDish(dishId)
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("商家[%s]不售卖菜品 %s", order.merchant().name(), dishId)));
        if (!dish.available()) {
            throw new IllegalArgumentException("菜品已下架：" + dish.name());
        }
        order.addItem(new OrderItem(dish.dishId(), dish.name(), dish.price(), quantity));
        orderStore.save(order);
        return order;
    }

    /**
     * 移除菜品。
     *
     * @param orderId 订单号
     * @param dishId  菜品编号
     * @return 更新后的订单
     */
    public Order removeDish(String orderId, String dishId) {
        Order order = requireOrder(orderId);
        order.removeItem(dishId);
        orderStore.save(order);
        return order;
    }

    // ==================================================================
    //                    提交：校验链 + 计价 + 优惠
    // ==================================================================

    /**
     * 提交订单：跑完整条业务编排（校验链 → 计价策略 → 优惠券解释 → 计算实付 → 落库 → 发事件）。
     *
     * @param orderId  订单号
     * @param couponId 优惠券编号，可为 null
     * @return 待支付状态的订单（金额已算好）
     */
    public Order submit(String orderId, String couponId) {
        Order order = requireOrder(orderId);
        User user = order.customer();
        Merchant merchant = order.merchant();

        // ---------- ① 职责链：一票否决 ----------
        // 注意：校验失败会抛异常，因此用 try/finally 保证「被拦在哪一环」的轨迹一定被记录下来
        var chain = com.example.delivery.application.validator.ValidatorChainFactory.defaultChain(inventory);
        chain.resetTrace();
        var context = OrderValidationContext.of(order, user, merchant)
                .withRecentOrderCount(orderStore.findByUser(user.userId()).size() % 4);
        try {
            chain.validate(context);
        } finally {
            this.lastValidationTrace = chain.collectTrace();
        }

        // ---------- ② 策略模式：配送费 ----------
        BigDecimal deliveryFee = pricing.deliveryFeeFor(order);
        order.setDeliveryFee(deliveryFee);
        this.lastPricingExplanation = String.format("%s（%s）→ 配送费 %s 元",
                pricing.currentStrategy().name(), pricing.currentStrategy().rule(),
                deliveryFee.toPlainString());

        // ---------- ③ 解释器模式：优惠券抵扣 ----------
        BigDecimal discount = BigDecimal.ZERO;
        if (couponId != null && !couponId.isBlank()) {
            Coupon coupon = couponLibrary.get(couponId);
            if (coupon == null) {
                throw new IllegalArgumentException("优惠券不存在：" + couponId);
            }
            if (!coupon.validOnToday()) {
                throw new IllegalArgumentException("优惠券已过期：" + couponId);
            }
            discount = couponInterpreter != null
                    ? couponInterpreter.evaluateCoupon(coupon, order)
                    : couponEvaluator.evaluate(order);
            order.applyCoupon(coupon.couponId(), discount);
            this.lastCouponExplanation = couponInterpreter != null
                    ? couponInterpreter.explain() : couponEvaluator.explain();
        } else {
            this.lastCouponExplanation = "未使用优惠券";
        }

        // ---------- ④ 预占库存（下单即锁库存，避免超卖） ----------
        if (!inventory.reserveAll(order)) {
            // 校验链刚检查过库存，走到这里说明并发下被别人抢走了，属于正常的业务失败
            throw new com.example.delivery.domain.order.OrderValidationException("库存预占",
                    "提交时库存已被抢占，请调整数量后重试");
        }

        // ---------- ⑤ 状态流转 + 落库 + 事件 ----------
        order.submit();
        orderStore.save(order);
        publisher.publishFor(order, OrderEvent.ORDER_SUBMITTED,
                String.format("小计 %s 元，优惠 %s 元，配送费 %s 元，实付 %s 元",
                        order.subtotal().toPlainString(), discount.toPlainString(),
                        order.deliveryFee().toPlainString(), order.totalAmount().toPlainString()));
        return order;
    }

    /**
     * 优惠试算：只计算不落库（用于「提交前预览实付金额」）。
     *
     * @param orderId  订单号
     * @param couponId 优惠券编号，可为 null
     * @return 试算后的实付金额
     */
    public BigDecimal previewTotal(String orderId, String couponId) {
        Order order = requireOrder(orderId);
        BigDecimal fee = pricing.deliveryFeeFor(order);
        BigDecimal discount = BigDecimal.ZERO;
        if (couponId != null && !couponId.isBlank()) {
            discount = couponEvaluator.evaluate(order);
        }
        BigDecimal total = order.subtotal().subtract(discount).add(fee);
        return total.signum() < 0 ? BigDecimal.ZERO.setScale(2) : total.setScale(2,
                java.math.RoundingMode.HALF_UP);
    }

    // ==================================================================
    //                          支付
    // ==================================================================

    /**
     * 支付订单：通过支付适配器调用具体渠道。
     *
     * @param orderId 订单号
     * @param method  支付方式
     * @return 更新后的订单
     * @throws IllegalStateException 支付失败时抛出（含失败原因）
     */
    public Order pay(String orderId, PaymentMethod method) {
        Order order = requireOrder(orderId);

        // 状态守卫：用状态对象而不是 if 判断（状态模式）
        OrderState state = OrderState.of(order);
        if (order.status() != OrderStatus.PENDING_PAYMENT) {
            throw new com.example.delivery.domain.order.IllegalOrderStateException(
                    order.orderId(), order.status(), OrderStatus.PAID,
                    "只有[待支付]订单可以支付；当前状态允许的操作：" + state.allowedOperations());
        }

        // 适配器模式：按渠道取出统一网关
        PaymentGateway gateway = gatewayRegistry.require(method);
        BigDecimal amount = order.totalAmount();
        if (!gateway.supports(amount, order.customer().userId())) {
            throw new IllegalStateException(String.format("[%s]不支持该笔支付：%s 元",
                    gateway.channelLabel(), amount.toPlainString()));
        }

        Payment payment = gateway.pay(order.orderId(), amount, order.customer().userId());
        paymentLedger.record(payment);

        if (!payment.succeeded()) {
            // 支付失败：保持订单在待支付状态，允许用户换渠道重试
            publisher.publishFor(order, OrderEvent.ORDER_PAID,
                    String.format("支付失败（%s）：%s", gateway.channelLabel(), payment.failureReason()));
            throw new IllegalStateException(String.format("支付失败[%s]：%s",
                    gateway.channelLabel(), payment.failureReason()));
        }

        // 支付成功：状态机推进 + 落库 + 事件
        state.pay(order, method.name(), payment.tradeNo());
        orderStore.save(order);
        publisher.publishFor(order, OrderEvent.ORDER_PAID,
                String.format("%s 支付 %s 元，流水号 %s",
                        gateway.channelLabel(), amount.toPlainString(), payment.tradeNo()));
        return order;
    }

    // ==================================================================
    //                        派单与配送
    // ==================================================================

    /**
     * 派单：委托给中介者（派单中心）。
     *
     * @param orderId 订单号
     * @return 派单结果
     */
    public DispatchOutcome dispatch(String orderId) {
        return dispatchCenter.dispatch(orderId);
    }

    /**
     * 显式指派骑手。
     *
     * @param orderId 订单号
     * @param riderId 骑手编号
     * @return 派单结果
     */
    public DispatchOutcome assignRider(String orderId, String riderId) {
        return dispatchCenter.assign(orderId, riderId);
    }

    /**
     * 切换调度策略（策略模式运行期切换的入口）。
     *
     * @param strategy 新策略
     */
    public void useDispatchStrategy(com.example.delivery.domain.support.DispatchStrategy strategy) {
        dispatchCenter.useStrategy(strategy);
    }

    /**
     * 骑手取餐：已接单 → 配送中。
     *
     * @param orderId 订单号
     * @return 更新后的订单
     */
    public Order startDelivery(String orderId) {
        Order order = requireOrder(orderId);
        backup(order, "骑手取餐前");
        OrderState.of(order).startDelivery(order);
        orderStore.save(order);
        publisher.publishFor(order, OrderEvent.ORDER_DELIVERING,
                order.rider().map(rider -> "骑手 " + rider.name() + " 已取餐").orElse("骑手已取餐"));
        return order;
    }

    /**
     * 送达完成：配送中 → 已完成；同时释放骑手负载。
     *
     * @param orderId 订单号
     * @return 更新后的订单
     */
    public Order complete(String orderId) {
        Order order = requireOrder(orderId);
        String riderId = order.rider().map(Rider::riderId).orElse(null);
        backup(order, "送达确认前");
        OrderState.of(order).complete(order);
        orderStore.save(order);

        String riderInfo = "无骑手信息";
        if (riderId != null) {
            riderInfo = "骑手负载已释放：" + dispatchCenter.releaseRider(riderId);
        }
        publisher.publishFor(order, OrderEvent.ORDER_COMPLETED,
                String.format("实付 %s 元；%s", order.totalAmount().toPlainString(), riderInfo));
        return order;
    }

    // ==================================================================
    //                          取消与撤销
    // ==================================================================

    /**
     * 取消订单：状态对象守卫 → 回滚库存 → 发事件。
     *
     * @param orderId 订单号
     * @param reason  取消原因
     * @return 更新后的订单
     */
    public Order cancel(String orderId, String reason) {
        Order order = requireOrder(orderId);
        backup(order, "取消前");
        OrderState.of(order).cancel(order, reason);
        orderStore.save(order);

        // 库存回滚：只有「已支付」之后才真正扣过库存，这里统一按已扣处理更安全
        inventory.releaseAll(order);
        publisher.publishFor(order, OrderEvent.ORDER_CANCELLED,
                String.format("原因：%s；库存已回滚", reason));
        return order;
    }

    /**
     * 撤销到上一个快照（备忘录模式）。
     *
     * @param orderId 订单号
     * @return 撤销说明
     */
    public String undo(String orderId) {
        Order order = requireOrder(orderId);
        String result = memoManager.undo(order);
        orderStore.save(order);
        return result;
    }

    /**
     * 记录一次快照（备忘录模式的入口，供控制台在关键操作前调用）。
     *
     * @param order  订单
     * @param reason 记录原因
     * @return 快照编号
     */
    public String backup(Order order, String reason) {
        return memoManager.backup(order, reason);
    }

    // ==================================================================
    //                        查询与归档
    // ==================================================================

    /**
     * 查询订单。
     *
     * @param orderId 订单号
     * @return 订单
     */
    public Order getOrder(String orderId) {
        return requireOrder(orderId);
    }

    /**
     * 查询订单（Optional 视图）。
     *
     * @param orderId 订单号
     * @return 可能为空的订单
     */
    public Optional<Order> findOrder(String orderId) {
        return orderStore.findById(orderId);
    }

    /**
     * 查询全部订单。
     *
     * @return 订单列表
     */
    public List<Order> listOrders() {
        return orderStore.findAll();
    }

    /**
     * 查询某用户的订单。
     *
     * @param userId 用户编号
     * @return 订单列表
     */
    public List<Order> listOrdersOfUser(String userId) {
        return orderStore.findByUser(userId);
    }

    /**
     * 查询某状态的订单。
     *
     * @param status 状态
     * @return 订单列表
     */
    public List<Order> listOrdersByStatus(OrderStatus status) {
        return orderStore.findByStatus(status);
    }

    /**
     * 归档历史订单（模板方法模式：流程骨架在仓储里固定）。
     *
     * @return 归档数量
     */
    public int archiveOrders() {
        // 归档条件：创建时间早于「现在 + 1 分钟」（演示用：所有订单都已过期可归档）+ 已是终态
        return orderStore.archiveBefore(System.currentTimeMillis() + 60_000L);
    }

    /**
     * 查询支付流水。
     *
     * @param orderId 订单号
     * @return 支付记录列表
     */
    public List<Payment> paymentsOf(String orderId) {
        return paymentLedger.findByOrder(orderId);
    }

    /**
     * 查询全部支付流水。
     *
     * @return 支付记录列表
     */
    public List<Payment> allPayments() {
        return paymentLedger.findAll();
    }

    // ==================================================================
    //                     基础数据访问与内部工具
    // ==================================================================

    /** @return 用户仓储 */
    public OrderStore.UserStore users() {
        return userStore;
    }

    /** @return 商家仓储 */
    public OrderStore.MerchantStore merchants() {
        return merchantStore;
    }

    /** @return 骑手仓储 */
    public OrderStore.RiderStore riders() {
        return riderStore;
    }

    /** @return 库存服务 */
    public InventoryService inventory() {
        return inventory;
    }

    /** @return 事件发布器 */
    public DomainEventPublisher publisher() {
        return publisher;
    }

    /** @return 派单中心 */
    public DispatchCenter dispatchCenter() {
        return dispatchCenter;
    }

    /** @return 备忘录管理器 */
    public OrderMemoManager memoManager() {
        return memoManager;
    }

    /** @return 计价上下文 */
    public PricingContext pricing() {
        return pricing;
    }

    /** @return 支付网关注册表 */
    public PaymentGatewayRegistry gateways() {
        return gatewayRegistry;
    }

    /**
     * 注册一张可用优惠券（演示数据装配用）。
     *
     * @param coupon 优惠券
     */
    public void registerCoupon(Coupon coupon) {
        couponLibrary.put(coupon.couponId(), coupon);
    }

    /** @return 优惠券库（只读） */
    public java.util.Map<String, Coupon> couponLibrary() {
        return java.util.Collections.unmodifiableMap(couponLibrary);
    }

    /** @return 最近一次提交的校验链轨迹 */
    public List<String> lastValidationTrace() {
        return lastValidationTrace;
    }

    /** @return 最近一次的计价说明 */
    public String lastPricingExplanation() {
        return lastPricingExplanation;
    }

    /** @return 最近一次的优惠券求值说明 */
    public String lastCouponExplanation() {
        return lastCouponExplanation;
    }

    /**
     * 取用户，不存在则抛业务异常。
     *
     * @param userId 用户编号
     * @return 用户
     */
    private User requireUser(String userId) {
        return userStore.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在：" + userId));
    }

    /**
     * 取商家，不存在则抛业务异常。
     *
     * @param merchantId 商家编号
     * @return 商家
     */
    private Merchant requireMerchant(String merchantId) {
        return merchantStore.findById(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("商家不存在：" + merchantId));
    }

    /**
     * 取订单，不存在则抛业务异常。
     *
     * @param orderId 订单号
     * @return 订单
     */
    private Order requireOrder(String orderId) {
        return orderStore.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("订单不存在：" + orderId));
    }

    /**
     * 门面建造者。
     *
     * <p><b>设计模式：建造者模式（Builder）</b>——门面依赖 10 个以上协作者，
     * 用建造者可以做到「只传关心的那部分、其余取默认实现」，
     * 测试时注入内存仓储与假网关，生产时注入真实实现。</p>
     */
    public static final class Builder {

        private OrderStore orderStore;
        private OrderStore.PaymentLedger paymentLedger;
        private OrderStore.UserStore userStore;
        private OrderStore.MerchantStore merchantStore;
        private OrderStore.RiderStore riderStore;
        private InventoryService inventory = InventoryService.getInstance();
        private CouponEvaluator couponEvaluator;
        private com.example.delivery.infrastructure.coupon.CouponRuleInterpreter couponInterpreter;
        private PricingContext pricing = new PricingContext();
        private DomainEventPublisher publisher = new DomainEventPublisher();
        private DispatchCenter dispatchCenter;
        private OrderMemoManager memoManager = new OrderMemoManager();
        private PaymentGatewayRegistry gatewayRegistry = PaymentGatewayRegistry.getInstance();
        private final java.util.Map<String, Coupon> coupons = new java.util.LinkedHashMap<>();

        /** @param store 订单仓储 @return this */
        public Builder orderStore(OrderStore store) {
            this.orderStore = store;
            return this;
        }

        /** @param ledger 支付流水仓储 @return this */
        public Builder paymentLedger(OrderStore.PaymentLedger ledger) {
            this.paymentLedger = ledger;
            return this;
        }

        /** @param store 用户仓储 @return this */
        public Builder userStore(OrderStore.UserStore store) {
            this.userStore = store;
            return this;
        }

        /** @param store 商家仓储 @return this */
        public Builder merchantStore(OrderStore.MerchantStore store) {
            this.merchantStore = store;
            return this;
        }

        /** @param store 骑手仓储 @return this */
        public Builder riderStore(OrderStore.RiderStore store) {
            this.riderStore = store;
            return this;
        }

        /** @param service 库存服务 @return this */
        public Builder inventory(InventoryService service) {
            this.inventory = service;
            return this;
        }

        /** @param evaluator 优惠券求值器 @return this */
        public Builder couponEvaluator(CouponEvaluator evaluator) {
            this.couponEvaluator = evaluator;
            return this;
        }

        /** @param interpreter 优惠券解释器（提供按券对象求值与 AST 说明的能力） @return this */
        public Builder couponInterpreter(
                com.example.delivery.infrastructure.coupon.CouponRuleInterpreter interpreter) {
            this.couponInterpreter = interpreter;
            this.couponEvaluator = interpreter;
            return this;
        }

        /** @param context 计价上下文 @return this */
        public Builder pricing(PricingContext context) {
            this.pricing = context;
            return this;
        }

        /** @param eventPublisher 事件发布器 @return this */
        public Builder publisher(DomainEventPublisher eventPublisher) {
            this.publisher = eventPublisher;
            return this;
        }

        /** @param center 派单中心 @return this */
        public Builder dispatchCenter(DispatchCenter center) {
            this.dispatchCenter = center;
            return this;
        }

        /** @param manager 备忘录管理器 @return this */
        public Builder memoManager(OrderMemoManager manager) {
            this.memoManager = manager;
            return this;
        }

        /** @param registry 支付网关注册表 @return this */
        public Builder gatewayRegistry(PaymentGatewayRegistry registry) {
            this.gatewayRegistry = registry;
            return this;
        }

        /** @param coupon 注册一张优惠券 @return this */
        public Builder withCoupon(Coupon coupon) {
            this.coupons.put(coupon.couponId(), coupon);
            return this;
        }

        /**
         * 组装门面。
         *
         * @return 校验通过的门面实例
         * @throws IllegalStateException 必填协作者缺失时抛出
         */
        public OrderFacade build() {
            if (orderStore == null || userStore == null || merchantStore == null || riderStore == null) {
                throw new IllegalStateException("订单/用户/商家/骑手仓储都是必填项");
            }
            if (couponEvaluator == null) {
                throw new IllegalStateException("优惠券求值器（couponEvaluator）是必填项");
            }
            if (dispatchCenter == null) {
                dispatchCenter = new DispatchCenter(orderStore, riderStore, publisher);
            }
            if (paymentLedger == null) {
                // 未提供流水仓储时退化为内存实现，保证门面可独立运行
                paymentLedger = new OrderStore.PaymentLedger() {
                    private final List<Payment> payments = new java.util.ArrayList<>();

                    @Override
                    public void record(Payment payment) {
                        payments.add(payment);
                    }

                    @Override
                    public List<Payment> findByOrder(String orderId) {
                        return payments.stream().filter(p -> p.orderId().equals(orderId)).toList();
                    }

                    @Override
                    public List<Payment> findAll() {
                        return List.copyOf(payments);
                    }
                };
            }
            return new OrderFacade(this);
        }
    }
}
