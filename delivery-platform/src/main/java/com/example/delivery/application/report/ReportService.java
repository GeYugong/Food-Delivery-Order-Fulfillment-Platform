package com.example.delivery.application.report;

import com.example.delivery.domain.order.Order;
import com.example.delivery.domain.order.OrderVisitor;
import com.example.delivery.domain.payment.Payment;

import java.math.BigDecimal;
import java.util.List;

/**
 * 报表服务（访问者模式的「客户端」：驱动一批订单接受访问者）。
 *
 * <p><b>设计模式：访问者模式（Visitor）+ 外观模式（Facade）</b></p>
 *
 * <p><b>职责：</b>把「取数据 → 遍历订单 → 由访问者累加 → 产出报表」这段流程封装起来。
 * 控制台、导出功能、测试都只调用 {@link #generate()}，不需要知道访问者的存在。</p>
 *
 * <p>本类也顺带演示访问者模式的两种典型访问者可以并存：
 * 一个产出汇总报表（{@link ReportVisitor}），一个产出明细文本（下面的内部类）。</p>
 *
 * @author 课程大作业
 */
public class ReportService {

    /** 订单数据来源。 */
    private final java.util.function.Supplier<List<Order>> orderSource;

    /** 支付流水数据来源。 */
    private final java.util.function.Supplier<List<Payment>> paymentSource;

    /**
     * @param orderSource   订单列表提供者
     * @param paymentSource 支付流水提供者
     */
    public ReportService(java.util.function.Supplier<List<Order>> orderSource,
                         java.util.function.Supplier<List<Payment>> paymentSource) {
        this.orderSource = orderSource;
        this.paymentSource = paymentSource;
    }

    /**
     * 生成统计报表。
     *
     * @return 统计报表
     */
    public StatisticsReport generate() {
        ReportVisitor visitor = new ReportVisitor();
        for (Order order : orderSource.get()) {
            // 双分派：order 决定「访问哪个元素」，visitor 决定「执行哪个操作」
            order.accept(visitor);
        }
        return visitor.toReport();
    }

    /**
     * 用访问者导出订单明细文本（演示「同一批数据、不同访问者、不同产出」）。
     *
     * @return 明细行列表
     */
    public List<String> exportDetails() {
        OrderVisitor<String> exportVisitor = order -> String.format("%s|%s|%s|%s|%s",
                order.orderId(),
                order.status().name(),
                order.subtotal().toPlainString(),
                order.totalAmount().toPlainString(),
                order.rider().map(rider -> rider.name()).orElse(""));
        return orderSource.get().stream().map(order -> order.accept(exportVisitor)).toList();
    }

    /**
     * 对账摘要：把支付流水与订单金额做一次简单核对。
     *
     * <p>真实系统这里是独立的对账任务；放在报表服务里只是为了演示
     * 「支付适配器产出的统一 {@link Payment} 记录」的价值——因为四个渠道
     * 都被适配成了同一种记录，对账代码完全不需要区分渠道。</p>
     *
     * @return 对账文本
     */
    public String reconcile() {
        List<Payment> payments = paymentSource.get();
        long successCount = payments.stream().filter(Payment::succeeded).count();
        long failedCount = payments.size() - successCount;
        BigDecimal successAmount = payments.stream()
                .filter(Payment::succeeded)
                .map(Payment::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return String.format("支付流水对账：共 %d 笔（成功 %d 笔 / 失败 %d 笔），成功金额合计 %s 元",
                payments.size(), successCount, failedCount, successAmount.toPlainString());
    }
}
