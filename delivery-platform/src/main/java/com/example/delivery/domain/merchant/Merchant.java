package com.example.delivery.domain.merchant;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 商家领域模型（含菜单）。
 *
 * <p><b>设计模式关联：</b></p>
 * <ul>
 *   <li><b>组合模式（Composite）</b>：{@link #menu()} 是菜品集合；
 *       后续引入套餐（{@code ComboMeal}）时，套餐与单品都实现同一个「可计价节点」接口，
 *       订单明细可以统一处理，无需区分「这是单品还是套餐」。</li>
 *   <li><b>原型模式（Prototype）</b>：商家菜单可整体克隆为新菜单版本（改价、上下架）。</li>
 *   <li><b>外观模式（Facade）</b>：{@link #sells(String)}、{@link #findDish(String)}、{@link #reachesMinimum}
 *       为订单校验链提供简洁的商家侧查询入口，隐藏菜单数据结构。</li>
 * </ul>
 *
 * @param merchantId   商家编号，例如 {@code "M-2001"}
 * @param name         商家名称
 * @param address      商家地址（骑手取餐点）
 * @param dishes       菜单（菜品列表）
 * @param minimumOrder 起送价（校验链中的「起送价校验」使用）
 * @param deliveryFee  该商家的基础配送费（计价策略的输入之一）
 * @author 课程大作业
 */
public record Merchant(
        String merchantId,
        String name,
        String address,
        List<Dish> dishes,
        BigDecimal minimumOrder,
        BigDecimal deliveryFee) {

    /** 紧凑构造器：复制菜单为不可变列表，校验金额字段。 */
    public Merchant {
        if (merchantId == null || merchantId.isBlank()) {
            throw new IllegalArgumentException("商家编号不能为空");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("商家名称不能为空");
        }
        dishes = dishes == null ? List.of() : List.copyOf(dishes);
        minimumOrder = minimumOrder == null ? BigDecimal.ZERO : minimumOrder;
        deliveryFee = deliveryFee == null ? BigDecimal.ZERO : deliveryFee;
    }

    /**
     * 判断本商家是否售卖某个菜品。
     *
     * <p>供 {@code Order#validateInvariants()} 校验「订单明细必须属于同一商家」。</p>
     *
     * @param dishId 菜品编号
     * @return 售卖返回 {@code true}
     */
    public boolean sells(String dishId) {
        return dishes.stream().anyMatch(dish -> dish.dishId().equals(dishId));
    }

    /**
     * 按编号查找菜品。
     *
     * @param dishId 菜品编号
     * @return 命中返回菜品，否则返回空
     */
    public Optional<Dish> findDish(String dishId) {
        return dishes.stream().filter(dish -> dish.dishId().equals(dishId)).findFirst();
    }

    /**
     * 判断订单小计是否达到起送价。
     *
     * @param subtotal 订单小计
     * @return 达到起送价返回 {@code true}
     */
    public boolean reachesMinimum(BigDecimal subtotal) {
        return subtotal != null && subtotal.compareTo(minimumOrder) >= 0;
    }

    /** @return 菜单中可售菜品的数量 */
    public int availableDishCount() {
        return (int) dishes.stream().filter(Dish::available).count();
    }

    /** @return 人类可读描述 */
    public String describe() {
        return String.format("%s(%s, 起送价 %s 元, 配送费 %s 元, 在售 %d 道菜)",
                name, merchantId, minimumOrder.toPlainString(),
                deliveryFee.toPlainString(), availableDishCount());
    }
}
