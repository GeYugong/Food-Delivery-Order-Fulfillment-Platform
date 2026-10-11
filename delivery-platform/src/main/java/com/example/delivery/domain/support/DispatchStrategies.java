package com.example.delivery.domain.support;

import com.example.delivery.domain.order.Order;
import com.example.delivery.domain.rider.Rider;

import java.util.Comparator;
import java.util.List;

/**
 * 骑手调度策略集合（策略模式的具体策略实现）。
 *
 * <p><b>为什么放在一个文件里：</b>三个策略都非常短（每个 5~15 行），
 * 且共享同一个「按某个字段排序取第一」的骨架；放在一起便于对比三种排序维度，
 * 演示「策略模式 = 把变化的那一行抽出来」。</p>
 *
 * @author 课程大作业
 */
public final class DispatchStrategies {

    /** 工具类不允许实例化。 */
    private DispatchStrategies() {
    }

    /**
     * 最近优先：距离商家最近的骑手优先。
     *
     * <p>适用于平峰期，目标是最小化「骑手到店」的时间。</p>
     *
     * @return 策略实例
     */
    public static DispatchStrategy nearest() {
        return new NearestFirst();
    }

    /**
     * 最少负载优先：在手订单最少的骑手优先。
     *
     * <p>适用于高峰期，目标是把单量摊平，避免个别骑手爆单导致超时。</p>
     *
     * @return 策略实例
     */
    public static DispatchStrategy leastLoaded() {
        return new LeastLoadedFirst();
    }

    /**
     * 评分最高优先：评分最高的骑手优先，评分相同时看距离。
     *
     * <p>适用于恶劣天气或高价值订单，目标是保障服务体验。</p>
     *
     * @return 策略实例
     */
    public static DispatchStrategy highestRated() {
        return new HighestRatedFirst();
    }

    /** 最近优先策略。 */
    public static final class NearestFirst implements DispatchStrategy {

        @Override
        public String name() {
            return "最近优先";
        }

        @Override
        public String rule() {
            return "按骑手与商家的距离升序，取最近者";
        }

        @Override
        public Rider pick(List<Rider> candidates, Order order) {
            return candidates.stream()
                    .min(Comparator.comparing(Rider::currentDistanceKm))
                    .orElse(null);
        }
    }

    /** 最少负载优先策略。 */
    public static final class LeastLoadedFirst implements DispatchStrategy {

        @Override
        public String name() {
            return "最少负载优先";
        }

        @Override
        public String rule() {
            return "按骑手在手订单数升序，负载相同则取距离更近者";
        }

        @Override
        public Rider pick(List<Rider> candidates, Order order) {
            return candidates.stream()
                    .min(Comparator.comparingInt(Rider::currentLoad)
                            .thenComparing(Rider::currentDistanceKm))
                    .orElse(null);
        }
    }

    /** 评分最高优先策略。 */
    public static final class HighestRatedFirst implements DispatchStrategy {

        @Override
        public String name() {
            return "评分最高优先";
        }

        @Override
        public String rule() {
            return "按骑手评分降序，评分相同则取距离更近者";
        }

        @Override
        public Rider pick(List<Rider> candidates, Order order) {
            return candidates.stream()
                    .max(Comparator.comparing(Rider::rating)
                            .thenComparing(rider -> rider.currentDistanceKm().negate()))
                    .orElse(null);
        }
    }
}
