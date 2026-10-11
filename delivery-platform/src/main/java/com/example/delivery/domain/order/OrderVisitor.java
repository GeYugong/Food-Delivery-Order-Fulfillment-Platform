package com.example.delivery.domain.order;

/**
 * 订单访问者接口。
 *
 * <p><b>设计模式：访问者模式（Visitor）的 Visitor 角色</b></p>
 *
 * <p><b>为什么用：</b>订单数据要被多套「与业务无关的横向功能」读取——
 * 统计报表（营业额、客单价、菜品销量榜）、数据导出（CSV/JSON）、风控审计（异常大额订单）、
 * 订单存档（序列化）。如果把这些逻辑写进 {@link Order}，订单类会被反复修改，
 * 违反开闭原则；用访问者把这些操作外置，每新增一种操作只需新增一个实现类。</p>
 *
 * <p>代价是「新增订单字段」需要改动所有 Visitor，这在课程报告中是很好的权衡讨论点。</p>
 *
 * @param <R> 访问结果类型，例如 {@code String}（报表文本）或 {@code OrderStatistics}（统计对象）
 * @author 课程大作业
 */
@FunctionalInterface
public interface OrderVisitor<R> {

    /**
     * 访问一张订单。
     *
     * @param order 被访问的订单（双分派：由 {@link Order#accept(OrderVisitor)} 回调本方法）
     * @return 访问结果
     */
    R visit(Order order);
}
