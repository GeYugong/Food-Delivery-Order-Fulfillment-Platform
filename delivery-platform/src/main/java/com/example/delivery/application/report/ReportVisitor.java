package com.example.delivery.application.report;

import com.example.delivery.domain.order.Order;
import com.example.delivery.domain.order.OrderVisitor;
import com.example.delivery.domain.order.OrderStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统计报表访问者（访问者模式 Visitor 的「具体访问者」角色）。
 *
 * <p><b>设计模式：访问者模式（Visitor）</b></p>
 *
 * <p><b>为什么统计逻辑要从订单里搬出来：</b>订单聚合只应该关心「订单自身的业务规则」
 * （金额、状态、明细）。而「营业额」「客单价」「菜品销量榜」「骑手接单榜」
 * 这些是<b>横向的、面向读的</b>统计需求，它们：</p>
 * <ol>
 *   <li>变化频繁（运营每周想看新指标）；</li>
 *   <li>与订单自身的状态流转无关；</li>
 *   <li>需要跨订单聚合，单个订单根本算不出来。</li>
 * </ol>
 * <p>把它们放进 {@link Order} 会让聚合越来越胖；用访问者把它们外置后，
 * 新增一个指标 = 新增一个 Visitor 实现，<b>订单类一行都不用改</b>。</p>
 *
 * <p><b>本类的状态性：</b>访问者是有状态的累加器（每次报表前需要新建实例或调用
 * {@link #reset()}）。这是访问者模式的常见用法：对一批元素逐个 {@code accept}，
 * 访问者在内部累积结果。缺点是同一个实例不能被并发复用。</p>
 *
 * @author 课程大作业
 */
public class ReportVisitor implements OrderVisitor<Void> {

    /** 订单总数。 */
    private int totalOrders;

    /** 已完成订单数。 */
    private int completedOrders;

    /** 已取消订单数。 */
    private int cancelledOrders;

    /** 配送中订单数。 */
    private int deliveringOrders;

    /** 已完成订单营业额。 */
    private BigDecimal grossRevenue = BigDecimal.ZERO;

    /** 累计优惠金额（所有订单）。 */
    private BigDecimal totalDiscount = BigDecimal.ZERO;

    /** 累计配送费（所有订单）。 */
    private BigDecimal totalDeliveryFee = BigDecimal.ZERO;

    /** 大额订单数量。 */
    private int highValueOrders;

    /** 已完成订单的履约总秒数。 */
    private long totalFulfillmentSeconds;

    /** 菜品销量：菜名 → [销量, 销售额]。 */
    private final Map<String, BigDecimal[]> dishSales = new LinkedHashMap<>();

    /** 骑手接单量：骑手名 → 单数。 */
    private final Map<String, Integer> riderLoads = new LinkedHashMap<>();

    /** 大额订单阈值。 */
    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("100.00");

    /**
     * 访问一张订单：把它的数据累加进统计结果。
     *
     * <p>这就是访问者模式的「双分派」落点：{@code order.accept(visitor)} 会回调本方法，
     * 于是「执行哪个操作」由 visitor 的运行时类型决定，
     * 「操作哪个元素」由 order 的运行时类型决定。</p>
     *
     * @param order 订单
     * @return 恒为 null（本访问者只做累加，不产出单元素结果）
     */
    @Override
    public Void visit(Order order) {
        totalOrders++;

        // ---- 状态维度统计 ----
        switch (order.status()) {
            case COMPLETED -> completedOrders++;
            case CANCELLED -> cancelledOrders++;
            case DELIVERING -> deliveringOrders++;
            default -> {
                // 其余状态（草稿/待支付/已支付/已接单）不单独统计
            }
        }

        // ---- 金额维度统计 ----
        totalDiscount = totalDiscount.add(order.discountAmount());
        totalDeliveryFee = totalDeliveryFee.add(order.deliveryFee());
        if (order.status() == OrderStatus.COMPLETED) {
            grossRevenue = grossRevenue.add(order.totalAmount());
        }
        if (order.totalAmount().compareTo(HIGH_VALUE_THRESHOLD) >= 0) {
            highValueOrders++;
        }

        // ---- 履约时长统计（只统计已完成且有完成时间的订单） ----
        if (order.status() == OrderStatus.COMPLETED && order.completedAt().isPresent()) {
            totalFulfillmentSeconds += java.time.Duration.between(
                    order.createdAt(), order.completedAt().orElseThrow()).getSeconds();
        }

        // ---- 菜品销量统计（草稿/取消的订单不计入销量榜，避免污染经营分析） ----
        if (order.status() != OrderStatus.CANCELLED && order.status() != OrderStatus.DRAFT) {
            for (var item : order.items()) {
                BigDecimal[] cell = dishSales.computeIfAbsent(item.dishName(),
                        key -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                cell[0] = cell[0].add(BigDecimal.valueOf(item.quantity()));
                cell[1] = cell[1].add(item.subtotal());
            }
        }

        // ---- 骑手维度统计 ----
        order.rider().ifPresent(rider -> riderLoads.merge(rider.name(), 1, Integer::sum));

        return null;
    }

    /**
     * 产出报表。
     *
     * @return 统计报表
     */
    public StatisticsReport toReport() {
        BigDecimal averageOrderValue = completedOrders == 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : grossRevenue.divide(BigDecimal.valueOf(completedOrders), 2, RoundingMode.HALF_UP);

        List<StatisticsReport.DishSales> dishRanking = new ArrayList<>();
        dishSales.forEach((name, cell) -> dishRanking.add(new StatisticsReport.DishSales(
                name, cell[0].intValue(), cell[1].setScale(2, RoundingMode.HALF_UP))));
        // 销量降序；销量相同时销售额高的排前面
        dishRanking.sort(Comparator.comparingInt(StatisticsReport.DishSales::quantity).reversed()
                .thenComparing(StatisticsReport.DishSales::amount, Comparator.reverseOrder()));

        List<StatisticsReport.RiderLoad> riderRanking = new ArrayList<>();
        riderLoads.forEach((name, count) -> riderRanking.add(
                new StatisticsReport.RiderLoad(name, count)));
        riderRanking.sort(Comparator.comparingInt(StatisticsReport.RiderLoad::orders).reversed());

        return new StatisticsReport(totalOrders, completedOrders, cancelledOrders, deliveringOrders,
                grossRevenue.setScale(2, RoundingMode.HALF_UP),
                totalDiscount.setScale(2, RoundingMode.HALF_UP),
                totalDeliveryFee.setScale(2, RoundingMode.HALF_UP),
                averageOrderValue,
                dishRanking.size() > 10 ? dishRanking.subList(0, 10) : dishRanking,
                riderRanking,
                highValueOrders,
                completedOrders == 0 ? 0L : totalFulfillmentSeconds / completedOrders);
    }

    /** 重置累加器（复用同一访问者对象统计另一批订单前调用）。 */
    public void reset() {
        totalOrders = 0;
        completedOrders = 0;
        cancelledOrders = 0;
        deliveringOrders = 0;
        grossRevenue = BigDecimal.ZERO;
        totalDiscount = BigDecimal.ZERO;
        totalDeliveryFee = BigDecimal.ZERO;
        highValueOrders = 0;
        totalFulfillmentSeconds = 0L;
        dishSales.clear();
        riderLoads.clear();
    }
}
