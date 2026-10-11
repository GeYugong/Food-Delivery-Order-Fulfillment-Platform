package com.example.delivery.domain.order;

import com.example.delivery.domain.merchant.Merchant;
import com.example.delivery.domain.user.User;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 订单聚合根（Aggregate Root）——整个履约平台的核心领域对象。
 *
 * <p><b>本类涉及的设计模式（在类内部落地，具体模式实现见后续阶段）：</b></p>
 * <ol>
 *   <li><b>建造者模式（Builder）</b>：订单字段多（用户、商家、明细、金额、支付、骑手、地址…），
 *       直接写构造器会造成「参数列表地狱」，因此提供 {@link OrderBuilder} 逐步组装，
 *       并在 {@code build()} 时统一做不变量校验（参见 {@link #validateInvariants()}）。</li>
 *   <li><b>状态模式（State）</b>：所有状态变更都必须走 {@link #transitionTo(OrderStatus, String)}，
 *       由 {@link OrderStatus#canTransferTo(OrderStatus)} 做矩阵校验，
 *       聚合自身绝不允许被外部随意 set 状态。</li>
 *   <li><b>原型模式（Prototype）</b>：提供 {@link #copyAsDraft(String)}，支持「再来一单」
 *       基于已有订单快速派生新草稿订单。</li>
 *   <li><b>备忘录模式（Memento）</b>：{@link #snapshot()} 生成状态快照，
 *       配合 {@link #restore(OrderSnapshot)} 支持撤销（如用户误取消后回滚、演示订单存档）。</li>
 *   <li><b>观察者模式（Observer）</b>：状态变化时通过 {@link #transitionTo} 返回事件名，
 *       由应用层发布领域事件（下单/支付/派单/送达），通知商家、骑手、用户、报表多个订阅者。</li>
 *   <li><b>访问者模式（Visitor）</b>：{@link #accept(OrderVisitor)} 提供双分派入口，
 *       让「统计报表」「数据导出」「审计」等新功能无需修改订单类即可扩展。</li>
 *   <li><b>组合模式（Composite）</b>：订单明细是「菜品组合」的叶子集合，
 *       支持套餐（组合节点）与单品（叶子）统一按 {@link OrderItem} 计价。</li>
 * </ol>
 *
 * <p><b>为什么金额字段在订单里而不是散落各处：</b>外卖订单一旦提交，
 * 价格必须可复现、可审计（对账、退款、报表）。因此本类把
 * 小计 {@code subtotal}、优惠 {@code discountAmount}、配送费 {@code deliveryFee}、实付 {@code totalAmount}
 * 全部收敛为订单自身的派生属性，任何一处改动都只能通过本类的方法完成。</p>
 *
 * @author 课程大作业
 */
public class Order {

    // ======================= 一、不可变标识与参与方 =======================

    /** 订单号，全局唯一，创建后不可变。 */
    private final String orderId;

    /** 下单用户。 */
    private final User customer;

    /** 出餐商家。 */
    private final Merchant merchant;

    /** 下单时刻。 */
    private final Instant createdAt;

    // ======================= 二、可变业务状态 =======================

    /** 订单明细（菜品行），只允许通过 {@link #addItem(OrderItem)} / {@link #removeItem(String)} 修改。 */
    private final List<OrderItem> items = new ArrayList<>();

    /** 当前状态，只允许通过 {@link #transitionTo(OrderStatus, String)} 修改。 */
    private OrderStatus status = OrderStatus.DRAFT;

    /** 使用的优惠券编号（可为空，表示未使用优惠券）。 */
    private String couponId;

    /** 优惠券抵扣金额（由解释器模式计算得出）。 */
    private BigDecimal discountAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    /** 配送费（由计价策略模式计算得出）。 */
    private BigDecimal deliveryFee = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    /** 支付方式标识（由支付适配器模式提供，例如 {@code "WALLET"}、{@code "WECHAT_PAY"}）。 */
    private String paymentMethod;

    /** 第三方支付流水号（由支付适配器返回，可为空）。 */
    private String paymentTradeNo;

    /** 收货地址（纯文本快照，避免用户改地址影响历史订单）。 */
    private String deliveryAddress;

    /** 用户备注，例如「不要香菜」。 */
    private String remark;

    /** 派单成功后接单的骑手。 */
    private com.example.delivery.domain.rider.Rider rider;

    // ======================= 三、时间戳 =======================

    /** 提交订单（进入待支付）时刻。 */
    private Instant submittedAt;

    /** 支付成功时刻。 */
    private Instant paidAt;

    /** 骑手接单时刻。 */
    private Instant assignedAt;

    /** 送达完成时刻。 */
    private Instant completedAt;

    /** 取消时刻。 */
    private Instant cancelledAt;

    /** 取消原因。 */
    private String cancelReason;

    /**
     * 唯一公开构造器：仅供 {@link OrderBuilder#build()} 使用，
     * 强制外部通过建造者创建订单，避免参数顺序错乱。
     *
     * @param orderId   订单号
     * @param customer  下单用户
     * @param merchant  商家
     * @param items     初始明细（可为空，草稿态之后再添加）
     * @param createdAt 创建时刻
     */
    private Order(String orderId, User customer, Merchant merchant, List<OrderItem> items, Instant createdAt) {
        this.orderId = Objects.requireNonNull(orderId, "订单号不能为空");
        this.customer = Objects.requireNonNull(customer, "下单用户不能为空");
        this.merchant = Objects.requireNonNull(merchant, "商家不能为空");
        this.createdAt = Objects.requireNonNull(createdAt, "创建时间不能为空");
        if (items != null) {
            items.forEach(this::addItem);
        }
    }

    // ======================= 四、静态工厂：创建入口 =======================

    /**
     * 创建一张全新的草稿订单（订单号自动生成）。
     *
     * <p><b>设计模式：工厂方法思想（静态工厂）</b>——把「订单号怎么生成、初始状态是什么」
     * 这类创建细节封装在聚合内部，调用方只表达业务意图「我要下一个单」。</p>
     *
     * @param customer 下单用户
     * @param merchant 商家
     * @return 处于 {@link OrderStatus#DRAFT} 状态的新订单
     */
    public static Order createDraft(User customer, Merchant merchant) {
        return new OrderBuilder()
                .customer(customer)
                .merchant(merchant)
                .build();
    }

    /**
     * 从持久化数据重建订单（供仓储实现使用）。
     *
     * @param orderId   订单号
     * @param customer  下单用户
     * @param merchant  商家
     * @param createdAt 创建时刻
     * @return 空明细、草稿态的订单骨架
     */
    public static Order reconstruct(String orderId, User customer, Merchant merchant, Instant createdAt) {
        return new Order(orderId, customer, merchant, List.of(), createdAt);
    }

    // ======================= 五、明细操作 =======================

    /**
     * 添加菜品明细；同一菜品的多行会被自动合并为一行（数量累加），
     * 这是外卖场景的真实语义（用户多次点击「+」不应该出现两行同名菜）。
     *
     * @param item 明细行，不可为 null
     * @throws IllegalOrderStateException 当前状态不允许加菜时抛出
     */
    public void addItem(OrderItem item) {
        Objects.requireNonNull(item, "明细行不能为空");
        if (!status.canAddItems()) {
            throw new IllegalOrderStateException(orderId, status, status,
                    "订单已提交，不能再修改菜品明细");
        }
        for (int i = 0; i < items.size(); i++) {
            OrderItem exist = items.get(i);
            if (exist.sameDishAs(item)) {
                items.set(i, exist.withExtraQuantity(item.quantity()));
                return;
            }
        }
        items.add(item);
    }

    /**
     * 移除某个菜品的全部明细行。
     *
     * @param dishId 菜品标识
     * @return 是否真的移除了
     * @throws IllegalOrderStateException 当前状态不允许修改明细时抛出
     */
    public boolean removeItem(String dishId) {
        if (!status.canAddItems()) {
            throw new IllegalOrderStateException(orderId, status, status, "订单已提交，不能再修改菜品明细");
        }
        return items.removeIf(line -> line.dishId().equals(dishId));
    }

    /**
     * 计算菜品小计（Σ 明细行小计）。
     *
     * @return 小计金额，保留 2 位小数
     */
    public BigDecimal subtotal() {
        return items.stream()
                .map(OrderItem::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 计算实付金额 = 小计 − 优惠 + 配送费。
     *
     * @return 实付金额，保留 2 位小数；不会小于 0
     */
    public BigDecimal totalAmount() {
        BigDecimal total = subtotal().subtract(discountAmount).add(deliveryFee);
        return total.signum() < 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : total.setScale(2, RoundingMode.HALF_UP);
    }

    // ======================= 六、优惠与费用 =======================

    /**
     * 应用优惠券抵扣结果。
     *
     * <p>抵扣金额由「解释器模式」把优惠券规则字符串（如 {@code "满30减5"}）解释后算出，
     * 订单只负责接收结论，不关心规则语法。</p>
     *
     * @param couponId 优惠券编号
     * @param discount 抵扣金额，不可为负
     * @throws IllegalArgumentException 抵扣金额为负或超过小计时抛出
     */
    public void applyCoupon(String couponId, BigDecimal discount) {
        if (discount == null || discount.signum() < 0) {
            throw new IllegalArgumentException("优惠金额不能为负数");
        }
        if (discount.compareTo(subtotal()) > 0) {
            throw new IllegalArgumentException("优惠金额不能超过菜品小计：" + subtotal().toPlainString());
        }
        this.couponId = couponId;
        this.discountAmount = discount.setScale(2, RoundingMode.HALF_UP);
    }

    /** @return 使用的优惠券编号（可能为空） */
    public Optional<String> couponId() {
        return Optional.ofNullable(couponId);
    }

    /** @return 优惠抵扣金额 */
    public BigDecimal discountAmount() {
        return discountAmount;
    }

    /** @return 配送费 */
    public BigDecimal deliveryFee() {
        return deliveryFee;
    }

    /**
     * 设置配送费。
     *
     * @param deliveryFee 配送费，不可为负
     */
    public void setDeliveryFee(BigDecimal deliveryFee) {
        if (deliveryFee == null || deliveryFee.signum() < 0) {
            throw new IllegalArgumentException("配送费不能为空且不能为负数");
        }
        this.deliveryFee = deliveryFee.setScale(2, RoundingMode.HALF_UP);
    }

    // ======================= 七、状态流转 =======================

    /**
     * 提交订单：草稿 → 待支付。
     *
     * @throws OrderValidationException   订单内容不合法（无明细）
     * @throws IllegalOrderStateException 当前状态不允许提交
     */
    public void submit() {
        if (items.isEmpty()) {
            throw new OrderValidationException("订单完整性校验", "订单没有任何菜品，无法提交");
        }
        transitionTo(OrderStatus.PENDING_PAYMENT, "用户提交订单");
        this.submittedAt = Instant.now();
    }

    /**
     * 记录支付成功：待支付 → 已支付。
     *
     * @param paymentMethod 支付方式标识
     * @param tradeNo       支付流水号
     * @throws IllegalOrderStateException 非待支付状态时抛出
     */
    public void markPaid(String paymentMethod, String tradeNo) {
        transitionTo(OrderStatus.PAID, "支付成功");
        this.paymentMethod = paymentMethod;
        this.paymentTradeNo = tradeNo;
        this.paidAt = Instant.now();
    }

    /**
     * 绑定支付方式（尚未支付，仅记录用户选择）。
     *
     * @param paymentMethod 支付方式标识，例如 {@code "WALLET"}
     * @throws IllegalOrderStateException 非草稿/待支付状态时抛出
     */
    public void bindPaymentMethod(String paymentMethod) {
        if (status != OrderStatus.DRAFT && status != OrderStatus.PENDING_PAYMENT) {
            throw new IllegalOrderStateException(orderId, status, status, "订单已支付，不能再修改支付方式");
        }
        this.paymentMethod = Objects.requireNonNull(paymentMethod, "支付方式不能为空");
    }

    /**
     * 派单成功，骑手接单：已支付 → 已接单。
     *
     * @param rider 接单骑手
     * @throws IllegalOrderStateException 非已支付状态时抛出
     */
    public void assignRider(com.example.delivery.domain.rider.Rider rider) {
        Objects.requireNonNull(rider, "骑手不能为空");
        transitionTo(OrderStatus.ASSIGNED, "骑手接单：" + rider.name());
        this.rider = rider;
        this.assignedAt = Instant.now();
    }

    /**
     * 骑手取餐：已接单 → 配送中。
     *
     * @throws IllegalOrderStateException 状态不对时抛出
     */
    public void startDelivering() {
        transitionTo(OrderStatus.DELIVERING, "骑手已取餐");
    }

    /**
     * 送达完成：配送中 → 已完成。
     *
     * @throws IllegalOrderStateException 状态不对时抛出
     */
    public void complete() {
        transitionTo(OrderStatus.COMPLETED, "订单送达");
        this.completedAt = Instant.now();
    }

    /**
     * 取消订单。
     *
     * <p>注意：配送中的订单在业务上不允许取消（骑手已在路上），
     * 该规则由 {@link OrderStatus} 的迁移矩阵统一保证。</p>
     *
     * @param reason 取消原因
     * @throws IllegalOrderStateException 当前状态不可取消时抛出
     */
    public void cancel(String reason) {
        if (!status.cancellable()) {
            throw new IllegalOrderStateException(orderId, status, OrderStatus.CANCELLED,
                    "当前状态不可取消");
        }
        transitionTo(OrderStatus.CANCELLED, reason);
        this.cancelReason = reason;
        this.cancelledAt = Instant.now();
    }

    /**
     * 状态流转的唯一入口：所有状态变更都必须经过这里。
     *
     * <p><b>设计模式：状态模式（State）的守卫（Guard）+ 观察者模式（Observer）的事件源</b>。
     * 校验通过后返回「领域事件名」，由应用层负责发布给观察者。</p>
     *
     * @param target  目标状态
     * @param reason  流转原因（写入日志与事件负载）
     * @return 领域事件名，例如 {@code "OrderPaid"}
     * @throws IllegalOrderStateException 迁移矩阵不允许时抛出
     */
    public String transitionTo(OrderStatus target, String reason) {
        if (!status.canTransferTo(target)) {
            throw new IllegalOrderStateException(orderId, status, target,
                    "允许的目标状态为 " + status.nextStatuses());
        }
        OrderStatus from = this.status;
        this.status = target;
        return "Order" + capitalize(target.name()) + "Event[" + from.label() + " -> " + target.label()
                + ", 原因=" + (reason == null ? "未说明" : reason) + "]";
    }

    // ======================= 八、快照（备忘录模式）与复制（原型模式） =======================

    /**
     * 生成订单状态快照。
     *
     * <p><b>设计模式：备忘录模式（Memento）</b>——把订单内部状态整体封存为一个不可变对象，
     * 后续可原样恢复，且不需要把 Order 的私有字段暴露给外部。</p>
     *
     * <p><b>注意：</b>快照覆盖订单<b>全部可变状态</b>（金额、渠道、时间戳、取消原因等）。
     * 若只想快照业务子集，回滚就会出现「状态回去了、痕迹没回去」的半吊子结果。</p>
     *
     * @return 快照对象
     */
    public OrderSnapshot snapshot() {
        return new OrderSnapshot(orderId, status, List.copyOf(items), couponId, discountAmount,
                deliveryFee, paymentMethod, paymentTradeNo,
                rider == null ? null : rider.riderId(), rider,
                deliveryAddress, remark, submittedAt, paidAt, assignedAt, completedAt,
                cancelledAt, cancelReason);
    }

    /**
     * 从快照恢复订单状态（撤销、回滚、演示存档校验）。
     *
     * @param snapshot 之前保存的快照
     * @throws IllegalArgumentException 快照与当前订单不是同一张订单时抛出
     */
    public void restore(OrderSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "快照不能为空");
        if (!snapshot.orderId().equals(this.orderId)) {
            throw new IllegalArgumentException("快照不匹配：快照订单号=" + snapshot.orderId()
                    + "，当前订单号=" + this.orderId);
        }
        this.items.clear();
        this.items.addAll(snapshot.items());
        this.status = snapshot.status();
        this.couponId = snapshot.couponId();
        this.discountAmount = snapshot.discountAmount();
        this.deliveryFee = snapshot.deliveryFee();
        this.paymentMethod = snapshot.paymentMethod();
        this.paymentTradeNo = snapshot.paymentTradeNo();
        this.deliveryAddress = snapshot.deliveryAddress();
        this.remark = snapshot.remark();
        // 时间戳与取消痕迹必须一并回滚，否则「撤销」是不完整的
        this.submittedAt = snapshot.submittedAt();
        this.paidAt = snapshot.paidAt();
        this.assignedAt = snapshot.assignedAt();
        this.completedAt = snapshot.completedAt();
        this.cancelledAt = snapshot.cancelledAt();
        this.cancelReason = snapshot.cancelReason();
        // 快照构造器保证「有编号必有骑手对象」，这里直接还原即可
        this.rider = snapshot.rider();
    }

    /**
     * 「再来一单」：基于当前订单复制出一张新的草稿订单（订单号重新生成）。
     *
     * <p><b>设计模式：原型模式（Prototype）</b>——复用已有订单的菜品明细作为基础，
     * 但清空支付、骑手、优惠等与历史订单绑定的信息，符合真实业务语义。</p>
     *
     * @param newOrderId 新订单号
     * @return 全新的草稿订单
     */
    public Order copyAsDraft(String newOrderId) {
        return new Order(newOrderId, customer, merchant, new ArrayList<>(items), Instant.now());
    }

    // ======================= 九、访问者模式入口 =======================

    /**
     * 接受访问者（双分派）。
     *
     * <p><b>设计模式：访问者模式（Visitor）</b>——统计报表、数据导出、风控审计等功能
     * 后续只需新增一个 Visitor 实现，无需改动 Order 类，符合开闭原则。</p>
     *
     * @param visitor 访问者
     * @param <R>     访问结果类型
     * @return 访问结果
     */
    public <R> R accept(OrderVisitor<R> visitor) {
        return visitor.visit(this);
    }

    // ======================= 十、不变量校验 =======================

    /**
     * 订单不变量校验：由 {@link OrderBuilder#build()} 调用。
     *
     * @throws OrderValidationException 订单不合法时抛出
     */
    private void validateInvariants() {
        if (items.isEmpty()) {
            // 草稿态允许空订单（用户先下单后加菜），提交时才强校验
            return;
        }
        boolean sameMerchant = items.stream()
                .map(OrderItem::dishId)
                .allMatch(dishId -> merchant.sells(dishId));
        if (!sameMerchant) {
            throw new OrderValidationException("商家归属校验",
                    "订单中存在不属于商家[" + merchant.name() + "]的菜品");
        }
    }

    // ======================= 十一、Getter（只读视图） =======================

    /** @return 订单号 */
    public String orderId() {
        return orderId;
    }

    /** @return 下单用户 */
    public User customer() {
        return customer;
    }

    /** @return 商家 */
    public Merchant merchant() {
        return merchant;
    }

    /** @return 只读的明细列表 */
    public List<OrderItem> items() {
        return Collections.unmodifiableList(items);
    }

    /** @return 菜品总份数（Σ 数量） */
    public int totalQuantity() {
        return items.stream().mapToInt(OrderItem::quantity).sum();
    }

    /** @return 当前状态 */
    public OrderStatus status() {
        return status;
    }

    /** @return 下单时刻 */
    public Instant createdAt() {
        return createdAt;
    }

    /** @return 提交时刻（可能为空） */
    public Optional<Instant> submittedAt() {
        return Optional.ofNullable(submittedAt);
    }

    /** @return 支付时刻（可能为空） */
    public Optional<Instant> paidAt() {
        return Optional.ofNullable(paidAt);
    }

    /** @return 接单时刻（可能为空） */
    public Optional<Instant> assignedAt() {
        return Optional.ofNullable(assignedAt);
    }

    /** @return 完成时刻（可能为空） */
    public Optional<Instant> completedAt() {
        return Optional.ofNullable(completedAt);
    }

    /** @return 取消时刻（可能为空） */
    public Optional<Instant> cancelledAt() {
        return Optional.ofNullable(cancelledAt);
    }

    /** @return 取消原因（可能为空） */
    public Optional<String> cancelReason() {
        return Optional.ofNullable(cancelReason);
    }

    /** @return 骑手（可能为空，未派单时） */
    public Optional<com.example.delivery.domain.rider.Rider> rider() {
        return Optional.ofNullable(rider);
    }

    /** @return 支付方式标识（可能为空） */
    public Optional<String> paymentMethod() {
        return Optional.ofNullable(paymentMethod);
    }

    /** @return 支付流水号（可能为空） */
    public Optional<String> paymentTradeNo() {
        return Optional.ofNullable(paymentTradeNo);
    }

    /** @return 收货地址（可能为空） */
    public Optional<String> deliveryAddress() {
        return Optional.ofNullable(deliveryAddress);
    }

    /** @return 用户备注（可能为空） */
    public Optional<String> remark() {
        return Optional.ofNullable(remark);
    }

    // ======================= 十二、equals / hashCode / toString =======================

    /** 聚合根以订单号判定相等（同一订单的两次加载视为同一对象）。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Order order)) {
            return false;
        }
        return orderId.equals(order.orderId);
    }

    @Override
    public int hashCode() {
        return orderId.hashCode();
    }

    @Override
    public String toString() {
        return String.format("Order{%s, 状态=%s, 菜品=%d份, 小计=%s, 优惠=%s, 配送费=%s, 实付=%s, 骑手=%s}",
                orderId, status.label(), totalQuantity(),
                subtotal().toPlainString(), discountAmount.toPlainString(),
                deliveryFee.toPlainString(), totalAmount().toPlainString(),
                rider == null ? "未派单" : rider.name());
    }

    /** 工具方法：首字母大写。 */
    private static String capitalize(String name) {
        return name.charAt(0) + name.substring(1).toLowerCase();
    }

    /**
     * 订单建造者。
     *
     * <p><b>设计模式：建造者模式（Builder）</b></p>
     * <p><b>为什么用：</b>创建一张订单需要用户、商家、地址、备注、优惠券、配送费等多达八九个可选/必填项，
     * 用建造者可以把「组装过程」与「最终校验」分开：链式调用负责收集，{@code build()} 负责把关。
     * 与 Lombok 不同，这里手写建造者便于在课程报告中逐行讲解。</p>
     */
    public static final class OrderBuilder {

        private String orderId;
        private User customer;
        private Merchant merchant;
        private final List<OrderItem> items = new ArrayList<>();
        private Instant createdAt;
        private String deliveryAddress;
        private String remark;
        private BigDecimal deliveryFee;

        /** @return 新建一个建造者 */
        public static OrderBuilder newBuilder() {
            return new OrderBuilder();
        }

        /** @param orderId 指定订单号（不指定则自动生成） @return this */
        public OrderBuilder orderId(String orderId) {
            this.orderId = orderId;
            return this;
        }

        /** @param customer 下单用户（必填） @return this */
        public OrderBuilder customer(User customer) {
            this.customer = customer;
            return this;
        }

        /** @param merchant 商家（必填） @return this */
        public OrderBuilder merchant(Merchant merchant) {
            this.merchant = merchant;
            return this;
        }

        /** @param item 追加一行明细 @return this */
        public OrderBuilder addItem(OrderItem item) {
            this.items.add(Objects.requireNonNull(item, "明细行不能为空"));
            return this;
        }

        /** @param deliveryAddress 收货地址 @return this */
        public OrderBuilder deliveryAddress(String deliveryAddress) {
            this.deliveryAddress = deliveryAddress;
            return this;
        }

        /** @param remark 用户备注 @return this */
        public OrderBuilder remark(String remark) {
            this.remark = remark;
            return this;
        }

        /** @param deliveryFee 配送费 @return this */
        public OrderBuilder deliveryFee(BigDecimal deliveryFee) {
            this.deliveryFee = deliveryFee;
            return this;
        }

        /** @param createdAt 指定创建时刻（重建历史订单时使用） @return this */
        public OrderBuilder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        /**
         * 组装并校验订单。
         *
         * @return 校验通过的订单
         * @throws NullPointerException     必填项缺失
         * @throws OrderValidationException 不变量校验失败
         */
        public Order build() {
            Objects.requireNonNull(customer, "下单用户（customer）是必填项");
            Objects.requireNonNull(merchant, "商家（merchant）是必填项");
            String finalId = (orderId == null || orderId.isBlank())
                    ? "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase()
                    : orderId;
            Instant finalCreatedAt = createdAt == null ? Instant.now() : createdAt;

            Order order = new Order(finalId, customer, merchant, items, finalCreatedAt);
            if (deliveryAddress != null) {
                order.deliveryAddress = deliveryAddress;
            }
            if (remark != null) {
                order.remark = remark;
            }
            if (deliveryFee != null) {
                order.setDeliveryFee(deliveryFee);
            }
            order.validateInvariants();
            return order;
        }
    }
}
