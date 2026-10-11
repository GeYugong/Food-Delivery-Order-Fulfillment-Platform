package com.example.delivery.application;

import com.example.delivery.domain.order.Order;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 订单备忘录管理器（备忘录模式 Memento 的「负责人 / Caretaker」角色）。
 *
 * <p><b>设计模式：备忘录模式（Memento）</b></p>
 *
 * <p><b>职责边界（这是备忘录模式的关键）：</b>负责人只负责<b>保存与归还</b>快照，
 * <b>绝不检查、绝不修改</b>快照内容。发起人（{@link Order}）自己知道如何生成与恢复快照。</p>
 *
 * <p>本管理器在「订单存档」与「误操作回滚」两个场景都会用到：
 * 控制台每次改变订单状态前自动记账一次，用户就可以「撤销上一步」。</p>
 *
 * @author 课程大作业
 */
public class OrderMemoManager {

    /** 单个订单最多保留的历史快照数量（防止内存无限增长）。 */
    private static final int MAX_HISTORY = 20;

    /** 订单号 → 快照栈（栈顶是最新快照）。 */
    private final java.util.Map<String, java.util.Deque<Entry>> history = new java.util.LinkedHashMap<>();

    /**
     * 记录一次快照。
     *
     * @param order  被记录的订单
     * @param reason 记录原因（例如「用户点击支付前」），仅作为标签，不参与业务判断
     * @return 本次快照的编号，便于控制台展示
     */
    public String backup(Order order, String reason) {
        String memoId = "MEMO-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        java.util.Deque<Entry> stack = history.computeIfAbsent(order.orderId(),
                key -> new java.util.ArrayDeque<>());
        stack.push(new Entry(memoId, reason, order.snapshot()));
        while (stack.size() > MAX_HISTORY) {
            stack.removeLast();
        }
        return memoId;
    }

    /**
     * 撤销到上一个快照。
     *
     * @param order 订单
     * @return 撤销说明；没有历史时返回「无可撤销的快照」
     */
    public String undo(Order order) {
        java.util.Deque<Entry> stack = history.get(order.orderId());
        if (stack == null || stack.isEmpty()) {
            return "订单[" + order.orderId() + "] 没有可撤销的快照";
        }
        Entry entry = stack.pop();
        order.restore(entry.snapshot());
        return String.format("已撤销到快照 %s（记录原因：%s），当前状态：%s",
                entry.memoId(), entry.reason(), order.status().label());
    }

    /**
     * 查看某订单的历史快照描述（只读，不暴露快照内部数据）。
     *
     * @param orderId 订单号
     * @return 描述列表，最新的在前
     */
    public List<String> historyOf(String orderId) {
        java.util.Deque<Entry> stack = history.get(orderId);
        if (stack == null) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (Entry entry : stack) {
            lines.add(String.format("%s ← %s（状态=%s）", entry.memoId(), entry.reason(),
                    entry.snapshot().status().label()));
        }
        return Collections.unmodifiableList(lines);
    }

    /**
     * 历史快照数量。
     *
     * @param orderId 订单号
     * @return 数量
     */
    public int historyCount(String orderId) {
        java.util.Deque<Entry> stack = history.get(orderId);
        return stack == null ? 0 : stack.size();
    }

    /** 清空全部历史（测试用）。 */
    public void clear() {
        history.clear();
    }

    /**
     * 快照条目：把「快照 + 标签」配对保存。
     *
     * <p>注意：负责人的职责仅限于保存，它并不知道快照里有什么；
     * 标签（reason）是负责人自己附加的元信息，不属于发起人的状态。</p>
     *
     * @param memoId   快照编号
     * @param reason   记录原因
     * @param snapshot 订单快照
     */
    private record Entry(String memoId, String reason,
                         com.example.delivery.domain.order.OrderSnapshot snapshot) {
    }
}
