package com.example.delivery.application.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 统计报表结果（访问者模式 Visitor 的「产物」对象）。
 *
 * <p><b>为什么报表结果是不可变 record：</b>报表会被控制台、导出文件、后续的
 * 「报表本身也要被比较/缓存」等多种场景消费；不可变数据不会被某个消费者意外改坏，
 * 也让「同一份数据多处分发」变得安全。</p>
 *
 * @param totalOrders     订单总数（含所有状态）
 * @param completedOrders 已完成订单数
 * @param cancelledOrders 已取消订单数
 * @param deliveringOrders 配送中订单数
 * @param grossRevenue    已完成订单的营业额（元）
 * @param totalDiscount   累计优惠金额（元）
 * @param totalDeliveryFee 累计配送费（元）
 * @param averageOrderValue 客单价（营业额 / 已完成订单数）
 * @param dishSalesRanking 菜品销量榜（降序，最多 10 条）
 * @param riderRanking    骑手接单量榜（降序）
 * @param highValueOrders 大额订单（实付 ≥ 100 元）数量
 * @param averageFulfillmentSeconds 平均履约时长（秒，仅统计已完成订单）
 * @author 课程大作业
 */
public record StatisticsReport(
        int totalOrders,
        int completedOrders,
        int cancelledOrders,
        int deliveringOrders,
        BigDecimal grossRevenue,
        BigDecimal totalDiscount,
        BigDecimal totalDeliveryFee,
        BigDecimal averageOrderValue,
        List<DishSales> dishSalesRanking,
        List<RiderLoad> riderRanking,
        int highValueOrders,
        long averageFulfillmentSeconds) {

    /** 紧凑构造器：列表不可变。 */
    public StatisticsReport {
        dishSalesRanking = dishSalesRanking == null ? List.of() : List.copyOf(dishSalesRanking);
        riderRanking = riderRanking == null ? List.of() : List.copyOf(riderRanking);
    }

    /**
     * 菜品销量条目。
     *
     * @param dishName 菜品名
     * @param quantity 销量（份）
     * @param amount   销售额（元）
     */
    public record DishSales(String dishName, int quantity, BigDecimal amount) {
    }

    /**
     * 骑手接单量条目。
     *
     * @param riderName 骑手名
     * @param orders    接单数
     */
    public record RiderLoad(String riderName, int orders) {
    }

    /** @return 完成率（百分比，保留 1 位小数） */
    public BigDecimal completionRate() {
        if (totalOrders == 0) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(completedOrders * 100.0 / totalOrders)
                .setScale(1, RoundingMode.HALF_UP);
    }

    /** @return 取消率（百分比，保留 1 位小数） */
    public BigDecimal cancelRate() {
        if (totalOrders == 0) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(cancelledOrders * 100.0 / totalOrders)
                .setScale(1, RoundingMode.HALF_UP);
    }

    /** @return 平均履约时长（分钟，保留 1 位小数） */
    public BigDecimal averageFulfillmentMinutes() {
        return BigDecimal.valueOf(averageFulfillmentSeconds / 60.0).setScale(1, RoundingMode.HALF_UP);
    }

    /**
     * 渲染为多行报表文本（控制台与导出共用）。
     *
     * @return 报表文本
     */
    public String render() {
        StringBuilder sb = new StringBuilder();
        sb.append("总计订单：").append(totalOrders)
                .append(" 单（已完成 ").append(completedOrders)
                .append(" 单 / 配送中 ").append(deliveringOrders)
                .append(" 单 / 已取消 ").append(cancelledOrders).append(" 单）").append(System.lineSeparator());
        sb.append("完成率：").append(completionRate()).append("%    取消率：")
                .append(cancelRate()).append("%").append(System.lineSeparator());
        sb.append("营业额（已完成）：").append(grossRevenue.toPlainString()).append(" 元")
                .append("    客单价：").append(averageOrderValue.toPlainString()).append(" 元")
                .append(System.lineSeparator());
        sb.append("累计优惠：").append(totalDiscount.toPlainString()).append(" 元")
                .append("    累计配送费：").append(totalDeliveryFee.toPlainString()).append(" 元")
                .append(System.lineSeparator());
        sb.append("大额订单（≥100 元）：").append(highValueOrders).append(" 单")
                .append("    平均履约时长：").append(averageFulfillmentMinutes()).append(" 分钟")
                .append(System.lineSeparator());

        sb.append("菜品销量榜：").append(System.lineSeparator());
        if (dishSalesRanking.isEmpty()) {
            sb.append("  （暂无数据）").append(System.lineSeparator());
        } else {
            int rank = 1;
            for (DishSales sales : dishSalesRanking) {
                sb.append(String.format("  %d. %-12s %3d 份   %s 元%n",
                        rank++, sales.dishName(), sales.quantity(), sales.amount().toPlainString()));
            }
        }

        sb.append("骑手接单榜：").append(System.lineSeparator());
        if (riderRanking.isEmpty()) {
            sb.append("  （暂无数据）").append(System.lineSeparator());
        } else {
            riderRanking.forEach(load -> sb.append(String.format("  · %s：%d 单%n",
                    load.riderName(), load.orders())));
        }
        return sb.toString();
    }
}
