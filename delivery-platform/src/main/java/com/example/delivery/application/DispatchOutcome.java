package com.example.delivery.application;

import java.util.List;

/**
 * 派单结果值对象。
 *
 * <p><b>设计模式关联：中介者模式（Mediator）的返回契约</b></p>
 *
 * <p><b>为什么要专门定义结果对象，而不是返回 {@code boolean}：</b>
 * 派单失败的原因对业务处理方式影响很大——</p>
 * <ul>
 *   <li>「无可用骑手」→ 应该进重试队列，稍后再派；</li>
 *   <li>「订单状态不对」→ 应该提示用户，重试没有意义；</li>
 *   <li>「使用了某策略但候选为空」→ 应该考虑放宽策略（例如允许跨区调度）。</li>
 * </ul>
 * <p>只用布尔会把这三类情况压扁成同一个 {@code false}，上层无法区分。</p>
 *
 * @param success    是否派单成功
 * @param orderId    订单号
 * @param riderId    承接骑手编号（失败为 null）
 * @param riderName  承接骑手姓名（失败为 null）
 * @param strategy   本次使用的调度策略名
 * @param attempts   尝试过的策略数量（1 表示首选策略即成功）
 * @param trace      派单过程轨迹（候选骑手、筛选理由等），用于控制台演示
 * @param message    结果说明（成功或失败原因）
 * @author 课程大作业
 */
public record DispatchOutcome(
        boolean success,
        String orderId,
        String riderId,
        String riderName,
        String strategy,
        int attempts,
        List<String> trace,
        String message) {

    /** 紧凑构造器：轨迹列表复制为不可变，避免外部改动历史。 */
    public DispatchOutcome {
        trace = trace == null ? List.of() : List.copyOf(trace);
    }

    /** @return 是否失败 */
    public boolean failed() {
        return !success;
    }

    /** @return 人类可读摘要 */
    public String describe() {
        return success
                ? String.format("派单成功：骑手 %s(%s)，策略=%s，轨迹 %d 条：%s",
                        riderName, riderId, strategy, trace.size(), message)
                : String.format("派单失败：策略=%s，原因=%s", strategy, message);
    }
}
