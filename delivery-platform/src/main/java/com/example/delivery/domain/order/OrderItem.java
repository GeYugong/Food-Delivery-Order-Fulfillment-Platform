package com.example.delivery.domain.order;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * 订单明细行（值对象 / Value Object）。
 *
 * <p><b>设计模式关联：原型模式（Prototype）的载体 + 组合模式（Composite）的叶子节点</b></p>
 * <p><b>为什么用 record：</b>明细行「是什么」由菜品、数量、下单时的单价唯一决定，
 * 没有独立生命周期，属于典型不可变值对象。用 {@code record} 实现可以：</p>
 * <ol>
 *   <li>自动获得 {@code equals/hashCode/toString}，测试断言与日志输出更直观；</li>
 *   <li>天然不可变，作为订单聚合内部元素可以安全共享；
 *       订单复制（原型模式）时只需复制引用，不必深拷贝整行；</li>
 *   <li>下单时「锁价」语义清晰：{@code unitPrice} 是下单那一刻的价格快照，
 *       之后商家调价不影响已有订单。</li>
 * </ol>
 *
 * @param dishId    菜品标识，例如 {@code "DISH-001"}
 * @param dishName  菜品名称快照（同样为了历史可读性，不随菜单改名而变化）
 * @param unitPrice 下单时的单价快照，不可为负
 * @param quantity  数量，必须大于 0
 * @author 课程大作业
 */
public record OrderItem(String dishId, String dishName, BigDecimal unitPrice, int quantity) {

    /**
     * 紧凑构造器：在对象诞生时就完成不变量校验，避免「非法明细行」在系统里传播。
     *
     * @throws IllegalArgumentException 当任一参数非法时抛出
     */
    public OrderItem {
        if (dishId == null || dishId.isBlank()) {
            throw new IllegalArgumentException("菜品标识不能为空");
        }
        if (dishName == null || dishName.isBlank()) {
            throw new IllegalArgumentException("菜品名称不能为空");
        }
        if (unitPrice == null || unitPrice.signum() < 0) {
            throw new IllegalArgumentException("菜品单价不能为空且不能为负数：" + unitPrice);
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("菜品数量必须大于 0：" + quantity);
        }
        // 金额统一保留 2 位小数，避免 0.1 + 0.2 这类精度问题在报表里冒出来
        unitPrice = unitPrice.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 便捷工厂：便于 Demo 与测试快速构造明细行。
     *
     * @param dishId    菜品标识
     * @param dishName  菜品名称
     * @param unitPrice 单价（元）
     * @param quantity  数量
     * @return 明细行
     */
    public static OrderItem of(String dishId, String dishName, String unitPrice, int quantity) {
        return new OrderItem(dishId, dishName, new BigDecimal(unitPrice), quantity);
    }

    /**
     * 本行小计 = 单价 × 数量。
     *
     * @return 小计金额，保留 2 位小数
     */
    public BigDecimal subtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 返回「数量翻倍」的新明细行（原型模式 Demo 中演示「基于原型派生新对象」）。
     *
     * @param extraQuantity 追加的数量，必须大于 0
     * @return 新的明细行对象，本对象不变（不可变对象的「修改」即产生新对象）
     */
    public OrderItem withExtraQuantity(int extraQuantity) {
        if (extraQuantity <= 0) {
            throw new IllegalArgumentException("追加数量必须大于 0：" + extraQuantity);
        }
        return new OrderItem(dishId, dishName, unitPrice, quantity + extraQuantity);
    }

    /** @return 人类可读的明细行描述，例如 {@code "香辣鸡腿堡 × 2 = 39.60 元"} */
    public String describe() {
        return String.format("%s × %d = %s 元", dishName, quantity, subtotal().toPlainString());
    }

    /** 便捷判空：用于 {@code Objects.equals} 之外的业务比较。 */
    public boolean sameDishAs(OrderItem other) {
        return other != null && Objects.equals(this.dishId, other.dishId);
    }
}
