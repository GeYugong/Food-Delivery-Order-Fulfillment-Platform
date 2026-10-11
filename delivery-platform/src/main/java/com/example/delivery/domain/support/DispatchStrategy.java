package com.example.delivery.domain.support;

import com.example.delivery.domain.order.Order;
import com.example.delivery.domain.rider.Rider;

import java.util.List;

/**
 * 骑手调度策略（策略模式 Strategy 的「抽象策略」角色）。
 *
 * <p><b>设计模式：策略模式（Strategy）</b>，并由中介者（Mediator）调用。</p>
 *
 * <p><b>为什么把调度算法抽出来：</b>「派给谁」是运营策略，会经常调整——
 * 高峰期看谁手上单少（防爆单）、平峰看谁离得近（省时间）、恶劣天气看谁评分高（保体验）。
 * 如果这些判断写进派单中心，派单中心会变成一坨 {@code if-else}，
 * 而且每加一种策略都要重新测试派单主流程。</p>
 *
 * <p>抽成策略后，派单中心只负责「协调」这一件事（中介者职责），
 * 「怎么挑」被完全外置，可运行期切换、可单独测试。</p>
 *
 * @author 课程大作业
 */
@FunctionalInterface
public interface DispatchStrategy {

    /**
     * 从候选骑手中挑选一名。
     *
     * @param candidates 可接单骑手列表（可能为空，实现需处理）
     * @param order      待派订单（提供商家、地址、金额等上下文）
     * @return 选中的骑手；无可选时返回 {@code null}
     */
    Rider pick(List<Rider> candidates, Order order);

    /**
     * 策略名称，用于日志与报表。
     *
     * @return 例如 {@code "最近优先"}
     */
    default String name() {
        return getClass().getSimpleName();
    }

    /**
     * 策略说明（控制台打印选择理由时使用）。
     *
     * @return 规则说明
     */
    default String rule() {
        return name();
    }
}
