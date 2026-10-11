package com.example.delivery.domain.rider;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * 骑手领域模型。
 *
 * <p><b>设计模式关联：</b></p>
 * <ul>
 *   <li><b>中介者模式（Mediator）</b>：骑手之间不互相通信，
 *       派单由「调度中心」中介者统一协调（避免骑手两两耦合）。</li>
 *   <li><b>策略模式（Strategy）</b>：骑手的「可接单性」由 {@link #canAccept()} 抽象出来，
 *       不同调度策略（最近优先 / 最少单量优先）都基于同一判定接口工作。</li>
 *   <li><b>状态模式（State）</b>：{@link RiderStatus} 描述骑手自身的可用性状态，
 *       与订单状态解耦但相互联动（骑手接单后变为派送中）。</li>
 *   <li><b>观察者模式（Observer）</b>：骑手订阅「新订单已支付」事件后才会被调度。</li>
 * </ul>
 *
 * @param riderId         骑手编号，例如 {@code "R-3001"}
 * @param name            姓名
 * @param phone           手机号（脱敏展示）
 * @param status          当前状态
 * @param currentLoad     当前在手订单数（负载均衡策略的输入）
 * @param maxLoad         最大可接单量（超过则 {@link #canAccept()} 返回 false）
 * @param currentDistanceKm 距离商家的距离（公里），最近优先策略的输入
 * @param rating          评分（0.00 ~ 5.00），影响派单优先级
 * @author 课程大作业
 */
public record Rider(
        String riderId,
        String name,
        String phone,
        RiderStatus status,
        int currentLoad,
        int maxLoad,
        BigDecimal currentDistanceKm,
        BigDecimal rating) {

    /** 紧凑构造器：校验不变量。 */
    public Rider {
        if (riderId == null || riderId.isBlank()) {
            throw new IllegalArgumentException("骑手编号不能为空");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("骑手姓名不能为空");
        }
        if (maxLoad <= 0) {
            throw new IllegalArgumentException("最大接单量必须大于 0");
        }
        if (currentLoad < 0 || currentLoad > maxLoad) {
            throw new IllegalArgumentException("当前在手单量必须在 0 ~ " + maxLoad + " 之间");
        }
        status = status == null ? RiderStatus.IDLE : status;
        currentDistanceKm = currentDistanceKm == null ? BigDecimal.ZERO : currentDistanceKm;
        rating = rating == null ? new BigDecimal("5.00") : rating.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 便捷工厂：空闲骑手。
     *
     * @param riderId  编号
     * @param name     姓名
     * @param phone    手机号
     * @param distance 距商家距离（公里）
     * @param rating   评分
     * @return 骑手对象
     */
    public static Rider idle(String riderId, String name, String phone,
                             String distance, String rating) {
        return new Rider(riderId, name, phone, RiderStatus.IDLE, 0, 3,
                new BigDecimal(distance), new BigDecimal(rating));
    }

    /**
     * 占位骑手：只有编号时使用。
     *
     * <p><b>使用场景：</b>订单从存储/快照恢复时可能只保留了骑手编号（跨进程存档不会序列化整个骑手对象），
     * 此时用本方法构造一个「只知道编号」的占位对象，保证订单的 {@code rider()} 不为空，
     * 后续需要完整信息时再由骑手仓储按编号回填。</p>
     *
     * @param riderId 骑手编号
     * @return 状态为离线的占位骑手
     */
    public static Rider placeholder(String riderId) {
        return new Rider(riderId, "骑手-" + riderId, null, RiderStatus.OFFLINE, 0, 1,
                BigDecimal.ZERO, new BigDecimal("5.00"));
    }

    /**
     * 是否可接单。
     *
     * <p>三个条件同时满足：状态为空闲、未超负载、且不在休息中。</p>
     *
     * @return 可接单返回 {@code true}
     */
    public boolean canAccept() {
        return status == RiderStatus.IDLE && currentLoad < maxLoad;
    }

    /**
     * 接单后派生新的骑手状态对象（record 不可变）。
     *
     * @return 在手单量 +1、状态变为派送中的新对象
     */
    public Rider acceptOrder() {
        return new Rider(riderId, name, phone, RiderStatus.DELIVERING,
                currentLoad + 1, maxLoad, currentDistanceKm, rating);
    }

    /**
     * 送达后派生新的骑手状态对象。
     *
     * @return 在手单量 -1、状态回归空闲的新对象
     */
    public Rider finishOrder() {
        int newLoad = Math.max(0, currentLoad - 1);
        RiderStatus newStatus = newLoad == 0 ? RiderStatus.IDLE : RiderStatus.DELIVERING;
        return new Rider(riderId, name, phone, newStatus, newLoad, maxLoad, currentDistanceKm, rating);
    }

    /** @return 手机号脱敏结果 */
    public String maskedPhone() {
        if (phone == null || phone.length() < 7) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /** @return 负载率描述，例如 {@code "1/3"} */
    public String loadText() {
        return currentLoad + "/" + maxLoad;
    }

    /** @return 人类可读描述 */
    public String describe() {
        return String.format("%s(%s, %s, 负载 %s, 距离 %s km, 评分 %s)",
                name, riderId, status.label(), loadText(),
                currentDistanceKm.toPlainString(), rating.toPlainString());
    }

    /**
     * 骑手状态枚举。
     *
     * <p><b>设计模式：状态模式（State）</b>——骑手可用性是一个小型状态机，
     * 把「空闲 / 派送中 / 休息 / 离线」显式建模，避免用 {@code boolean available} 之类的
     * 布尔标志造成状态含义被压扁（布尔无法表达「休息」与「离线」的差别）。</p>
     */
    public enum RiderStatus {

        /** 空闲：可被派单 */
        IDLE("空闲"),
        /** 派送中：手上有单，仍可能继续接顺路单 */
        DELIVERING("派送中"),
        /** 休息：主动休息，不接单 */
        RESTING("休息"),
        /** 离线：未登录，不接单 */
        OFFLINE("离线");

        private final String label;

        RiderStatus(String label) {
            this.label = label;
        }

        /** @return 中文显示名 */
        public String label() {
            return label;
        }
    }
}
