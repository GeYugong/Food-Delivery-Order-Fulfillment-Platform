package com.example.delivery.application;

import com.example.delivery.domain.merchant.Merchant;
import com.example.delivery.domain.order.Order;
import com.example.delivery.domain.user.User;

/**
 * 下单校验上下文（职责链模式 Chain of Responsibility 的「请求对象」）。
 *
 * <p><b>为什么校验器不直接接收 {@link Order}：</b>如果每个校验器都从订单里去捞用户、
 * 捞商家、查库存、读配置，链条上每个环节都会重复这些准备工作，而且很难测试。
 * 把「本次校验需要的数据」提前装进上下文对象（也叫参数对象 / Parameter Object），
 * 校验器就变成纯函数式的「读上下文 → 返回是否通过」，单元测试只需构造一个上下文。</p>
 *
 * <p>上下文的 {@code reject} 字段让链条能记录「被哪一环拦下、原因是什么」，
 * 从而在控制台打印完整的否决轨迹——这对课堂演示职责链非常直观。</p>
 *
 * @param order      待校验订单
 * @param user       下单用户
 * @param merchant   商家
 * @param recentOrderCount 用户最近 1 分钟内的下单次数（频控校验使用）
 * @author 课程大作业
 */
public record OrderValidationContext(Order order, User user, Merchant merchant, int recentOrderCount) {

    /**
     * 便捷工厂：默认最近下单次数为 0。
     *
     * @param order    订单
     * @param user     用户
     * @param merchant 商家
     * @return 上下文
     */
    public static OrderValidationContext of(Order order, User user, Merchant merchant) {
        return new OrderValidationContext(order, user, merchant, 0);
    }

    /**
     * 派生一个「最近下单次数不同」的新上下文（record 不可变，故返回新对象）。
     *
     * @param count 新的下单次数
     * @return 新上下文
     */
    public OrderValidationContext withRecentOrderCount(int count) {
        return new OrderValidationContext(order, user, merchant, count);
    }
}
