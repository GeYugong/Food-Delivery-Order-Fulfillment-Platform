package com.example.delivery.bootstrap;

import com.example.delivery.application.DispatchOutcome;
import com.example.delivery.domain.order.Order;
import com.example.delivery.domain.order.OrderStatus;
import com.example.delivery.domain.payment.PaymentMethod;
import com.example.delivery.domain.rider.Rider;
import com.example.delivery.domain.support.DispatchStrategies;
import com.example.delivery.infrastructure.coupon.CouponRuleInterpreter;

import java.math.BigDecimal;

/**
 * 端到端业务闭环演示脚本（一键跑完整流程）。
 *
 * <p><b>为什么需要它：</b>交互式控制台适合自己点着玩，但课堂演示 / 答辩 / 验收
 * 需要一条「稳定可复现、不用手工输入」的主线。本类就是这条主线：
 * 用固定脚本把 12 个履约环节全部跑一遍，并额外演示
 * <b>校验拦截、支付失败、无骑手可派、非法状态流转、库存回滚、撤销</b>等异常场景。</p>
 *
 * <p><b>脚本覆盖的业务闭环：</b></p>
 * <pre>
 *   用户下单 → 订单构建 → 校验链 → 计价策略 → 优惠券解释 → 支付适配
 *   → 状态流转 → 事件通知 → 派单中介 → 骑手调度 → 订单存档 → 统计报表
 * </pre>
 *
 * <p><b>涉及的设计模式：</b>外观（只调门面）、职责链（校验轨迹）、策略（计价/调度切换）、
 * 解释器（优惠券 AST）、适配器（多渠道支付）、中介者（派单中心）、观察者（事件流水）、
 * 状态（非法流转被拒）、备忘录（撤销）、模板方法（归档）、访问者（报表）、
 * 单例（仓储/库存/注册表）、建造者（订单与门面装配）、原型（再来一单）、
 * 工厂方法/抽象工厂（数据装配）。</p>
 *
 * @author 课程大作业
 */
public final class DemoRunner {

    /** 工具类不允许实例化。 */
    private DemoRunner() {
    }

    /**
     * 执行完整演示。
     *
     * @param platform 已装配的平台
     */
    public static void run(PlatformBootstrap.Platform platform) {
        var facade = platform.facade();
        step("0", "平台装配（抽象工厂 + 单例 + 建造者）");
        System.out.println("  事件订阅者：" + facade.publisher().listenerNames());
        System.out.println("  支付渠道：" + facade.gateways().registeredChannels());
        System.out.println("  库存：" + facade.inventory().snapshotText());

        // ============================================================
        step("1", "用户下单 + 订单构建（建造者模式）");
        Order order1 = facade.create("U-1001", "M-2001");
        System.out.println("  创建草稿订单：" + order1.orderId() + "，状态=" + order1.status().label());
        facade.addDish(order1.orderId(), "DISH-003", 1);
        System.out.println("  只加了一份可乐（6.50 元），当前小计：" + order1.subtotal().toPlainString() + " 元");

        // ============================================================
        step("2", "校验链（职责链模式）—— 先演示被拦截");
        try {
            facade.submit(order1.orderId(), null);
        } catch (RuntimeException e) {
            System.out.println("  [失败] 被校验链拦下：" + e.getMessage());
        }
        System.out.println("  ── 校验轨迹 ──");
        facade.lastValidationTrace().forEach(line -> System.out.println("    " + line));

        System.out.println();
        System.out.println("  继续加菜后重新提交：");
        facade.addDish(order1.orderId(), "DISH-001", 2);
        facade.addDish(order1.orderId(), "DISH-002", 1);
        order1.items().forEach(item -> System.out.println("    · " + item.describe()));
        System.out.println("  当前小计：" + order1.subtotal().toPlainString() + " 元");

        // ============================================================
        step("3", "计价策略 + 优惠券解释（策略模式 + 解释器模式）");
        Order submitted = facade.submit(order1.orderId(), "C-9001");
        System.out.println("  " + facade.lastPricingExplanation());
        System.out.println("  优惠券解释：");
        System.out.println(facade.lastCouponExplanation().indent(4));
        System.out.println("  → 小计 " + submitted.subtotal().toPlainString()
                + " 元，优惠 " + submitted.discountAmount().toPlainString()
                + " 元，配送费 " + submitted.deliveryFee().toPlainString()
                + " 元，实付 " + submitted.totalAmount().toPlainString() + " 元");

        // ============================================================
        step("4", "支付适配（适配器模式）—— 先演示余额不足失败");
        // 换一位余额较少的用户（U-1002 余额 50 元）来演示「余额不足」这条真实失败路径，
        // 因为第一单的用户 U-1001 余额充足，余额支付必然成功（不能拿它演失败）。
        Order lowBalanceOrder = facade.create("U-1002", "M-2001");
        facade.addDish(lowBalanceOrder.orderId(), "DISH-003", 6);
        facade.addDish(lowBalanceOrder.orderId(), "DISH-002", 3);
        Order lowBalanceSubmitted = facade.submit(lowBalanceOrder.orderId(), null);
        System.out.println("  用余额仅 50.00 元的用户 U-1002 下一笔 "
                + lowBalanceSubmitted.totalAmount().toPlainString() + " 元的订单");
        try {
            facade.pay(lowBalanceOrder.orderId(), PaymentMethod.WALLET);
        } catch (RuntimeException e) {
            System.out.println("  [失败] 支付失败（订单仍停留在待支付，可换渠道重试）：" + e.getMessage());
        }
        System.out.println("  该订单状态仍为：" + facade.getOrder(lowBalanceOrder.orderId()).status().label());
        Order lowBalancePaid = facade.pay(lowBalanceOrder.orderId(), PaymentMethod.ALIPAY);
        System.out.println("  [成功] 换支付宝支付成功，状态=" + lowBalancePaid.status().label()
                + "，流水号=" + lowBalancePaid.paymentTradeNo().orElse("-"));
        facade.cancel(lowBalancePaid.orderId(), "演示完成，主动取消该笔订单");

        System.out.println();
        System.out.println("  回到第一单（U-1001，余额充足）改用微信支付：");
        Order paid1 = facade.pay(order1.orderId(), PaymentMethod.WECHAT_PAY);
        System.out.println("  [成功] 微信支付成功，状态=" + paid1.status().label()
                + "，流水号=" + paid1.paymentTradeNo().orElse("-"));

        // ============================================================
        step("5", "状态流转 + 事件通知 + 派单中介 + 骑手调度（状态/观察者/中介者/策略）");
        System.out.println("  可用骑手：" + facade.riders().findAvailable().stream()
                .map(Rider::describe).toList());
        System.out.println("  当前调度策略：" + facade.dispatchCenter().preferredStrategy().name());
        DispatchOutcome outcome1 = facade.dispatch(order1.orderId());
        outcome1.trace().forEach(line -> System.out.println("    · " + line));
        System.out.println("  " + outcome1.describe());

        // ============================================================
        step("6", "配送推进 + 备忘录撤销 + 重新推进");
        Order delivering = facade.startDelivery(order1.orderId());
        System.out.println("  骑手取餐后状态：" + delivering.status().label() + "（骑手已在路上）");
        System.out.println("  执行撤销（备忘录模式）：");
        System.out.println("    " + facade.undo(order1.orderId()));
        System.out.println("  撤销后状态：" + facade.getOrder(order1.orderId()).status().label()
                + "（回退到取餐之前，需要重新推进）");
        Order deliveringAgain = facade.startDelivery(order1.orderId());
        System.out.println("  重新取餐后状态：" + deliveringAgain.status().label());
        Order completed = facade.complete(order1.orderId());
        System.out.println("  确认送达后状态：" + completed.status().label()
                + "，骑手负载已释放：" + facade.riders().findById(completed.rider()
                        .map(Rider::riderId).orElse("R-3001")).map(Rider::describe).orElse("-"));

        // ============================================================
        step("7", "非法状态流转防护（状态模式）");
        try {
            facade.cancel(order1.orderId(), "已完成还想取消");
        } catch (RuntimeException e) {
            System.out.println("  [失败] 已按预期拦截：" + e.getMessage());
        }
        System.out.println("  状态仍为：" + facade.getOrder(order1.orderId()).status().label());

        // ============================================================
        step("8", "第二单：调度策略切换 + 支付宝适配");
        Order order2 = facade.create("U-1002", "M-2002");
        facade.addDish(order2.orderId(), "DISH-101", 1);
        facade.addDish(order2.orderId(), "DISH-103", 2);
        Order submitted2 = facade.submit(order2.orderId(), "C-9003");
        System.out.println("  使用 9 折券（解释器）：优惠 "
                + submitted2.discountAmount().toPlainString() + " 元，实付 "
                + submitted2.totalAmount().toPlainString() + " 元");
        Order paid2 = facade.pay(order2.orderId(), PaymentMethod.ALIPAY);
        System.out.println("  支付宝支付成功，状态=" + paid2.status().label());

        // 把骑手池清空，演示「无骑手可派」以及失败事件
        var riders = facade.riders().findAll();
        riders.forEach(rider -> facade.riders().save(new Rider(rider.riderId(), rider.name(),
                rider.phone(), Rider.RiderStatus.RESTING, 0, rider.maxLoad(),
                rider.currentDistanceKm(), rider.rating())));
        System.out.println("  已把所有骑手置为「休息」，模拟运力枯竭：");
        DispatchOutcome failed = facade.dispatch(order2.orderId());
        System.out.println("  " + failed.describe());
        failed.trace().forEach(line -> System.out.println("    · " + line));

        // 恢复运力并切换策略重试
        facade.riders().save(Rider.idle("R-3001", "李骑手", "13900001111", "1.20", "4.90"));
        facade.riders().save(Rider.idle("R-3002", "赵骑手", "13900002222", "0.80", "4.70"));
        facade.useDispatchStrategy(DispatchStrategies.leastLoaded());
        System.out.println("  恢复运力并切换策略为：" + facade.dispatchCenter().preferredStrategy().name());
        DispatchOutcome retry = facade.dispatch(order2.orderId());
        System.out.println("  " + retry.describe());
        Order done2 = facade.complete(facade.startDelivery(order2.orderId()).orderId());
        System.out.println("  第二单完成：" + done2.orderId() + " 状态=" + done2.status().label());

        // ============================================================
        step("9", "第三单：解释器规则组合 + 取消与库存回滚（备忘录/观察者）");
        Order order3 = facade.create("U-1003", "M-2001");
        facade.addDish(order3.orderId(), "DISH-004", 2);
        System.out.println("  钻石会员（U-1003）小计：" + facade.getOrder(order3.orderId()).subtotal().toPlainString() + " 元");
        CouponRuleInterpreter interpreter = platform.interpreter();
        for (String rule : CouponRuleInterpreter.sampleRules()) {
            BigDecimal discount = interpreter.evaluateForTest(rule,
                    facade.getOrder(order3.orderId()).subtotal(), "DIAMOND");
            System.out.printf("    规则「%s」→ 钻石会员可抵扣 %s 元%n", rule, discount.toPlainString());
        }
        Order submitted3 = facade.submit(order3.orderId(), "C-9005");
        System.out.println("  订单使用「满50减10 或 会员等级==2」→ 抵扣 "
                + submitted3.discountAmount().toPlainString() + " 元");
        Order paid3 = facade.pay(order3.orderId(), PaymentMethod.BANK_CARD);
        System.out.println("  银行卡支付成功：" + paid3.paymentTradeNo().orElse("-"));
        System.out.println("  取消前库存：" + facade.inventory().snapshotText());
        Order cancelled = facade.cancel(order3.orderId(), "用户临时有事，申请取消");
        System.out.println("  取消后状态：" + cancelled.status().label()
                + "，原因=" + cancelled.cancelReason().orElse("-"));
        System.out.println("  取消后库存（已回滚）：" + facade.inventory().snapshotText());

        // ============================================================
        step("10", "原型模式：再来一单");
        Order reorder = facade.getOrder(order1.orderId()).copyAsDraft("ORD-REORDER-01");
        facade.publisher().publishFor(reorder, com.example.delivery.domain.support.OrderEvent.ORDER_SUBMITTED,
                "由已完成的 " + order1.orderId() + " 复制而来");
        System.out.println("  新草稿订单：" + reorder.orderId() + "，复用明细 "
                + reorder.items().size() + " 行，状态=" + reorder.status().label()
                + "，原订单状态=" + facade.getOrder(order1.orderId()).status().label());

        // ============================================================
        step("11", "订单存档（模板方法模式 + 原型/备忘录）");
        int archived = facade.archiveOrders();
        System.out.println("  已归档订单数：" + archived
                + "，存档快照份数：" + platform.orderStore().archivedOrderCount());
        platform.orderStore().archiveLog().forEach(line -> System.out.println("    · " + line));

        // ============================================================
        step("12", "统计报表（访问者模式）+ 支付对账");
        System.out.println(platform.reportService().generate().render().indent(2));
        System.out.println("  " + platform.reportService().reconcile());
        System.out.println("  事件流水统计：" + platform.reportListener().summary());
        System.out.println("  用户短信共 " + platform.smsListener().sentMessages().size() + " 条，例如：");
        platform.smsListener().sentMessages().stream().limit(3)
                .forEach(message -> System.out.println("    · " + message));
        System.out.println("  审计日志共 " + platform.auditListener().logs().size() + " 条");

        // ============================================================
        step("13", "状态迁移矩阵总览（状态模式）");
        for (OrderStatus status : OrderStatus.values()) {
            System.out.printf("  %-6s 可取消防:%s 终态:%s 下一状态=%s%n",
                    status.label(), status.cancellable(), status.isFinalState(), status.nextStatuses());
        }

        System.out.println();
        System.out.println("=".repeat(72));
        System.out.println("  端到端演示完成：12 个履约环节全部跑通，含 5 类异常场景验证");
        System.out.println("=".repeat(72));
    }

    /**
     * 打印步骤标题。
     *
     * @param index 步骤序号
     * @param title 标题
     */
    private static void step(String index, String title) {
        System.out.println();
        System.out.println("═".repeat(72));
        System.out.println("  步骤 " + index + "｜" + title);
        System.out.println("═".repeat(72));
    }
}
