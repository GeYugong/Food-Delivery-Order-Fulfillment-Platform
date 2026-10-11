package com.example.delivery.domain.support;

import com.example.delivery.domain.order.Order;

import java.math.BigDecimal;

/**
 * 优惠券求值器（解释器模式的边界接口）。
 *
 * <p><b>设计模式：解释器模式（Interpreter）的对外契约 + 依赖倒置</b></p>
 *
 * <p><b>为什么领域层只定义接口、不定义解释器实现：</b>优惠券规则的语法（{@code "满30减5"}、
 * {@code "9折"}、{@code "且/或"}）属于「平台运营配置规则」这一外部关注点，
 * 领域层只需要「给定订单，告诉我能抵扣多少钱」这一个能力。</p>
 *
 * <p>这样分层后：解析器/语法树/表达式节点全部落在基础设施层，
 * 领域层与订单聚合完全不知道规则语法存在；将来把自研解释器换成脚本引擎（Groovy/QLExpress）
 * 也不用改动业务代码。</p>
 *
 * @author 课程大作业
 */
public interface CouponEvaluator {

    /**
     * 计算优惠券对某订单的可抵扣金额。
     *
     * <p>实现必须保证：</p>
     * <ol>
     *   <li>抵扣金额不为负；</li>
     *   <li>抵扣金额不超过订单菜品小计（否则实付会变成负数）；</li>
     *   <li>券不在有效期或未达门槛时返回 {@code BigDecimal.ZERO}，并给出 {@link #explain()} 说明。</li>
     * </ol>
     *
     * @param order 订单（提供小计与会员等级）
     * @return 可抵扣金额，保留 2 位小数
     */
    BigDecimal evaluate(Order order);

    /**
     * 最近一次求值的可读说明（例如「规则树：And(满30减5, 等级>=GOLD) → 抵扣 5.00 元」），
     * 供控制台打印「为什么减了这么多」，也是解释器模式最好的演示材料。
     *
     * @return 求值说明
     */
    String explain();
}
