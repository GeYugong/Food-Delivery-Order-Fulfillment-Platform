package com.example.delivery.application;

import com.example.delivery.domain.order.IllegalOrderStateException;
import com.example.delivery.domain.order.Order;
import com.example.delivery.domain.order.OrderStatus;
import com.example.delivery.domain.rider.Rider;
import com.example.delivery.domain.support.DispatchStrategies;
import com.example.delivery.domain.support.DispatchStrategy;
import com.example.delivery.domain.support.DomainEventPublisher;
import com.example.delivery.domain.support.OrderEvent;
import com.example.delivery.domain.support.OrderStore;

import java.util.ArrayList;
import java.util.List;

/**
 * 派单中心（中介者模式 Mediator 的「具体中介者」角色）。
 *
 * <p><b>设计模式：中介者模式（Mediator）+ 策略模式（Strategy）+ 命令模式（Receiver）</b></p>
 *
 * <p><b>为什么需要中介者：</b>派单涉及订单、商家、骑手三方。</p>
 * <ul>
 *   <li>不用中介者：订单要认识骑手池、骑手要知道订单、商家要通知骑手，
 *       两两耦合需要 3 条双向链路；每加一种调度规则，三方都要改；</li>
 *   <li>用中介者：三方都只与「派单中心」通信（星形拓扑），
 *       订单只发布「我已支付」，骑手只报告「我空闲」，谁被选中由中心决定。</li>
 * </ul>
 *
 * <p><b>中心自身的职责要克制：</b>它只做三件事——</p>
 * <ol>
 *   <li>判断订单是否可以派（前置校验）；</li>
 *   <li>挑一个策略去筛选骑手（把「怎么挑」委托给策略，自己不写排序逻辑）；</li>
 *   <li>促成订单与骑手绑定，并发布事件通知下游（观察者）。</li>
 * </ol>
 *
 * <p><b>多策略回退机制：</b>首选策略可能选不出人（例如所有骑手都超载），
 * 此时按 {@link #fallbackStrategies} 依次回退尝试，并记录完整轨迹，
 * 这是真实派单系统的常见做法（策略降级）。</p>
 *
 * @author 课程大作业
 */
public class DispatchCenter {

    /** 订单仓储。 */
    private final OrderStore orderStore;

    /** 骑手仓储。 */
    private final OrderStore.RiderStore riderStore;

    /** 事件发布器。 */
    private final DomainEventPublisher publisher;

    /** 首选策略。 */
    private DispatchStrategy preferred = DispatchStrategies.nearest();

    /** 回退策略链（首选策略失败后依序尝试）。 */
    private final List<DispatchStrategy> fallbackStrategies = new ArrayList<>(
            List.of(DispatchStrategies.leastLoaded(), DispatchStrategies.highestRated()));

    /** 最近的派单轨迹（供控制台打印）。 */
    private final List<String> lastTrace = new ArrayList<>();

    /**
     * 构造派单中心。
     *
     * @param orderStore 订单仓储
     * @param riderStore 骑手仓储
     * @param publisher  事件发布器
     */
    public DispatchCenter(OrderStore orderStore, OrderStore.RiderStore riderStore,
                          DomainEventPublisher publisher) {
        this.orderStore = orderStore;
        this.riderStore = riderStore;
        this.publisher = publisher;
    }

    /**
     * 切换首选调度策略（策略模式的核心能力：运行期可换）。
     *
     * @param strategy 新策略
     * @return this
     */
    public DispatchCenter useStrategy(DispatchStrategy strategy) {
        if (strategy == null) {
            throw new IllegalArgumentException("调度策略不能为空");
        }
        this.preferred = strategy;
        return this;
    }

    /** @return 当前首选策略 */
    public DispatchStrategy preferredStrategy() {
        return preferred;
    }

    /** @return 可用策略列表（用于控制台展示） */
    public List<DispatchStrategy> availableStrategies() {
        List<DispatchStrategy> all = new ArrayList<>();
        all.add(preferred);
        all.addAll(fallbackStrategies);
        return all;
    }

    /**
     * 为指定订单派单。
     *
     * @param orderId 订单号
     * @return 派单结果
     */
    public DispatchOutcome dispatch(String orderId) {
        lastTrace.clear();
        Order order = orderStore.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("订单不存在：" + orderId));

        // ① 前置校验：只有「已支付」状态才允许派单
        if (order.status() != OrderStatus.PAID) {
            String message = String.format("订单当前状态为[%s]，只有[已支付]的订单可以派单",
                    order.status().label());
            trace(message);
            return new DispatchOutcome(false, orderId, null, null, preferred.name(), 0, lastTrace, message);
        }

        // ② 收集候选骑手（中介者掌握全局信息，这是它相对「两两直连」的最大优势）
        List<Rider> candidates = riderStore.findAvailable();
        trace(String.format("候选骑手 %d 名：%s", candidates.size(),
                candidates.isEmpty() ? "无" : candidates.stream().map(Rider::describe).toList()));
        if (candidates.isEmpty()) {
            String message = "当前没有可接单的骑手（全部离线/休息/满载）";
            trace(message);
            publisher.publishFor(order, OrderEvent.ORDER_DISPATCH_FAILED, message);
            return new DispatchOutcome(false, orderId, null, null, preferred.name(), 0, lastTrace, message);
        }

        // ③ 依策略链挑选（首选 → 回退），并记录每一步的决策
        List<DispatchStrategy> chain = new ArrayList<>();
        chain.add(preferred);
        chain.addAll(fallbackStrategies);
        int attempts = 0;
        for (DispatchStrategy strategy : chain) {
            attempts++;
            Rider picked = strategy.pick(candidates, order);
            if (picked == null) {
                trace(String.format("策略[%s]未选出骑手，回退下一个策略", strategy.name()));
                continue;
            }
            trace(String.format("策略[%s]（%s）选中骑手 %s", strategy.name(), strategy.rule(), picked.describe()));
            return bind(order, picked, strategy.name(), attempts);
        }

        String message = "所有调度策略都未能选出骑手";
        trace(message);
        publisher.publishFor(order, OrderEvent.ORDER_DISPATCH_FAILED, message);
        return new DispatchOutcome(false, orderId, null, null, preferred.name(), attempts, lastTrace, message);
    }

    /**
     * 显式指派骑手（演示与运营干预用）。
     *
     * @param orderId 订单号
     * @param riderId 骑手编号
     * @return 派单结果
     */
    public DispatchOutcome assign(String orderId, String riderId) {
        lastTrace.clear();
        Order order = orderStore.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("订单不存在：" + orderId));
        if (order.status() != OrderStatus.PAID) {
            String message = String.format("订单当前状态为[%s]，只有[已支付]的订单可以派单",
                    order.status().label());
            trace(message);
            return new DispatchOutcome(false, orderId, null, null, "人工指派", 1, lastTrace, message);
        }
        Rider rider = riderStore.findById(riderId)
                .orElseThrow(() -> new IllegalArgumentException("骑手不存在：" + riderId));
        if (!rider.canAccept()) {
            String message = String.format("骑手[%s]当前不可接单（状态=%s，负载=%s）",
                    rider.name(), rider.status().label(), rider.loadText());
            trace(message);
            return new DispatchOutcome(false, orderId, null, null, "人工指派", 1, lastTrace, message);
        }
        trace("人工指派骑手：" + rider.describe());
        return bind(order, rider, "人工指派", 1);
    }

    /**
     * 完成绑定：更新订单、更新骑手负载、发布事件。
     *
     * @param order      订单
     * @param rider      骑手
     * @param strategy   策略名
     * @param attempts   尝试次数
     * @return 派单结果
     */
    private DispatchOutcome bind(Order order, Rider rider, String strategy, int attempts) {
        try {
            // 订单侧：已支付 → 已接单
            order.assignRider(rider);
            orderStore.save(order);
        } catch (IllegalOrderStateException e) {
            trace("订单状态迁移失败：" + e.getMessage());
            return new DispatchOutcome(false, order.orderId(), null, null, strategy, attempts,
                    lastTrace, e.getMessage());
        }

        // 骑手侧：接单后负载 +1、状态变为派送中（record 覆盖式更新）
        riderStore.save(rider.acceptOrder());
        trace(String.format("骑手[%s]负载更新：%s → %s", rider.name(), rider.loadText(),
                riderStore.findById(rider.riderId()).orElseThrow().loadText()));

        String message = String.format("骑手 %s 已接单（策略=%s，第 %d 次尝试）",
                rider.name(), strategy, attempts);
        // 观察者：通知商家、骑手端、用户、报表
        publisher.publishFor(order, OrderEvent.ORDER_DISPATCHED, message);
        return new DispatchOutcome(true, order.orderId(), rider.riderId(), rider.name(),
                strategy, attempts, lastTrace, message);
    }

    /**
     * 骑手送达后释放负载（由应用服务在订单完成时调用）。
     *
     * @param riderId 骑手编号
     * @return 释放后的骑手描述
     */
    public String releaseRider(String riderId) {
        Rider rider = riderStore.findById(riderId)
                .orElseThrow(() -> new IllegalArgumentException("骑手不存在：" + riderId));
        Rider freed = rider.finishOrder();
        riderStore.save(freed);
        return freed.describe();
    }

    /** @return 最近一次派单的轨迹 */
    public List<String> lastTrace() {
        return List.copyOf(lastTrace);
    }

    /**
     * 记录轨迹（内部工具）。
     *
     * @param line 轨迹行
     */
    private void trace(String line) {
        lastTrace.add(line);
    }
}
