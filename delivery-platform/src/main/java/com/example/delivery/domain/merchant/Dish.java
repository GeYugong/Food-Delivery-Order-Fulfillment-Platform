package com.example.delivery.domain.merchant;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 菜品领域模型（也可作为套餐的组合子节点）。
 *
 * <p><b>设计模式关联：</b></p>
 * <ul>
 *   <li><b>组合模式（Composite）的叶子（Leaf）</b>：与后续的套餐（组合节点）共享「可计价节点」语义，
 *       让订单明细不关心自己装的是单品还是套餐。</li>
 *   <li><b>建造者模式（Builder）</b>：菜品有名称、价格、库存、上下架状态、辣度等较多可选属性，
 *       由菜品建造者组装（Demo 中演示）。</li>
 *   <li><b>原型模式（Prototype）</b>：{@link #withPrice(String)} 以「复制 + 改价」的方式生成新菜品，
 *       用于菜单版本迭代，避免直接修改在售菜品对象。</li>
 * </ul>
 *
 * @param dishId    菜品编号，例如 {@code "DISH-001"}
 * @param name      菜品名称
 * @param price     售价（元）
 * @param stock     库存份数（校验链中的「库存校验」使用）
 * @param available 是否在售（下架菜品不允许下单）
 * @param category  分类，用于报表按品类统计（访问者模式）
 * @author 课程大作业
 */
public record Dish(
        String dishId,
        String name,
        BigDecimal price,
        int stock,
        boolean available,
        String category) {

    /** 紧凑构造器：校验不变量。 */
    public Dish {
        if (dishId == null || dishId.isBlank()) {
            throw new IllegalArgumentException("菜品编号不能为空");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("菜品名称不能为空");
        }
        if (price == null || price.signum() < 0) {
            throw new IllegalArgumentException("菜品价格不能为空且不能为负数");
        }
        if (stock < 0) {
            throw new IllegalArgumentException("库存不能为负数");
        }
        price = price.setScale(2, RoundingMode.HALF_UP);
        category = (category == null || category.isBlank()) ? "未分类" : category;
    }

    /**
     * 便捷工厂：在售菜品。
     *
     * @param dishId   编号
     * @param name     名称
     * @param price    价格（元，字符串形式）
     * @param stock    库存
     * @param category 分类
     * @return 菜品对象
     */
    public static Dish available(String dishId, String name, String price, int stock, String category) {
        return new Dish(dishId, name, new BigDecimal(price), stock, true, category);
    }

    /**
     * 复制并改价（原型思想：不修改原对象）。
     *
     * @param newPrice 新价格（元，字符串形式）
     * @return 新菜品对象
     */
    public Dish withPrice(String newPrice) {
        return new Dish(dishId, name, new BigDecimal(newPrice), stock, available, category);
    }

    /**
     * 扣减库存。
     *
     * @param quantity 扣减份数
     * @return 扣减后的新菜品对象
     * @throws IllegalArgumentException 库存不足时抛出
     */
    public Dish reduceStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("扣减数量必须大于 0");
        }
        if (quantity > stock) {
            throw new IllegalArgumentException(String.format("菜品[%s]库存不足：剩余 %d 份，需要 %d 份",
                    name, stock, quantity));
        }
        return new Dish(dishId, name, price, stock - quantity, available, category);
    }

    /**
     * 是否可下单：在售且有库存。
     *
     * @param quantity 期望购买份数
     * @return 可下单返回 {@code true}
     */
    public boolean canOrder(int quantity) {
        return available && quantity > 0 && stock >= quantity;
    }

    /** @return 人类可读描述 */
    public String describe() {
        return String.format("%s(%s, %s 元, 库存 %d, %s)",
                name, dishId, price.toPlainString(), stock, available ? "在售" : "已下架");
    }
}
