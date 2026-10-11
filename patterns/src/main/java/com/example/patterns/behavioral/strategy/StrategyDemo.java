package com.example.patterns.behavioral.strategy;

import com.example.patterns.PatternDemo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 策略模式（Strategy）演示：骑手调度策略 + 配送费计价策略。
 *
 * <p><b>用了什么模式：</b>策略模式。把「一族可互换的算法」各自封装成实现同一接口的独立类，再提供一个持有该
 * 接口的上下文（{@link DispatchContext}），客户端可在运行期随意替换算法，而上下文与调用方代码一行都不用改。</p>
 *
 * <p><b>为什么用：</b>外卖平台天然的「同一件事有很多种做法」——派单可按距离、按在手单量、按评分；
 * 计价可按基础公式、按会员权益、按恶劣天气。<b>不用会怎样：</b>只能在一个方法里塞
 * {@code if (type == 1) ... else if (type == 2) ...}，于是每加一种规则都要改已有类（违反开闭原则）、
 * 方法膨胀成几百行的「上帝方法」、单个算法无法独立测试；运营想上「雨天加价」还得发版改老代码，
 * 改成策略后新增规则 = 新增一个类，上线开关只是切换 {@code setStrategy(...)} 的入参。</p>
 *
 * <p><b>与状态模式的区别（答辩常问）：</b>策略由客户端显式选择与切换，各策略彼此平等、互不知道对方；
 * 状态模式的状态迁移由对象内部根据业务事件自动完成，状态类通常持有回写上下文的引用。</p>
 *
 * @author 课程大作业
 */
public class StrategyDemo implements PatternDemo {

    /** public 无参构造器：便于 PatternShowcase 通过反射创建实例。 */
    public StrategyDemo() {
        // 无需初始化状态，演示数据结构每次 run() 内重新构造。
    }

    /** 独立运行入口，与菜单入口走同一个 run()，保证两次运行结果一致。 */
    public static void main(String[] args) {
        new StrategyDemo().run();
    }

    @Override
    public String name() {
        return "策略模式";
    }

    @Override
    public String code() {
        return "15";
    }

    @Override
    public String category() {
        return "行为型";
    }

    @Override
    public String description() {
        return "骑手派单与配送费计价：距离/负载/评分多套算法与多套计价规则可运行期互换";
    }

    @Override
    public void run() {
        System.out.println("==================================================");
        System.out.println("  " + name() + "（Behavioral / Strategy）  编号 " + code());
        System.out.println("  场景：" + description());
        System.out.println("==================================================");

        System.out.println();
        System.out.println("【参与角色】");
        System.out.println("  Strategy        : DispatchStrategy（调度策略接口）+ PricingStrategy（计价策略接口）");
        System.out.println("  ConcreteStrategy: 调度＝NearestFirst / LeastLoad / HighestRating；"
                + "计价＝Base / MemberFreeDelivery / RainyDay");
        System.out.println("  Context         : DispatchContext（持有 DispatchStrategy，支持运行期 setStrategy）");
        System.out.println("  Client          : StrategyDemo（本类，负责选择并切换策略）");

        // ================= 第一部分：调度策略 =================
        System.out.println();
        System.out.println("【调用过程 · 一、同一批骑手，三种调度策略各选一次】");
        List<Rider> candidates = mockRiders();
        System.out.println("  候选骑手池（3 人，数据完全相同，仅策略不同）：");
        for (Rider rider : candidates) {
            System.out.println("    " + rider);
        }

        DispatchContext context = new DispatchContext();
        List<DispatchStrategy> dispatchStrategies = new ArrayList<>();
        dispatchStrategies.add(new NearestFirstStrategy());
        dispatchStrategies.add(new LeastLoadStrategy());
        dispatchStrategies.add(new HighestRatingStrategy());

        int step = 1;
        for (DispatchStrategy strategy : dispatchStrategies) {
            // 关键点：运行期切换策略，只换「策略对象」这一个入参。
            context.setStrategy(strategy);
            Rider picked = context.dispatch(candidates, "老王牛肉面（人民路店）");
            System.out.println("  (" + step + ") 策略 = " + strategy.name());
            System.out.println("      选中骑手 " + picked);
            System.out.println("      选择理由：" + explain(strategy));
            step++;
        }

        System.out.println();
        System.out.println("  [观察] 调用方（上面的 for 循环）没有一行 if/else 判断策略类型，它只知道 DispatchStrategy");
        System.out.println("     这一个接口。新增「按空闲时长优先」策略，只需新增一个类并 add 进列表，上下文与本方法都不用改。");

        // ================= 第二部分：计价策略 =================
        System.out.println();
        System.out.println("【调用过程 · 二、同一笔订单，三种计价策略各算一次配送费】");
        BigDecimal subtotal = new BigDecimal("42.50");
        int distanceKm = 3;
        boolean peakHour = true;
        System.out.println("  订单参数：菜品小计 = " + money(subtotal)
                + " 元，配送距离 = " + distanceKm + " km，是否高峰时段 = " + peakHour);

        List<PricingStrategy> pricingStrategies = new ArrayList<>();
        pricingStrategies.add(new BasePricingStrategy());
        pricingStrategies.add(new MemberFreeDeliveryStrategy());
        pricingStrategies.add(new RainyDayPricingStrategy());

        int priceStep = 1;
        for (PricingStrategy pricing : pricingStrategies) {
            BigDecimal fee = money(pricing.deliveryFee(subtotal, distanceKm, peakHour));
            System.out.println("  (" + priceStep + ") " + pricing.name()
                    + " → 配送费 = " + fee + " 元");
            priceStep++;
        }
        System.out.println("  [观察] 同一笔订单三种结果说明：计价算法本身也是「一族可替换算法」，用策略模式封装后，");
        System.out.println("    运营配置只是选择哪一个策略实现，而不是改公式代码。");

        // ================= 第三部分：结论 =================
        System.out.println();
        System.out.println("【结论】");
        System.out.println("  1. 调度与计价各自面向接口编程，算法可运行期互换，上下文零修改；");
        System.out.println("  2. 新增策略 = 新增一个类，符合开闭原则；每个算法独立成类，可单独单元测试；");
        System.out.println("  3. 与状态模式的区别：策略由客户端 setStrategy 选择/切换，状态由对象内部自动迁移；");
        System.out.println("  4. 代价：策略类数量会膨胀，且客户端必须知道各策略的差异才能选对。");
        System.out.println("==================================================");
    }

    /** 打印用的策略理由，避免把解释性文字写进策略类（策略只负责算，不负责解释）。 */
    private String explain(DispatchStrategy strategy) {
        if (strategy instanceof NearestFirstStrategy) {
            return "取 distanceKm 最小者，最快到店，适合追求时效的午高峰";
        }
        if (strategy instanceof LeastLoadStrategy) {
            return "取 currentLoad 最小者，避免骑手超载，适合运力均衡";
        }
        return "取 rating 最高者，评分相同时比距离，适合服务质量优先";
    }

    /** 构造固定的候选骑手池，保证三种策略面对的是完全相同的数据。 */
    private List<Rider> mockRiders() {
        List<Rider> riders = new ArrayList<>();
        riders.add(new Rider("R01", "张伟", 2.4, 5, 4.9, "在线"));
        riders.add(new Rider("R02", "李娜", 1.1, 2, 4.6, "在线"));
        riders.add(new Rider("R03", "王强", 3.8, 0, 4.6, "在线"));
        return riders;
    }

    /** 统一金额精度：保留两位小数、四舍五入，避免浮点误差出现在账单里。 */
    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
/** 骑手：包级类，只作为策略的输入数据载体。 */
class Rider {

    private final String id;
    private final String name;
    private final double distanceKm;
    private final int currentLoad;
    private final double rating;
    private final String status;

    Rider(String id, String name, double distanceKm, int currentLoad, double rating, String status) {
        this.id = id;
        this.name = name;
        this.distanceKm = distanceKm;
        this.currentLoad = currentLoad;
        this.rating = rating;
        this.status = status;
    }

    double getDistanceKm() { return distanceKm; }

    int getCurrentLoad() { return currentLoad; }

    double getRating() { return rating; }

    @Override
    public String toString() {
        return id + "/" + name + "（距离 " + distanceKm + "km，在手 " + currentLoad
                + " 单，评分 " + rating + "，" + status + "）";
    }
}

/** 调度策略接口：把「怎么挑骑手」抽象出来，上下文只依赖它。 */
interface DispatchStrategy {

    /** 策略中文名，用于打印与配置界面展示。 */
    String name();

    /** 从候选中挑出一名骑手；返回 null 表示无可用骑手。 */
    Rider pick(List<Rider> candidates, String merchantName);
}

/** 具体策略一：距离最近优先。 */
class NearestFirstStrategy implements DispatchStrategy {

    @Override
    public String name() {
        return "距离最近优先（NearestFirst）";
    }

    @Override
    public Rider pick(List<Rider> candidates, String merchantName) {
        return candidates.stream().min(Comparator.comparingDouble(Rider::getDistanceKm)).orElse(null);
    }
}

/** 具体策略二：在手单量最少优先，保护骑手不超载。 */
class LeastLoadStrategy implements DispatchStrategy {

    @Override
    public String name() {
        return "在手单量最少优先（LeastLoad）";
    }

    @Override
    public Rider pick(List<Rider> candidates, String merchantName) {
        return candidates.stream().min(Comparator.comparingInt(Rider::getCurrentLoad)).orElse(null);
    }
}

/** 具体策略三：评分最高优先，评分相同时比距离（保证结果稳定可解释）。 */
class HighestRatingStrategy implements DispatchStrategy {

    @Override
    public String name() {
        return "评分最高优先（HighestRating）";
    }

    @Override
    public Rider pick(List<Rider> candidates, String merchantName) {
        Comparator<Rider> byRatingThenDistance = Comparator.comparingDouble(Rider::getRating)
                .reversed().thenComparingDouble(Rider::getDistanceKm);
        return candidates.stream().min(byRatingThenDistance).orElse(null);
    }
}

/** 调度上下文：持有策略引用，提供运行期切换与委托调用。 */
class DispatchContext {

    private DispatchStrategy strategy;

    /** 运行期切换策略：这是策略模式相对于继承硬编码的核心优势。 */
    void setStrategy(DispatchStrategy strategy) {
        this.strategy = strategy;
    }

    /** 委托给当前策略执行，上下文自身不含任何算法细节。 */
    Rider dispatch(List<Rider> candidates, String merchantName) {
        if (strategy == null) {
            throw new IllegalStateException("未设置调度策略");
        }
        return strategy.pick(candidates, merchantName);
    }
}

/** 计价策略接口：把「怎么算配送费」抽象出来。 */
interface PricingStrategy {

    /** 计价策略中文名。 */
    String name();

    /** 计算配送费：菜品小计 + 距离 + 是否高峰。 */
    BigDecimal deliveryFee(BigDecimal subtotal, int distanceKm, boolean peakHour);
}

/** 具体计价策略一：基础公式 = 起步 5 元 + 每公里 1 元 + 高峰加价 2 元。 */
class BasePricingStrategy implements PricingStrategy {

    private static final BigDecimal BASE = new BigDecimal("5.00");
    private static final BigDecimal PER_KM = new BigDecimal("1.00");
    private static final BigDecimal PEAK_SURCHARGE = new BigDecimal("2.00");

    @Override
    public String name() {
        return "基础计价（起步 5 元 + 1 元/公里 + 高峰加价 2 元）";
    }

    @Override
    public BigDecimal deliveryFee(BigDecimal subtotal, int distanceKm, boolean peakHour) {
        BigDecimal fee = BASE.add(PER_KM.multiply(new BigDecimal(distanceKm)));
        if (peakHour) {
            fee = fee.add(PEAK_SURCHARGE);
        }
        return fee.setScale(2, RoundingMode.HALF_UP);
    }
}

/** 具体计价策略二：会员菜品满 30 元免配送费，否则退回基础计价。 */
class MemberFreeDeliveryStrategy implements PricingStrategy {

    private static final BigDecimal FREE_THRESHOLD = new BigDecimal("30.00");
    private final PricingStrategy fallback = new BasePricingStrategy();

    @Override
    public String name() {
        return "会员满 30 免配送费（MemberFreeDelivery）";
    }

    @Override
    public BigDecimal deliveryFee(BigDecimal subtotal, int distanceKm, boolean peakHour) {
        if (subtotal.compareTo(FREE_THRESHOLD) >= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return fallback.deliveryFee(subtotal, distanceKm, peakHour);
    }
}

/** 具体计价策略三：恶劣天气整体 1.5 倍（先用基础价算出毛额再乘系数）。 */
class RainyDayPricingStrategy implements PricingStrategy {

    private static final BigDecimal FACTOR = new BigDecimal("1.5");
    private final PricingStrategy base = new BasePricingStrategy();

    @Override
    public String name() {
        return "雨天加价 1.5 倍（RainyDay）";
    }

    @Override
    public BigDecimal deliveryFee(BigDecimal subtotal, int distanceKm, boolean peakHour) {
        return base.deliveryFee(subtotal, distanceKm, peakHour)
                .multiply(FACTOR)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
