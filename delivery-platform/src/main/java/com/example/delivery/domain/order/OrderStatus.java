package com.example.delivery.domain.order;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 订单状态枚举，并内置「状态迁移矩阵」。
 *
 * <p><b>设计模式：状态模式（State）的基础设施</b></p>
 * <p><b>为什么这样用：</b>外卖订单的合法流转是固定且有限的，把「谁能走到谁」
 * 收敛到枚举内部的静态矩阵，可以做到：</p>
 * <ol>
 *   <li>合法性判断只有一处（{@link #canTransferTo(OrderStatus)}），不会散落在各个 Service 里；</li>
 *   <li>后续引入状态模式（每个状态一个类，负责自己的行为与迁移）时，
 *       本枚举天然是各状态类的「上下文数据」，无需改动领域模型；</li>
 *   <li>控制台演示时可以直接打印整张状态图，便于课程答辩展示。</li>
 * </ol>
 *
 * <p>状态流转图（横向看当前状态，纵向看目标状态）：</p>
 * <pre>
 *   待支付 PENDING_PAYMENT ──支付成功──▶ 已支付 PAID ──派单成功──▶ 已接单 ASSIGNED
 *        │                                  │                        │
 *        │取消                               │取消                     │骑手取餐
 *        ▼                                  ▼                        ▼
 *     已取消 CANCELLED                    已取消                  配送中 DELIVERING
 *                                                                     │
 *                                                                     │送达
 *                                                                     ▼
 *                                                              已完成 COMPLETED
 * </pre>
 *
 * @author 课程大作业
 */
public enum OrderStatus {

    /** 草稿：刚创建/正在构建购物车，允许继续加菜，也允许直接提交。 */
    DRAFT("草稿", true, true, false),

    /** 待支付：已提交订单，等待用户完成支付（支付方式可以是余额/微信/支付宝…）。 */
    PENDING_PAYMENT("待支付", false, true, false),

    /** 已支付：钱已到平台，等待派单（派单中介介入）。 */
    PAID("已支付", false, true, false),

    /** 已接单：骑手已接单，即将前往商家取餐。 */
    ASSIGNED("已接单", false, true, false),

    /** 配送中：骑手已取餐，正在送往用户地址。 */
    DELIVERING("配送中", false, false, false),

    /** 已完成：用户已收货，订单终态。 */
    COMPLETED("已完成", false, false, true),

    /** 已取消：订单终态。 */
    CANCELLED("已取消", false, false, true);

    /** 中文显示名，用于控制台输出与报表。 */
    private final String label;

    /** 是否还允许往订单里加菜（只有草稿态允许）。 */
    private final boolean canAddItems;

    /** 是否允许取消（终态不允许）。 */
    private final boolean cancellable;

    /** 是否为终态（终态不可再流转）。 */
    private final boolean finalState;

    /** 状态迁移矩阵：key = 当前状态，value = 允许迁移到的目标状态集合。 */
    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = new EnumMap<>(OrderStatus.class);

    static {
        // 草稿：可以提交进入待支付，也可以直接取消
        TRANSITIONS.put(DRAFT, EnumSet.of(PENDING_PAYMENT, CANCELLED));
        // 待支付：可以支付成功，也可以取消（超时未支付/用户主动取消）
        TRANSITIONS.put(PENDING_PAYMENT, EnumSet.of(PAID, CANCELLED));
        // 已支付：可以派单成功，也可以取消（商家拒单/无骑手可派，走退款流程）
        TRANSITIONS.put(PAID, EnumSet.of(ASSIGNED, CANCELLED));
        // 已接单：骑手可以取餐，也可以取消（骑手异常，需重新派单——由中介者处理）
        TRANSITIONS.put(ASSIGNED, EnumSet.of(DELIVERING, CANCELLED));
        // 配送中：只能送达完成（此时不允许取消，业务上骑手已在路上）
        TRANSITIONS.put(DELIVERING, EnumSet.of(COMPLETED));
        // 终态：没有任何出边
        TRANSITIONS.put(COMPLETED, EnumSet.noneOf(OrderStatus.class));
        TRANSITIONS.put(CANCELLED, EnumSet.noneOf(OrderStatus.class));
    }

    OrderStatus(String label, boolean canAddItems, boolean cancellable, boolean finalState) {
        this.label = label;
        this.canAddItems = canAddItems;
        this.cancellable = cancellable;
        this.finalState = finalState;
    }

    /**
     * 判断当前状态是否可以迁移到目标状态。
     *
     * @param target 目标状态，为 {@code null} 时返回 {@code false}
     * @return 允许迁移返回 {@code true}
     */
    public boolean canTransferTo(OrderStatus target) {
        if (target == null) {
            return false;
        }
        return TRANSITIONS.getOrDefault(this, Collections.emptySet()).contains(target);
    }

    /**
     * 返回当前状态所有合法的下一状态（只读集合）。
     * 供控制台菜单、PlantUML 状态图生成、单元测试遍历校验使用。
     */
    public Set<OrderStatus> nextStatuses() {
        return Collections.unmodifiableSet(TRANSITIONS.getOrDefault(this, Collections.emptySet()));
    }

    /** @return 中文显示名，例如「配送中」 */
    public String label() {
        return label;
    }

    /** @return 是否允许继续加菜 */
    public boolean canAddItems() {
        return canAddItems;
    }

    /** @return 是否允许取消 */
    public boolean cancellable() {
        return cancellable;
    }

    /** @return 是否为终态 */
    public boolean isFinalState() {
        return finalState;
    }
}
