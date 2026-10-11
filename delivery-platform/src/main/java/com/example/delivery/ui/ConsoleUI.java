package com.example.delivery.ui;

import com.example.delivery.application.DispatchOutcome;
import com.example.delivery.application.InventoryService;
import com.example.delivery.application.OrderFacade;
import com.example.delivery.application.report.ReportService;
import com.example.delivery.application.report.StatisticsReport;
import com.example.delivery.domain.coupon.Coupon;
import com.example.delivery.domain.merchant.Dish;
import com.example.delivery.domain.merchant.Merchant;
import com.example.delivery.domain.order.Order;
import com.example.delivery.domain.order.OrderStatus;
import com.example.delivery.domain.payment.PaymentMethod;
import com.example.delivery.domain.rider.Rider;
import com.example.delivery.domain.support.DispatchStrategies;
import com.example.delivery.domain.user.User;

import java.math.BigDecimal;
import java.util.List;

/**
 * 控制台界面（命令行版的「视图层」）。
 *
 * <p><b>设计模式关联：</b></p>
 * <ul>
 *   <li><b>外观模式的使用者</b>：本类只调用 {@link OrderFacade} 的少量方法，
 *       完全不接触校验链、计价策略、解释器、适配器等子系统；</li>
 *   <li><b>中介者模式的同事（Colleague）</b>：控制台不直接指挥骑手或商家，
 *       而是把请求交给 {@code DeliveryPlatformApplication}（中介者）转发；</li>
 *   <li><b>命令模式的调用者（Invoker）</b>：菜单项本质上就是一条条命令，
 *       编号即命令标识，执行结果即命令返回值。</li>
 * </ul>
 *
 * <p><b>为什么视图层要单独一层：</b>把 {@code System.out} 与输入解析从业务里剥离后，
 * 业务逻辑可以在没有控制台的情况下被单元测试覆盖（见 test 目录）。</p>
 *
 * @author 课程大作业
 */
public class ConsoleUI {

    /** 业务门面。 */
    private final OrderFacade facade;

    /** 报表服务。 */
    private final ReportService reportService;

    /** 输入读取器。 */
    private final java.util.Scanner scanner = new java.util.Scanner(System.in);

    /**
     * @param facade        业务门面
     * @param reportService 报表服务
     */
    public ConsoleUI(OrderFacade facade, ReportService reportService) {
        this.facade = facade;
        this.reportService = reportService;
    }

    // ==================================================================
    //                            主菜单
    // ==================================================================

    /**
     * 交互式主循环。
     */
    public void runInteractive() {
        printBanner();
        boolean running = true;
        while (running) {
            printMenu();
            String choice = readLine("请选择操作", "0");
            System.out.println();
            try {
                switch (choice) {
                    case "1" -> placeOrderFlow();
                    case "2" -> listOrders();
                    case "3" -> payFlow();
                    case "4" -> dispatchFlow();
                    case "5" -> deliverFlow();
                    case "6" -> cancelFlow();
                    case "7" -> undoFlow();
                    case "8" -> statsAndArchive();
                    case "9" -> showDishMenu();
                    case "10" -> showRiders();
                    case "11" -> showCoupons();
                    case "12" -> showLastExplanation();
                    case "0" -> running = false;
                    default -> System.out.println("  无效选项：" + choice);
                }
            } catch (RuntimeException e) {
                // 业务异常统一在这里兜住，保证控制台不会因为一次失败操作而退出
                System.out.println("  [失败] 操作失败：" + e.getMessage());
            }
            System.out.println();
        }
        System.out.println("已退出外卖订单履约平台，再见！");
    }

    /** 打印欢迎横幅。 */
    private void printBanner() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════╗");
        System.out.println("║        基于设计模式实例库与模块化外卖订单履约平台               ║");
        System.out.println("║        Food Delivery Order Fulfillment Platform (v1.0)           ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════╝");
        System.out.println("  设计模式：单例/工厂方法/抽象工厂/建造者/原型/适配器/组合/装饰器/外观/");
        System.out.println("            职责链/命令/解释器/中介者/备忘录/观察者/状态/策略/模板方法/访问者");
        System.out.println();
    }

    /** 打印主菜单。 */
    private void printMenu() {
        System.out.println("────────────────────────── 主菜单 ──────────────────────────");
        System.out.println("  1. 下单          （用户下单 → 订单构建 → 校验链 → 计价策略 → 优惠券解释）");
        System.out.println("  2. 查询订单      （按订单号 / 全部 / 按状态）");
        System.out.println("  3. 支付订单      （支付适配：余额 / 微信 / 支付宝 / 银行卡）");
        System.out.println("  4. 派单          （派单中介 → 骑手调度策略，可切换策略）");
        System.out.println("  5. 配送推进      （骑手取餐 / 确认送达）");
        System.out.println("  6. 取消订单      （状态守卫 + 库存回滚 + 事件通知）");
        System.out.println("  7. 撤销上一步    （备忘录模式回滚）");
        System.out.println("  8. 统计报表与存档（访问者模式报表 + 模板方法归档）");
        System.out.println("  9. 查看商家菜单与库存");
        System.out.println(" 10. 查看骑手池");
        System.out.println(" 11. 查看优惠券规则（解释器可用的规则）");
        System.out.println(" 12. 查看最近一次计价/优惠/事件说明");
        System.out.println("  0. 退出");
        System.out.println("────────────────────────────────────────────────────────────");
    }

    // ==================================================================
    //                            业务操作
    // ==================================================================

    /**
     * 下单流程：选择用户 → 选择商家 → 加菜 → 选券 → 提交。
     */
    private void placeOrderFlow() {
        // ---------- 选择用户 ----------
        List<User> users = facade.users().findAll();
        System.out.println("【1/5 选择下单用户】");
        for (int i = 0; i < users.size(); i++) {
            System.out.printf("  %d) %s%n", i + 1, users.get(i).describe());
        }
        User user = users.get(readIndex("选择用户序号", users.size()) - 1);

        // ---------- 选择商家 ----------
        List<Merchant> merchants = facade.merchants().findAll();
        System.out.println("【2/5 选择商家】");
        for (int i = 0; i < merchants.size(); i++) {
            System.out.printf("  %d) %s%n", i + 1, merchants.get(i).describe());
        }
        Merchant merchant = merchants.get(readIndex("选择商家序号", merchants.size()) - 1);

        // ---------- 创建订单并加菜 ----------
        Order order = facade.create(user.userId(), merchant.merchantId());
        System.out.println("【3/5 选择菜品】（可连续选择，输入 0 结束）");
        while (true) {
            List<Dish> dishes = merchant.dishes();
            for (int i = 0; i < dishes.size(); i++) {
                System.out.printf("  %d) %-12s %6s 元  库存 %d%n", i + 1, dishes.get(i).name(),
                        dishes.get(i).price().toPlainString(),
                        facade.inventory().remaining(dishes.get(i).dishId()));
            }
            System.out.println("  0) 结束选菜");
            int index = readIndex("选择菜品序号", dishes.size(), true);
            if (index == 0) {
                break;
            }
            int quantity = readInt("数量", 1);
            facade.addDish(order.orderId(), dishes.get(index - 1).dishId(), quantity);
            System.out.printf("  已加入：%s × %d，当前小计 %s 元%n", dishes.get(index - 1).name(),
                    quantity, order.subtotal().toPlainString());
        }
        if (order.items().isEmpty()) {
            System.out.println("  [失败] 没有选择任何菜品，已放弃本次下单");
            return;
        }

        // ---------- 选择优惠券 ----------
        System.out.println("【4/5 选择优惠券】（直接回车表示不使用）");
        facade.couponLibrary().values().forEach(coupon ->
                System.out.printf("  · %s：%s%n", coupon.couponId(), coupon.describe()));
        String couponId = readLine("输入券编号", "").trim();
        if (couponId.isEmpty()) {
            couponId = null;
        }

        // ---------- 提交 ----------
        System.out.println("【5/5 提交订单】");
        Order submitted = facade.submit(order.orderId(), couponId);
        printOrder(submitted);
        printValidationTrace();
        System.out.println("  计价：" + facade.lastPricingExplanation());
        System.out.println();
        System.out.println("  优惠券解释过程：");
        System.out.println(facade.lastCouponExplanation().indent(4));
    }

    /**
     * 支付流程。
     */
    private void payFlow() {
        Order order = pickPendingOrder("选择待支付订单");
        if (order == null) {
            return;
        }
        System.out.println("  应付金额：" + order.totalAmount().toPlainString() + " 元");
        System.out.printf("  可用渠道：%s%n", facade.gateways().registeredChannels());
        String raw = readLine("输入支付渠道（WALLET/WECHAT_PAY/ALIPAY/BANK_CARD）", "WECHAT_PAY");
        PaymentMethod method = PaymentMethod.parse(raw);
        Order paid = facade.pay(order.orderId(), method);
        printOrder(paid);
    }

    /**
     * 派单流程（可切换调度策略）。
     */
    private void dispatchFlow() {
        List<Order> paidOrders = facade.listOrdersByStatus(OrderStatus.PAID);
        if (paidOrders.isEmpty()) {
            System.out.println("  当前没有已支付待派单的订单");
            return;
        }
        Order order = paidOrders.get(readIndex("选择待派单订单序号", paidOrders.size()) - 1);

        System.out.println("  当前调度策略：" + facade.dispatchCenter().preferredStrategy().name());
        System.out.println("  1) 最近优先    2) 最少负载优先    3) 评分最高优先");
        int strategyChoice = readIndex("选择调度策略序号", 3);
        switch (strategyChoice) {
            case 1 -> facade.useDispatchStrategy(DispatchStrategies.nearest());
            case 2 -> facade.useDispatchStrategy(DispatchStrategies.leastLoaded());
            default -> facade.useDispatchStrategy(DispatchStrategies.highestRated());
        }

        DispatchOutcome outcome = facade.dispatch(order.orderId());
        System.out.println("  ── 派单轨迹 ──");
        outcome.trace().forEach(line -> System.out.println("    · " + line));
        System.out.println("  " + outcome.describe());
        facade.findOrder(order.orderId()).ifPresent(this::printOrder);
    }

    /**
     * 配送推进：取餐 → 送达。
     */
    private void deliverFlow() {
        List<Order> candidates = new java.util.ArrayList<>();
        candidates.addAll(facade.listOrdersByStatus(OrderStatus.ASSIGNED));
        candidates.addAll(facade.listOrdersByStatus(OrderStatus.DELIVERING));
        if (candidates.isEmpty()) {
            System.out.println("  当前没有待配送的订单");
            return;
        }
        Order order = candidates.get(readIndex("选择订单序号", candidates.size()) - 1);
        System.out.println("  1) 骑手取餐（已接单 → 配送中）   2) 确认送达（配送中 → 已完成）");
        int action = readIndex("选择操作", 2);
        Order updated = action == 1 ? facade.startDelivery(order.orderId()) : facade.complete(order.orderId());
        printOrder(updated);
    }

    /**
     * 取消流程。
     */
    private void cancelFlow() {
        List<Order> cancellable = facade.listOrders().stream()
                .filter(order -> order.status().cancellable())
                .toList();
        if (cancellable.isEmpty()) {
            System.out.println("  当前没有可取消的订单（配送中与终态订单不可取消）");
            return;
        }
        Order order = cancellable.get(readIndex("选择要取消的订单序号", cancellable.size()) - 1);
        String reason = readLine("取消原因", "用户主动取消");
        Order cancelled = facade.cancel(order.orderId(), reason);
        printOrder(cancelled);
        System.out.println("  库存回滚后：" + facade.inventory().snapshotText());
    }

    /**
     * 撤销上一步。
     */
    private void undoFlow() {
        Order order = pickAnyOrder("选择要撤销的订单");
        if (order == null) {
            return;
        }
        List<String> history = facade.memoManager().historyOf(order.orderId());
        if (history.isEmpty()) {
            System.out.println("  该订单没有历史快照（快照在支付/取餐/送达/取消等关键操作前自动记录）");
            return;
        }
        System.out.println("  历史快照：");
        history.forEach(line -> System.out.println("    · " + line));
        System.out.println("  " + facade.undo(order.orderId()));
        facade.findOrder(order.orderId()).ifPresent(this::printOrder);
    }

    /**
     * 统计报表与订单存档。
     */
    private void statsAndArchive() {
        StatisticsReport report = reportService.generate();
        System.out.println(report.render());
        System.out.println(reportService.reconcile());

        int archived = facade.archiveOrders();
        System.out.println("  本次归档订单数：" + archived);
    }

    // ==================================================================
    //                            查询展示
    // ==================================================================

    /** 展示全部订单或按条件查询。 */
    private void listOrders() {
        System.out.println("  1) 全部订单   2) 按订单号   3) 按状态   4) 按用户");
        int choice = readIndex("选择查询方式", 4);
        List<Order> orders;
        switch (choice) {
            case 2 -> {
                String orderId = readLine("输入订单号", "");
                Order order = facade.findOrder(orderId)
                        .orElseThrow(() -> new IllegalArgumentException("订单不存在：" + orderId));
                printOrderDetail(order);
                return;
            }
            case 3 -> {
                System.out.println("  状态可选：" + java.util.Arrays.toString(OrderStatus.values()));
                String raw = readLine("输入状态名", "PAID");
                orders = facade.listOrdersByStatus(OrderStatus.valueOf(raw.trim().toUpperCase()));
            }
            case 4 -> {
                String userId = readLine("输入用户编号", "U-1001");
                orders = facade.listOrdersOfUser(userId);
            }
            default -> orders = facade.listOrders();
        }
        if (orders.isEmpty()) {
            System.out.println("  没有符合条件的订单");
            return;
        }
        orders.forEach(this::printOrder);
    }

    /** 打印商家菜单与实时库存。 */
    private void showDishMenu() {
        System.out.println("  库存服务为单例（InventoryService.getInstance()），与菜单定义分离：");
        for (Merchant merchant : facade.merchants().findAll()) {
            System.out.println("  ■ " + merchant.describe());
            for (Dish dish : merchant.dishes()) {
                System.out.printf("      %s  库存 %d%n", dish.describe(),
                        facade.inventory().remaining(dish.dishId()));
            }
        }
    }

    /** 打印骑手池。 */
    private void showRiders() {
        System.out.println("  骑手池（record 不可变，接单/送达产生新对象并回写仓储）：");
        facade.riders().findAll().forEach(rider -> System.out.printf("    · %s  可接单=%s%n",
                rider.describe(), rider.canAccept()));
    }

    /** 打印优惠券库与可用规则。 */
    private void showCoupons() {
        System.out.println("  券库（规则表达式由解释器模式解析）：");
        facade.couponLibrary().values().forEach(coupon ->
                System.out.printf("    · %s：%s%n", coupon.couponId(), coupon.describe()));
        System.out.println("  解释器内置示例规则：");
        com.example.delivery.infrastructure.coupon.CouponRuleInterpreter.sampleRules()
                .forEach(rule -> System.out.println("    · " + rule));
    }

    /** 打印最近一次计价、优惠与校验轨迹。 */
    private void showLastExplanation() {
        printValidationTrace();
        System.out.println("  计价说明：" + facade.lastPricingExplanation());
        System.out.println("  优惠说明：");
        System.out.println(facade.lastCouponExplanation().indent(4));
        System.out.println("  事件流水（最近 8 条）：");
        List<com.example.delivery.domain.support.OrderEvent> events = facade.publisher().publishedEvents();
        int from = Math.max(0, events.size() - 8);
        events.subList(from, events.size()).forEach(event -> System.out.println("    · " + event.describe()));
    }

    /** 打印最近一次下单的校验链轨迹。 */
    private void printValidationTrace() {
        List<String> trace = facade.lastValidationTrace();
        if (trace.isEmpty()) {
            return;
        }
        System.out.println("  ── 校验链轨迹（职责链模式）──");
        trace.forEach(line -> System.out.println("    " + line));
    }

    // ==================================================================
    //                            打印工具
    // ==================================================================

    /**
     * 单行打印订单。
     *
     * @param order 订单
     */
    public void printOrder(Order order) {
        System.out.printf("  %s 状态=%-6s 实付=%-8s 骑手=%-8s 明细=%d 行%n",
                order.orderId(), order.status().label(),
                order.totalAmount().toPlainString() + " 元",
                order.rider().map(Rider::name).orElse("未派单"),
                order.items().size());
    }

    /**
     * 详细打印订单（含明细、金额构成、时间线）。
     *
     * @param order 订单
     */
    public void printOrderDetail(Order order) {
        System.out.println("  ┌── 订单详情 " + order.orderId() + " ──────────────────");
        System.out.println("  │ 用户：" + order.customer().describe());
        System.out.println("  │ 商家：" + order.merchant().name());
        System.out.println("  │ 地址：" + order.deliveryAddress().orElse("-"));
        System.out.println("  │ 备注：" + order.remark().orElse("-"));
        System.out.println("  │ 状态：" + order.status().label()
                + "（允许的操作：" + com.example.delivery.domain.order.OrderState.of(order).allowedOperations() + "）");
        order.items().forEach(item -> System.out.println("  │   · " + item.describe()));
        System.out.printf("  │ 小计=%s  优惠=%s  配送费=%s  实付=%s 元%n",
                order.subtotal().toPlainString(), order.discountAmount().toPlainString(),
                order.deliveryFee().toPlainString(), order.totalAmount().toPlainString());
        System.out.println("  │ 支付：" + order.paymentMethod().orElse("-")
                + " / " + order.paymentTradeNo().orElse("-"));
        System.out.println("  │ 骑手：" + order.rider().map(Rider::describe).orElse("未派单"));
        System.out.println("  │ 创建：" + order.createdAt());
        order.paidAt().ifPresent(at -> System.out.println("  │ 支付：" + at));
        order.assignedAt().ifPresent(at -> System.out.println("  │ 接单：" + at));
        order.completedAt().ifPresent(at -> System.out.println("  │ 完成：" + at));
        order.cancelledAt().ifPresent(at -> System.out.println("  │ 取消：" + at
                + "（" + order.cancelReason().orElse("-") + "）"));
        System.out.println("  └──────────────────────────────────────────────");
    }

    // ==================================================================
    //                            输入工具
    // ==================================================================

    /**
     * 读取一行输入。
     *
     * @param prompt       提示语
     * @param defaultValue 默认值（直接回车时使用）
     * @return 输入内容
     */
    private String readLine(String prompt, String defaultValue) {
        System.out.print("  " + prompt + "[" + defaultValue + "]: ");
        System.out.flush();
        if (!scanner.hasNextLine()) {
            return defaultValue;
        }
        String line = scanner.nextLine().trim();
        return line.isEmpty() ? defaultValue : line;
    }

    /**
     * 读取一个 1..max 的序号（非法输入自动取默认值 1）。
     *
     * @param prompt 提示语
     * @param max    最大值
     * @return 序号（1 起始）
     */
    private int readIndex(String prompt, int max) {
        return readIndex(prompt, max, false);
    }

    /**
     * 读取序号。
     *
     * @param prompt      提示语
     * @param max         最大值
     * @param allowZero   是否允许 0（表示结束）
     * @return 序号
     */
    private int readIndex(String prompt, int max, boolean allowZero) {
        int min = allowZero ? 0 : 1;
        for (int attempt = 0; attempt < 3; attempt++) {
            String raw = readLine(prompt + "（" + min + "-" + max + "）", String.valueOf(min));
            try {
                int value = Integer.parseInt(raw);
                if (value >= min && value <= max) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
                // 落到下面统一提示
            }
            System.out.println("  输入无效，请重新输入");
        }
        System.out.println("  连续输入无效，使用默认值 " + min);
        return min;
    }

    /**
     * 读取整数。
     *
     * @param prompt       提示语
     * @param defaultValue 默认值
     * @return 整数
     */
    private int readInt(String prompt, int defaultValue) {
        String raw = readLine(prompt, String.valueOf(defaultValue));
        try {
            return Math.max(1, Integer.parseInt(raw));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 从待支付订单中选择一张。
     *
     * @param title 选择提示标题
     * @return 选中的订单；没有待支付订单时返回 null
     */
    private Order pickPendingOrder(String title) {
        List<Order> orders = facade.listOrdersByStatus(OrderStatus.PENDING_PAYMENT);
        if (orders.isEmpty()) {
            System.out.println("  当前没有待支付订单，请先下单");
            return null;
        }
        System.out.println("  " + title + "：");
        for (int i = 0; i < orders.size(); i++) {
            System.out.printf("    %d) %s 实付 %s 元%n", i + 1, orders.get(i).orderId(),
                    orders.get(i).totalAmount().toPlainString());
        }
        return orders.get(readIndex("选择订单序号", orders.size()) - 1);
    }

    /**
     * 从全部订单中选择一张。
     *
     * @param title 选择提示标题
     * @return 选中的订单；没有订单时返回 null
     */
    private Order pickAnyOrder(String title) {
        List<Order> orders = facade.listOrders();
        if (orders.isEmpty()) {
            System.out.println("  当前没有任何订单");
            return null;
        }
        System.out.println("  " + title + "：");
        for (int i = 0; i < orders.size(); i++) {
            System.out.printf("    %d) %s%n", i + 1, orders.get(i).toString());
        }
        return orders.get(readIndex("选择订单序号", orders.size()) - 1);
    }

    // ==================================================================
    //                     演示数据展示（启动时打印）
    // ==================================================================

    /**
     * 打印平台当前的基础数据，便于确认演示环境。
     */
    public void printPlatformOverview() {
        System.out.println("── 平台基础数据 ────────────────────────────────────────");
        facade.users().findAll().forEach(user -> System.out.println("  用户  " + user.describe()));
        facade.merchants().findAll().forEach(merchant -> System.out.println("  商家  " + merchant.describe()));
        facade.riders().findAll().forEach(rider -> System.out.println("  骑手  " + rider.describe()));
        facade.couponLibrary().values().forEach(coupon -> System.out.println("  优惠券 " + coupon.describe()));
        System.out.println("  库存  " + facade.inventory().snapshotText());
        System.out.println("  支付渠道  " + facade.gateways().registeredChannels());
        System.out.println("  事件订阅者 " + facade.publisher().listenerNames());
        System.out.println("───────────────────────────────────────────────────────");
        System.out.println();
    }
}
