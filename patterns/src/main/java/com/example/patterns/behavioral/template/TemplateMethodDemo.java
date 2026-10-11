package com.example.patterns.behavioral.template;

import com.example.patterns.PatternDemo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 模板方法模式（Template Method）演示：订单存档与报表导出。
 *
 * <p><b>用了什么模式：</b>模板方法模式。在抽象类 {@link OrderArchiveTemplate} 中把「订单存档」的算法骨架
 * 固定下来（加载 → 过滤 → 前置钩子 → 格式化 → 写盘 → 后置钩子），其中骨架方法
 * {@link OrderArchiveTemplate#archive()} 声明为 {@code final}，只把「可变步骤」留给子类：
 * {@code format(...)}、{@code writeOut(...)} 为抽象方法，{@code filter(...)}、{@code beforeArchive()}、
 * {@code afterArchive(int)} 为可覆盖的钩子方法。</p>
 *
 * <p><b>为什么用：</b>平台每天要把订单存档成 CSV 交财务、导出成 JSON 交数据仓库、生成汇总给运营。
 * 三种导出的差别只有「格式」和「输出目的地」，「取哪些订单、什么时候加统计、写完要不要发通知」
 * 完全是同一套流程，用模板方法把公共流程提炼到父类，子类只填差异部分，去掉三份重复代码。</p>
 *
 * <p><b>不用会怎样：</b>三个导出类各自写一遍「查订单 → 过滤终态 → 格式化 → 输出」，
 * 一旦业务要求「只存档已完成订单」或「导出前先锁表」，就要改三处，很容易漏改一处导致口径不一致。</p>
 *
 * <p><b>好莱坞原则（Hollywood Principle）：</b>「别调用我们，我们会调用你」。
 * 子类从不主动调用父类的流程方法，父类 {@code archive()} 在恰当的时刻反过来调用子类
 * 的 {@code format()} / {@code writeOut()}。控制权在父类，子类只提供插槽实现。</p>
 *
 * @author 课程大作业
 */
public class TemplateMethodDemo implements PatternDemo {

    /** public 无参构造器：便于 PatternShowcase 通过反射创建实例。 */
    public TemplateMethodDemo() {
        // 无状态，每次 run() 重新构造模板实现。
    }

    /** 独立运行入口，与菜单入口走同一个 run()。 */
    public static void main(String[] args) {
        new TemplateMethodDemo().run();
    }

    @Override
    public String name() {
        return "模板方法模式";
    }

    @Override
    public String code() {
        return "16";
    }

    @Override
    public String category() {
        return "行为型";
    }

    @Override
    public String description() {
        return "订单存档与报表导出：CSV、JSON、汇总报表共用同一套存档骨架，只重写可变步骤";
    }

    @Override
    public void run() {
        System.out.println("==================================================");
        System.out.println("  " + name() + "（Behavioral / Template Method）  编号 " + code());
        System.out.println("  场景：" + description());
        System.out.println("==================================================");

        System.out.println();
        System.out.println("【参与角色】");
        System.out.println("  AbstractClass   : OrderArchiveTemplate（final archive() 固定骨架 + 钩子）");
        System.out.println("  ConcreteClass   : CsvArchiveTemplate / JsonArchiveTemplate / SummaryArchiveTemplate");
        System.out.println("  固定骨架步骤     : loadAll() → filter() → beforeArchive() → format() → writeOut() → afterArchive()");
        System.out.println("  原始订单数据     : 由父类 loadAll() 提供（模拟 6 笔订单，含 2 笔非终态）");

        List<OrderArchiveTemplate> templates = new ArrayList<>();
        templates.add(new CsvArchiveTemplate());
        templates.add(new JsonArchiveTemplate());
        templates.add(new SummaryArchiveTemplate());

        System.out.println();
        System.out.println("【调用过程：三个子类依次跑同一套 archive() 骨架】");
        int step = 1;
        for (OrderArchiveTemplate template : templates) {
            System.out.println();
            System.out.println("  (" + step + ") 执行子类：" + template.getClass().getSimpleName());
            System.out.println("      注意：调用方只调用了 archive() 这一个方法，"
                    + "骨架里的每一步都是父类回调子类的。");
            // 调用方与子类都只依赖 archive()：流程顺序由父类掌握，调用方无需编排步骤。
            template.archive();
            step++;
        }

        System.out.println();
        System.out.println("【结论】");
        System.out.println("  1. 同一套骨架（加载 → 过滤 → 钩子 → 格式化 → 输出 → 钩子）被三种导出复用，");
        System.out.println("     重复代码只保留一份，数据口径天然一致；");
        System.out.println("  2. archive() 加了 final：子类无法改变步骤顺序，也无法「忘记调用」某一步，");
        System.out.println("     这正是模板方法模式把「流程控制权」留在父类的关键手段；");
        System.out.println("  3. SummaryArchiveTemplate 覆盖了 filter()，说明钩子方法让子类可以在不改流程的前提下");
        System.out.println("     微调某一步的细节（只保留已完成订单）；");
        System.out.println("  4. 好莱坞原则：别调用我们，我们会调用你——子类被动等待父类回调；");
        System.out.println("  5. 代价：继承是一种强耦合，子类数量多时容易出现类爆炸，");
        System.out.println("     且父类新增抽象步骤会迫使所有子类一起改（可给默认实现来缓解）。");
        System.out.println("==================================================");
    }
}

/** 订单领域对象：包级类，仅用于演示存档数据来源。 */
class Order {

    private final String orderId;
    private final String status;
    private final BigDecimal amount;
    private final String rider;

    Order(String orderId, String status, BigDecimal amount, String rider) {
        this.orderId = orderId;
        this.status = status;
        this.amount = amount;
        this.rider = rider;
    }

    String getOrderId() { return orderId; }

    String getStatus() { return status; }

    BigDecimal getAmount() { return amount; }

    String getRider() { return rider; }
}

/**
 * 抽象类：定义订单存档的算法骨架。
 *
 * <p>骨架方法 {@code archive()} 必须是 {@code final}——原因见方法上的注释。</p>
 */
abstract class OrderArchiveTemplate {

    /**
     * 模板方法：<b>流程顺序在这里一次性写死</b>。
     *
     * <p><b>为什么加 final：</b>模板方法的全部价值在于「步骤顺序由父类统一掌控」。
     * 如果允许子类覆盖它，子类就可能漏掉 beforeArchive() 的加锁、或把 format() 放到 writeOut() 之后，
     * 于是「同一个骨架」的承诺失效，业务口径又会分散回各个子类。加 final 后，
     * 子类只能替换插槽实现，不能改流程——这是本模式最重要的约束。</p>
     */
    public final void archive() {
        // 第 1 步：加载订单（默认实现，子类可覆盖为真实的数据库查询）
        List<Order> orders = loadAll();
        System.out.println("      [骨架 1/6] loadAll() 加载到 " + orders.size() + " 笔订单");

        // 第 2 步：过滤（默认只保留终态订单，子类可覆盖）
        List<Order> archived = filter(orders);
        System.out.println("      [骨架 2/6] filter() 过滤后剩余 " + archived.size() + " 笔终态订单");
        if (archived.isEmpty()) {
            System.out.println("      [骨架] 无数据可存档，提前结束（流程仍然由父类控制）");
            return;
        }

        // 第 3 步：前置钩子（默认空实现，子类可加锁、打点、发通知）
        beforeArchive();
        System.out.println("      [骨架 3/6] beforeArchive() 前置钩子执行完毕");

        // 第 4 步：格式化（抽象方法，子类必须实现）
        List<String> lines = format(archived);
        System.out.println("      [骨架 4/6] format() 由子类生成了 " + lines.size() + " 行文本");

        // 第 5 步：输出（抽象方法，子类必须实现）
        writeOut(lines);
        System.out.println("      [骨架 5/6] writeOut() 由子类完成输出");

        // 第 6 步：后置钩子，把条数回传给子类做统计或通知
        afterArchive(archived.size());
    }

    /** 默认实现：返回模拟订单列表；真实项目中这里调用订单仓储。 */
    protected List<Order> loadAll() {
        List<Order> orders = new ArrayList<>();
        orders.add(new Order("FD2024001", "已完成", new BigDecimal("68.00"), "张伟"));
        orders.add(new Order("FD2024002", "已完成", new BigDecimal("25.50"), "李娜"));
        orders.add(new Order("FD2024003", "已取消", new BigDecimal("39.90"), "王强"));
        orders.add(new Order("FD2024004", "配送中", new BigDecimal("52.00"), "张伟"));
        orders.add(new Order("FD2024005", "已完成", new BigDecimal("103.20"), "赵敏"));
        orders.add(new Order("FD2024006", "已完成", new BigDecimal("18.80"), "李娜"));
        return orders;
    }

    /** 默认实现：只保留终态订单（已完成 / 已取消 / 已退款），子类可按业务覆盖。 */
    protected List<Order> filter(List<Order> orders) {
        List<Order> result = new ArrayList<>();
        for (Order order : orders) {
            if (isTerminal(order.getStatus())) {
                result.add(order);
            }
        }
        return result;
    }

    /** 钩子方法：默认什么都不做，子类可插入前置动作。 */
    protected void beforeArchive() {
        // 默认空实现：这是「钩子」与「抽象方法」的区别——钩子让子类可选地介入。
    }

    /** 抽象方法：子类必须给出格式化的具体算法。 */
    protected abstract List<String> format(List<Order> orders);

    /** 抽象方法：子类必须给出输出的具体去向（文件 / 内存 / 网络）。 */
    protected abstract void writeOut(List<String> lines);

    /** 钩子方法：默认什么都不做，子类可插入后置动作（写审计日志、发通知）。 */
    protected void afterArchive(int count) {
        // 默认空实现。
    }

    /** 终态判定：供默认 filter 与子类共用。 */
    protected boolean isTerminal(String status) {
        return "已完成".equals(status) || "已取消".equals(status) || "已退款".equals(status);
    }

    /** 统一金额格式，保证 CSV / JSON 里的小数位一致。 */
    protected String money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** 工具方法：把行列表拼成带换行的整块文本（演示用，实际会写入文件）。 */
    protected String join(List<String> lines) {
        return String.join(System.lineSeparator(), lines);
    }
}

/** 具体实现一：CSV 存档，表头 orderId,status,amount,rider，写入内存字符串并打印。 */
class CsvArchiveTemplate extends OrderArchiveTemplate {

    @Override
    protected void beforeArchive() {
        System.out.println("      [钩子] beforeArchive()：CSV 导出前先对订单表加只读快照，避免导出期间数据漂移");
    }

    @Override
    protected List<String> format(List<Order> orders) {
        List<String> lines = new ArrayList<>();
        // CSV 表头固定，财务系统按列解析，列顺序不可随意调整。
        lines.add("orderId,status,amount,rider");
        for (Order order : orders) {
            lines.add(order.getOrderId() + "," + order.getStatus() + ","
                    + money(order.getAmount()) + "," + order.getRider());
        }
        return lines;
    }

    @Override
    protected void writeOut(List<String> lines) {
        System.out.println("      [输出] 写入内存字符串 csvBuffer（长度 " + join(lines).length() + " 字符）：");
        for (String line : lines) {
            System.out.println("         " + line);
        }
    }

    @Override
    protected void afterArchive(int count) {
        System.out.println("      [钩子] afterArchive(" + count + ")：通知财务系统 CSV 已就绪");
    }
}

/** 具体实现二：JSON 存档，输出 JSON 数组文本，交给数据仓库。 */
class JsonArchiveTemplate extends OrderArchiveTemplate {

    @Override
    protected List<String> format(List<Order> orders) {
        List<String> lines = new ArrayList<>();
        lines.add("[");
        for (int i = 0; i < orders.size(); i++) {
            Order order = orders.get(i);
            String comma = (i == orders.size() - 1) ? "" : ",";
            lines.add("  {\"orderId\": \"" + order.getOrderId() + "\", \"status\": \""
                    + order.getStatus() + "\", \"amount\": " + money(order.getAmount())
                    + ", \"rider\": \"" + order.getRider() + "\"}" + comma);
        }
        lines.add("]");
        return lines;
    }

    @Override
    protected void writeOut(List<String> lines) {
        System.out.println("      [输出] 写入内存字符串 jsonBuffer（length=" + lines.size() + " 行）：");
        for (String line : lines) {
            System.out.println("         " + line);
        }
    }
}

/** 具体实现三：汇总报表，只统计条数与总额，并覆盖 filter() 只保留已完成订单。 */
class SummaryArchiveTemplate extends OrderArchiveTemplate {

    @Override
    protected List<Order> filter(List<Order> orders) {
        // 覆盖钩子方法：运营口径只认「已完成」，已取消/已退款不进汇总营业额。
        List<Order> result = new ArrayList<>();
        for (Order order : orders) {
            if ("已完成".equals(order.getStatus())) {
                result.add(order);
            }
        }
        System.out.println("      [钩子] 本子类覆盖了 filter()：只保留「已完成」订单（这是钩子的价值）");
        return result;
    }

    @Override
    protected List<String> format(List<Order> orders) {
        BigDecimal total = BigDecimal.ZERO;
        for (Order order : orders) {
            total = total.add(order.getAmount());
        }
        List<String> lines = new ArrayList<>();
        lines.add("汇总报表：订单数 = " + orders.size() + " 笔，营业额合计 = "
                + money(total) + " 元");
        return lines;
    }

    @Override
    protected void writeOut(List<String> lines) {
        System.out.println("      [输出] 汇总结果写入内存字符串 summaryBuffer：");
        for (String line : lines) {
            System.out.println("         " + line);
        }
    }

    @Override
    protected void afterArchive(int count) {
        System.out.println("      [钩子] afterArchive(" + count + ")：汇总报表已推送给运营值班群");
    }
}
