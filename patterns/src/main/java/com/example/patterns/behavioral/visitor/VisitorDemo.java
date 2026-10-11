package com.example.patterns.behavioral.visitor;

import com.example.patterns.PatternDemo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 访问者模式（Visitor）演示：统计报表 / 数据导出 / 风控审计。
 *
 * <p><b>用了什么模式：</b>访问者模式。把「作用在订单对象结构上的各种操作」从订单类里抽出来，独立成访问者类
 * （{@link OrderVisitor} 及其四个实现）。元素侧只保留一个 {@code accept(OrderVisitor)} 作为双分派入口：
 * 元素把自己当参数回传给访问者，从而让重载的 {@code visit(...)} 在运行期被精确选中。</p>
 *
 * <p><b>为什么用 / 不用会怎样：</b>订单对象结构稳定（普通单、拼单、退款单三种），但操作在频繁增加：
 * 营业额报表、菜品销量榜、CSV 导出、风控审计。若把操作写进订单类，{@code NormalOrder} 会被统计逻辑、
 * 导出格式、风控规则污染，每加一个报表都要改动并重新测试稳定的领域模型。</p>
 *
 * <p><b>本模式的代价（典型取舍，答辩必答）：</b>访问者把「新增操作」变便宜了，却把「新增元素类型」变贵了
 * ——一旦增加第四种订单（如「预约单」），{@link OrderVisitor} 就要加一个 {@code visit(ReservationOrder)}
 * 重载，<b>所有已存在的访问者实现都会被编译器强制要求改动</b>，故它只适合「数据结构稳定、操作频繁变化」
 * 的场景；元素类型本身经常变时，应改为把行为留在元素内或使用策略模式。</p>
 *
 * @author 课程大作业
 */
public class VisitorDemo implements PatternDemo {

    /** public 无参构造器：便于 PatternShowcase 通过反射创建实例。 */
    public VisitorDemo() {
        // 无状态，每次 run() 内重新构造元素集合与访问者。
    }

    /** 独立运行入口，与菜单入口走同一个 run()。 */
    public static void main(String[] args) {
        new VisitorDemo().run();
    }

    @Override
    public String name() {
        return "访问者模式";
    }

    @Override
    public String code() {
        return "17";
    }

    @Override
    public String category() {
        return "行为型";
    }

    @Override
    public String description() {
        return "订单数据统计与风控：营业额、菜品销量、CSV 导出、风控审计四类操作作用于同一批订单";
    }

    @Override
    public void run() {
        System.out.println("==================================================");
        System.out.println("  " + name() + "（Behavioral / Visitor）  编号 " + code());
        System.out.println("  场景：" + description());
        System.out.println("==================================================");

        System.out.println();
        System.out.println("【参与角色】");
        System.out.println("  Visitor        : OrderVisitor（三个重载 visit 方法，靠双分派选中）");
        System.out.println("  ConcreteVisitor: RevenueReportVisitor / PopularDishVisitor /"
                + " CsvExportVisitor / RiskAuditVisitor");
        System.out.println("  Element        : OrderElement（accept(OrderVisitor) 为双分派入口）");
        System.out.println("  ConcreteElement: NormalOrder（普通订单）/ GroupOrder（拼单）/ RefundOrder（退款单）");
        System.out.println("  ObjectStructure: 本方法构造的 orders 列表（3 种类型混合共 6 个元素）");

        List<OrderElement> orders = mockOrders();
        System.out.println();
        System.out.println("  对象结构（同一批元素，全程不做任何修改）：");
        for (OrderElement element : orders) {
            System.out.println("    " + element);
        }

        System.out.println();
        System.out.println("【调用过程：对同一批元素依次跑 4 个访问者】");

        // 4 个访问者共用同一段遍历代码：差异只来自传入的访问者对象。
        runVisitor(orders, "RevenueReportVisitor（营业额与订单数统计）", new RevenueReportVisitor());
        runVisitor(orders, "PopularDishVisitor（菜品销量榜，仅普通单与拼单）", new PopularDishVisitor());
        runVisitor(orders, "CsvExportVisitor（按订单类型输出不同格式的行）", new CsvExportVisitor());
        runVisitor(orders, "RiskAuditVisitor（大额订单与退款异常告警）", new RiskAuditVisitor());

        System.out.println();
        System.out.println("  [观察] 4 次遍历用的是完全相同的 orders 列表与完全相同的 accept()，差异只来自传入的访问者对象：");
        System.out.println("    第 1 次分派 element.accept(visitor) 选中元素类，第 2 次分派 visitor.visit(this) 命中重载方法。");

        System.out.println();
        System.out.println("【结论】");
        System.out.println("  1. 价值：操作外置到访问者，订单模型保持干净；新增报表 = 新增一个访问者类，");
        System.out.println("     元素类（NormalOrder / GroupOrder / RefundOrder）一行都不用改，符合开闭原则；");
        System.out.println("  2. 双分派 + 可累积状态：访问者在多次 visit 中累计结果，避免 instanceof 与强制类型转换；");
        System.out.println("  3. 代价：新增一种订单类型要改动所有访问者实现，故只适合结构稳定、操作多变的场景；");
        System.out.println("  4. 与策略模式的区别：策略替换的是「一个算法的多种实现」，访问者扩展的是「多种操作」。");
        System.out.println("==================================================");
    }

    /** 复用同一段遍历 + 打印逻辑：元素只暴露 accept()，操作细节全部留在访问者里。 */
    private void runVisitor(List<OrderElement> orders, String visitorName, OrderVisitor visitor) {
        System.out.println();
        System.out.println("  >> " + visitorName);
        orders.forEach(element -> element.accept(visitor));
        for (String line : visitor.report()) {
            System.out.println("      → " + line);
        }
    }

    /** 构造 3 种类型混合的订单元素集合，保证每个访问者都被覆盖到。 */
    private List<OrderElement> mockOrders() {
        List<OrderElement> orders = new ArrayList<>();
        orders.add(new NormalOrder("FD2024001", new BigDecimal("68.00"), "宫保鸡丁 x1,米饭 x2,可乐 x1"));
        orders.add(new GroupOrder("FD2024002", new BigDecimal("126.00"), 4));
        orders.add(new RefundOrder("FD2024003", new BigDecimal("39.90"), new BigDecimal("39.90")));
        orders.add(new NormalOrder("FD2024004", new BigDecimal("103.20"), "水煮牛肉 x1,米饭 x1,酸梅汤 x1"));
        orders.add(new RefundOrder("FD2024005", new BigDecimal("88.00"), new BigDecimal("35.20")));
        orders.add(new GroupOrder("FD2024006", new BigDecimal("72.50"), 2));
        return orders;
    }
}

/** 元素接口：只暴露双分派入口，不暴露任何业务操作。 */
interface OrderElement {

    /** 双分派第一跳：元素把自己交给访问者，由访问者决定调用哪个重载。 */
    void accept(OrderVisitor visitor);
}

/** 具体元素一：普通订单（单人多菜）。 */
class NormalOrder implements OrderElement {

    private final String orderId;
    private final BigDecimal amount;
    private final String items;

    NormalOrder(String orderId, BigDecimal amount, String items) {
        this.orderId = orderId;
        this.amount = amount;
        this.items = items;
    }

    String getOrderId() { return orderId; }

    BigDecimal getAmount() { return amount; }

    String getItems() { return items; }

    @Override
    public void accept(OrderVisitor visitor) {
        // 双分派第二跳：this 的静态类型就是 NormalOrder，重载在此刻被唯一确定。
        visitor.visit(this);
    }

    @Override
    public String toString() {
        return "NormalOrder{" + orderId + ", amount=" + amount + ", items=" + items + "}";
    }
}

/** 具体元素二：拼单（多人合单）。 */
class GroupOrder implements OrderElement {

    private final String orderId;
    private final BigDecimal amount;
    private final int participantCount;

    GroupOrder(String orderId, BigDecimal amount, int participantCount) {
        this.orderId = orderId;
        this.amount = amount;
        this.participantCount = participantCount;
    }

    String getOrderId() { return orderId; }

    BigDecimal getAmount() { return amount; }

    int getParticipantCount() { return participantCount; }

    @Override
    public void accept(OrderVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public String toString() {
        return "GroupOrder{" + orderId + ", amount=" + amount + ", participantCount=" + participantCount + "}";
    }
}

/** 具体元素三：退款单（可能全额退款，也可能部分退款）。 */
class RefundOrder implements OrderElement {

    private final String orderId;
    private final BigDecimal amount;
    private final BigDecimal refundedAmount;

    RefundOrder(String orderId, BigDecimal amount, BigDecimal refundedAmount) {
        this.orderId = orderId;
        this.amount = amount;
        this.refundedAmount = refundedAmount;
    }

    String getOrderId() { return orderId; }

    BigDecimal getAmount() { return amount; }

    BigDecimal getRefundedAmount() { return refundedAmount; }

    @Override
    public void accept(OrderVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public String toString() {
        return "RefundOrder{" + orderId + ", amount=" + amount + ", refundedAmount=" + refundedAmount + "}";
    }
}

/** 访问者接口：三个重载方法，由双分派决定实际进入哪一个；report() 供演示统一打印结果。 */
interface OrderVisitor {

    void visit(NormalOrder order);

    void visit(GroupOrder order);

    void visit(RefundOrder order);

    /** 返回本访问者累积的结果文本。 */
    List<String> report();
}

/** 具体访问者一：营业额与订单数统计，退款单按负值计入。 */
class RevenueReportVisitor implements OrderVisitor {

    private BigDecimal totalRevenue = BigDecimal.ZERO;
    private int countedOrders;

    @Override
    public void visit(NormalOrder order) {
        totalRevenue = totalRevenue.add(order.getAmount());
        countedOrders++;
    }

    @Override
    public void visit(GroupOrder order) {
        totalRevenue = totalRevenue.add(order.getAmount());
        countedOrders++;
    }

    @Override
    public void visit(RefundOrder order) {
        // 退款是收入冲减：按「退款金额的负值」计入营业额，而不是把整单金额抹掉。
        totalRevenue = totalRevenue.subtract(order.getRefundedAmount());
        countedOrders++;
    }

    @Override
    public List<String> report() {
        List<String> lines = new ArrayList<>();
        lines.add("纳入统计订单数 = " + countedOrders + " 笔");
        lines.add("营业额（退款按负值冲减）= " + totalRevenue.setScale(2, RoundingMode.HALF_UP) + " 元");
        return lines;
    }
}

/** 具体访问者二：菜品销量榜，只处理普通订单与拼单。 */
class PopularDishVisitor implements OrderVisitor {

    /** 拼单不做逐菜解析（真实项目会展开明细），这里按人数折算销量。 */
    private static final int GROUP_DISH_PER_PERSON = 2;

    private final Map<String, Integer> dishSales = new LinkedHashMap<>();

    private final List<String> ignoredRefunds = new ArrayList<>();

    @Override
    public void visit(NormalOrder order) {
        // 明细格式「菜名 x份数」，按菜名归并，才能得到真正的销量榜。
        for (String item : order.getItems().split(",")) {
            String[] parts = item.trim().split("x");
            String dish = parts[0].trim();
            int quantity = (parts.length > 1) ? Integer.parseInt(parts[1].trim()) : 1;
            dishSales.merge(dish, quantity, Integer::sum);
        }
    }

    @Override
    public void visit(GroupOrder order) {
        dishSales.merge("拼单套餐", GROUP_DISH_PER_PERSON * order.getParticipantCount(), Integer::sum);
    }

    @Override
    public void visit(RefundOrder order) {
        // 退款单不参与销量榜：这是「同一元素、不同访问者行为不同」的直观体现。
        ignoredRefunds.add("忽略退款单 " + order.getOrderId() + "：不计入菜品销量");
    }

    @Override
    public List<String> report() {
        List<Map.Entry<String, Integer>> ranking = new ArrayList<>(dishSales.entrySet());
        ranking.sort(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                .thenComparing(Map.Entry.comparingByKey()));
        List<String> lines = new ArrayList<>(ignoredRefunds);
        lines.add("菜品销量榜（降序，含拼单折算）：");
        for (int i = 0; i < ranking.size(); i++) {
            lines.add("  " + (i + 1) + ". " + ranking.get(i).getKey() + "  销量 " + ranking.get(i).getValue());
        }
        return lines;
    }
}

/** 具体访问者三：CSV 导出，按订单类型输出不同格式的行（操作外置的典型例子）。 */
class CsvExportVisitor implements OrderVisitor {

    private final List<String> rows = new ArrayList<>();

    @Override
    public void visit(NormalOrder order) {
        rows.add("NORMAL," + order.getOrderId() + "," + money(order.getAmount())
                + ",items=" + order.getItems().replace(",", "|"));
    }

    @Override
    public void visit(GroupOrder order) {
        rows.add("GROUP," + order.getOrderId() + "," + money(order.getAmount())
                + ",participants=" + order.getParticipantCount());
    }

    @Override
    public void visit(RefundOrder order) {
        rows.add("REFUND," + order.getOrderId() + "," + money(order.getAmount())
                + ",refunded=" + money(order.getRefundedAmount()));
    }

    private String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    @Override
    public List<String> report() {
        List<String> lines = new ArrayList<>();
        lines.add("导出 CSV 行数 = " + rows.size());
        lines.addAll(rows);
        return lines;
    }
}

/** 具体访问者四：风控审计，识别大额订单与退款率异常。 */
class RiskAuditVisitor implements OrderVisitor {

    private static final BigDecimal LARGE_AMOUNT = new BigDecimal("100.00");
    private static final BigDecimal HIGH_REFUND_RATIO = new BigDecimal("0.80");

    private final List<String> alerts = new ArrayList<>();
    private int orderCount;
    private int refundCount;

    @Override
    public void visit(NormalOrder order) {
        orderCount++;
        checkLargeAmount(order.getOrderId(), order.getAmount());
    }

    @Override
    public void visit(GroupOrder order) {
        orderCount++;
        checkLargeAmount(order.getOrderId(), order.getAmount());
    }

    @Override
    public void visit(RefundOrder order) {
        orderCount++;
        refundCount++;
        checkLargeAmount(order.getOrderId(), order.getAmount());
        // 退款率异常：单笔退款金额占订单金额 80% 以上，需要人工复核。
        BigDecimal ratio = order.getRefundedAmount().divide(order.getAmount(), 4, RoundingMode.HALF_UP);
        if (ratio.compareTo(HIGH_REFUND_RATIO) >= 0) {
            alerts.add("[退款预警] 退款率异常：" + order.getOrderId() + " 退款 " + order.getRefundedAmount()
                    + " / 订单 " + order.getAmount() + "（占比 "
                    + ratio.multiply(new BigDecimal("100")).setScale(1, RoundingMode.HALF_UP) + "%）");
        }
    }

    private void checkLargeAmount(String orderId, BigDecimal amount) {
        if (amount.compareTo(LARGE_AMOUNT) >= 0) {
            alerts.add("[大额预警] " + orderId + " 金额 " + amount + " 元，建议人工确认");
        }
    }

    @Override
    public List<String> report() {
        List<String> lines = new ArrayList<>();
        lines.add("审计订单数 = " + orderCount + "，其中退款单 " + refundCount + " 笔");
        if (alerts.isEmpty()) {
            lines.add("未发现风险");
            return lines;
        }
        lines.add("告警 " + alerts.size() + " 条：");
        lines.addAll(alerts);
        return lines;
    }
}
