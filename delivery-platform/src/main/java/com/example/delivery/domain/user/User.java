package com.example.delivery.domain.user;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * 用户（下单方）领域模型。
 *
 * <p><b>设计模式关联：</b></p>
 * <ul>
 *   <li><b>抽象工厂模式（Abstract Factory）</b>：用户与商家、菜品同属「业务对象族」，
 *       由平台工厂统一创建，保证同一套演示数据（同一城市/同一费率体系）彼此自洽。</li>
 *   <li><b>原型模式（Prototype）</b>：{@link #withAddress(String)} 以「基于原型派生新对象」
 *       的方式修改地址，避免可变对象被多张订单共享后相互污染。</li>
 *   <li><b>观察者模式（Observer）</b>：用户是订单事件的订阅者之一（支付成功、骑手接单、订单送达）。</li>
 * </ul>
 *
 * @param userId          用户编号，例如 {@code "U-1001"}
 * @param name            昵称
 * @param phone           手机号（用于骑手联系与脱敏展示）
 * @param address         默认收货地址（下单时可覆盖，订单里保存快照）
 * @param balance         账户余额（演示「余额支付」适配器使用）
 * @param memberLevel     会员等级，影响计价策略中的折扣（策略模式数据来源）
 * @author 课程大作业
 */
public record User(
        String userId,
        String name,
        String phone,
        String address,
        BigDecimal balance,
        MemberLevel memberLevel) {

    /** 紧凑构造器：校验必填项与金额合法性。 */
    public User {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("用户编号不能为空");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("用户昵称不能为空");
        }
        if (balance == null || balance.signum() < 0) {
            throw new IllegalArgumentException("账户余额不能为空且不能为负数");
        }
        balance = balance.setScale(2, RoundingMode.HALF_UP);
        memberLevel = memberLevel == null ? MemberLevel.NORMAL : memberLevel;
    }

    /**
     * 便捷工厂：普通会员。
     *
     * @param userId  用户编号
     * @param name    昵称
     * @param phone   手机号
     * @param address 地址
     * @param balance 余额
     * @return 用户对象
     */
    public static User of(String userId, String name, String phone, String address, String balance) {
        return new User(userId, name, phone, address, new BigDecimal(balance), MemberLevel.NORMAL);
    }

    /**
     * 基于当前用户派生一个「地址不同」的新用户对象（原型思想）。
     *
     * @param newAddress 新地址
     * @return 新用户对象，本对象不变
     */
    public User withAddress(String newAddress) {
        return new User(userId, name, phone, newAddress, balance, memberLevel);
    }

    /**
     * 扣减余额（余额支付成功后调用）。
     *
     * @param amount 扣减金额
     * @return 扣减后的新用户对象（record 不可变，故返回新对象）
     * @throws IllegalArgumentException 余额不足时抛出
     */
    public User deductBalance(BigDecimal amount) {
        Objects.requireNonNull(amount, "扣减金额不能为空");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("扣减金额不能为负数");
        }
        if (balance.compareTo(amount) < 0) {
            throw new IllegalArgumentException("余额不足：当前 " + balance.toPlainString()
                    + " 元，需要 " + amount.toPlainString() + " 元");
        }
        return new User(userId, name, phone, address, balance.subtract(amount), memberLevel);
    }

    /**
     * 手机号脱敏，用于控制台与日志输出。
     *
     * @return 形如 {@code "138****8888"} 的字符串
     */
    public String maskedPhone() {
        if (phone == null || phone.length() < 7) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /** @return 人类可读描述 */
    public String describe() {
        return String.format("%s(%s, %s, 余额 %s 元)", name, userId, memberLevel.label(),
                balance.toPlainString());
    }

    /**
     * 会员等级枚举。
     *
     * <p><b>设计模式关联：策略模式（Strategy）的数据来源</b>——
     * 不同等级对应不同的计价参数，策略实现类读取本枚举决定费率，而不是用 if-else 硬编码。</p>
     */
    public enum MemberLevel {

        /** 普通会员：无折扣 */
        NORMAL("普通会员", new BigDecimal("0.00")),
        /** 黄金会员：9.5 折 */
        GOLD("黄金会员", new BigDecimal("0.05")),
        /** 钻石会员：9 折 */
        DIAMOND("钻石会员", new BigDecimal("0.10"));

        private final String label;
        private final BigDecimal discountRate;

        MemberLevel(String label, BigDecimal discountRate) {
            this.label = label;
            this.discountRate = discountRate;
        }

        /** @return 中文显示名 */
        public String label() {
            return label;
        }

        /** @return 折扣率，例如 {@code 0.05} 表示 95 折 */
        public BigDecimal discountRate() {
            return discountRate;
        }
    }
}
