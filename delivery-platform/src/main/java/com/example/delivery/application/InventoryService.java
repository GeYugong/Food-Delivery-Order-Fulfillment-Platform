package com.example.delivery.application;

import com.example.delivery.domain.merchant.Dish;
import com.example.delivery.domain.merchant.Merchant;
import com.example.delivery.domain.order.Order;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 库存服务（内存实现，控制台演示用）。
 *
 * <p><b>为什么单独抽一个服务而不是直接改 {@code Dish}：</b>
 * {@link Dish} 是不可变值对象（record），代表「菜品定义（含初始库存）」；
 * 而「当前剩余库存」是**高频变化、需要并发控制**的运行时数据。
 * 把两者分开是领域驱动设计中「值对象 vs 可变状态」的典型区分：
 * 菜品定义可以放心缓存与共享，库存必须集中管理。</p>
 *
 * <p><b>设计模式关联：</b></p>
 * <ul>
 *   <li><b>单例模式</b>：{@link #getInstance()} 提供全局唯一库存视图；</li>
 *   <li><b>原型模式</b>：{@link #register(Merchant)} 依据菜品定义「派生」出库存记录；</li>
 *   <li><b>命令模式的接收者（Receiver）</b>：预占/释放库存是被命令对象调用的具体动作，
 *       支持失败回滚（{@link #release}）。</li>
 * </ul>
 *
 * @author 课程大作业
 */
public class InventoryService {

    /** 单例。 */
    private static final InventoryService INSTANCE = new InventoryService();

    /** 菜品编号 → 当前剩余库存。 */
    private final Map<String, Integer> stock = new LinkedHashMap<>();

    /** 菜品编号 → 菜品名称（用于日志可读性）。 */
    private final Map<String, String> names = new LinkedHashMap<>();

    /** 私有构造器。 */
    private InventoryService() {
    }

    /**
     * @return 单例
     */
    public static InventoryService getInstance() {
        return INSTANCE;
    }

    /**
     * 依据商家菜单注册（或重置）库存。
     *
     * @param merchant 商家
     */
    public void register(Merchant merchant) {
        for (Dish dish : merchant.dishes()) {
            stock.put(dish.dishId(), dish.stock());
            names.put(dish.dishId(), dish.name());
        }
    }

    /**
     * 查询剩余库存。
     *
     * @param dishId 菜品编号
     * @return 剩余份数；未注册的菜品返回 0（保守策略：未知即不可售）
     */
    public int remaining(String dishId) {
        return stock.getOrDefault(dishId, 0);
    }

    /**
     * 判断库存是否充足。
     *
     * @param dishId   菜品编号
     * @param quantity 需要份数
     * @return 充足返回 true
     */
    public boolean hasEnough(String dishId, int quantity) {
        return remaining(dishId) >= quantity;
    }

    /**
     * 预占库存（下单成功后扣减）。
     *
     * @param dishId   菜品编号
     * @param quantity 份数
     * @return 预占成功返回 true；库存不足返回 false 且不做任何修改
     */
    public synchronized boolean reserve(String dishId, int quantity) {
        int current = remaining(dishId);
        if (current < quantity) {
            return false;
        }
        stock.put(dishId, current - quantity);
        return true;
    }

    /**
     * 释放库存（订单取消、支付失败、超时未支付时回滚）。
     *
     * @param dishId   菜品编号
     * @param quantity 份数
     */
    public synchronized void release(String dishId, int quantity) {
        stock.merge(dishId, quantity, Integer::sum);
    }

    /**
     * 为整张订单预占库存：要么全部成功，要么全部回滚（简易的两阶段语义）。
     *
     * <p><b>为什么需要「原子性」：</b>一个订单含多道菜，若第 3 道菜库存不足，
     * 前 2 道菜已经扣掉的库存必须还回去，否则库存会被慢慢「吃掉」。</p>
     *
     * @param order 订单
     * @return 全部预占成功返回 true
     */
    public synchronized boolean reserveAll(Order order) {
        var reserved = new java.util.ArrayList<com.example.delivery.domain.order.OrderItem>();
        for (var item : order.items()) {
            if (reserve(item.dishId(), item.quantity())) {
                reserved.add(item);
            } else {
                // 回滚已预占的部分
                reserved.forEach(done -> release(done.dishId(), done.quantity()));
                return false;
            }
        }
        return true;
    }

    /**
     * 释放整张订单占用的库存。
     *
     * @param order 订单
     */
    public synchronized void releaseAll(Order order) {
        order.items().forEach(item -> release(item.dishId(), item.quantity()));
    }

    /**
     * 减少库存（简化接口：等价于 reserve 但语义为「直接出库」）。
     *
     * @param order 订单
     */
    public synchronized void deductAll(Order order) {
        order.items().forEach(item -> {
            int current = remaining(item.dishId());
            stock.put(item.dishId(), Math.max(0, current - item.quantity()));
        });
    }

    /**
     * 查询菜品名称。
     *
     * @param dishId 菜品编号
     * @return 名称（未注册时返回编号本身）
     */
    public String dishName(String dishId) {
        return names.getOrDefault(dishId, dishId);
    }

    /**
     * 以「菜品名:剩余」形式导出现有库存，便于控制台展示。
     *
     * @return 库存快照字符串（按菜品编号排序）
     */
    public String snapshotText() {
        StringBuilder sb = new StringBuilder();
        stock.keySet().stream().sorted().forEach(dishId ->
                sb.append(String.format("%s=%d  ", dishName(dishId), remaining(dishId))));
        return sb.toString().trim();
    }

    /**
     * 查询某菜品库存记录的 Optional 视图（便于函数式调用）。
     *
     * @param dishId 菜品编号
     * @return 可能为空的库存
     */
    public Optional<Integer> findStock(String dishId) {
        return Optional.ofNullable(stock.get(dishId));
    }

    /** 清空库存数据（测试用）。 */
    public void clear() {
        stock.clear();
        names.clear();
    }
}
