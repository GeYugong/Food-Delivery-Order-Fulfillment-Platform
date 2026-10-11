package com.example.patterns.creational.abstractfactory;

import com.example.patterns.PatternDemo;

import java.math.BigDecimal;

/**
 * 抽象工厂模式（Abstract Factory）演示：外卖平台的「数据工厂族」。
 *
 * <p><b>用了什么模式：</b>抽象工厂模式——一个工厂接口负责创建<b>一整族相互匹配的产品</b>
 * （用户、商家、骑手、优惠券），换一个工厂实现就整套换掉，并保证同族产品参数自洽。</p>
 *
 * <p><b>为什么用：</b>平台需要两套完整数据：演示环境要「普通会员 + 川味小厨 + 空闲骑手 + 满30减5」，
 * 压测环境要「钻石会员 + 大额订单 + 高负载骑手 + 9折券」。不用抽象工厂就得在造数代码里写四段
 * {@code if (stress) {...} else {...}}，任何一次漏改都会产出「压测用户配演示商家费率」这类脏数据。</p>
 *
 * <p><b>不用会怎样：</b>分支随「产品种类 × 环境数」爆炸；新增一套环境（灰度/回归）要改动四处旧分支，
 * 极易漏项——压测时用户拿高折扣、商家却按演示费率结算，测出的吞吐与结算数据完全不可信。</p>
 *
 * <p><b>与工厂方法的分工：</b>工厂方法关心「单个产品」的创建延迟；
 * 抽象工厂关心「一族产品」的成套创建与相互匹配。</p>
 *
 * @author 课程大作业
 */
public class AbstractFactoryDemo implements PatternDemo {

    /**
     * 单独运行入口：与 {@link #run()} 共用同一段逻辑，保证「单独跑」和「菜单跑」输出一致。
     *
     * @param args 未使用
     */
    public static void main(String[] args) { new AbstractFactoryDemo().run(); }

    @Override
    public String name() { return "抽象工厂模式"; }

    @Override
    public String code() { return "03"; }

    @Override
    public String category() { return "创建型"; }

    @Override
    public String description() {
        return "演示数据族与压测数据族的成套造数：一个工厂产出相互匹配的用户/商家/骑手/券，对应外卖平台的环境隔离造数。";
    }

    /** 执行演示：模式名 → 参与角色 → 调用过程 → 结论。 */
    @Override
    public void run() {
        System.out.println("============================================================");
        System.out.println("【" + name() + "】" + category() + "模式 · 编号 " + code());
        System.out.println("场景：" + description());
        System.out.println("============================================================");
        System.out.println();
        System.out.println("—— 参与角色 ——");
        System.out.println("  AbstractFactory（抽象工厂）：PlatformDataFactory —— 声明 createUser/createMerchant/createRider/createCoupon");
        System.out.println("  ConcreteFactory（具体工厂）：DemoDataFactory   —— 演示数据族（低负载、可读性好）");
        System.out.println("  ConcreteFactory（具体工厂）：StressTestFactory —— 压测数据族（高负载、大额订单）");
        System.out.println("  AbstractProduct（抽象产品） ：User / Merchant / Rider / Coupon");
        System.out.println("  客户端                      ：buildScene(factory) 只依赖抽象工厂，不认识任何具体产品类");

        demoDemoDataFamily();
        demoStressDataFamily();
        demoMixingFamilies();
        printConclusion();
    }

    /** ① 演示数据族：调用方只拿到抽象工厂即可完成整套造数。 */
    private void demoDemoDataFamily() {
        System.out.println();
        System.out.println("—— 调用过程 ——");
        System.out.println("1) 传入具体工厂 DemoDataFactory，一次性产出整族「演示数据」：");
        buildScene(new DemoDataFactory());
        System.out.println("   观察点：四个产品口径一致——低折扣、低起送价、低并发、低门槛券，");
        System.out.println("          适合课堂演示与手工点单验证。");
    }

    /** ② 压测数据族：同一段客户端代码，换一个工厂即换整套数据。 */
    private void demoStressDataFamily() {
        System.out.println();
        System.out.println("2) 同一段客户端代码换成 StressTestFactory，整族数据随之切换（客户端零改动）：");
        buildScene(new StressTestFactory());
        System.out.println("   观察点：钻石会员 8.8 折、商家起送价 199 元、骑手并发上限 12 单、券门槛 300 元，");
        System.out.println("          四个参数同向拉高，才能压出真实的高负载链路。");
    }

    /**
     * 造数客户端：只依赖抽象工厂，因此不认识具体产品类，也无法写出 switch。
     *
     * @param factory 数据族工厂（演示族 / 压测族）
     */
    private void buildScene(PlatformDataFactory factory) {
        User user = factory.createUser();
        Merchant merchant = factory.createMerchant();
        Rider rider = factory.createRider();
        Coupon coupon = factory.createCoupon();
        System.out.println("   工厂实现类 = " + factory.getClass().getSimpleName());
        System.out.println("   " + user.describe());
        System.out.println("   " + merchant.describe());
        System.out.println("   " + rider.describe());
        System.out.println("   " + coupon.describe());
    }

    /** ③ 反例：混用两族产品会立刻暴露数据不自洽。 */
    private void demoMixingFamilies() {
        System.out.println();
        System.out.println("3) 反例：故意「跨族混用」——压测用户 + 演示商家 + 演示券，看数据是否还自洽");
        PlatformDataFactory stress = new StressTestFactory(), demo = new DemoDataFactory();
        User stressUser = stress.createUser();
        Merchant demoMerchant = demo.createMerchant();
        Coupon demoCoupon = demo.createCoupon();
        System.out.println("   混搭结果：");
        System.out.println("     " + stressUser.describe());
        System.out.println("     " + demoMerchant.describe());
        System.out.println("     " + demoCoupon.describe());
        System.out.println("   不自洽点 A：压测用户预期单值 ￥" + stressUser.getExpectedOrderAmount()
                + "，而演示商家起送价只有 ￥" + demoMerchant.getMinOrderAmount()
                + " → 压测流量全卡在「起送价不足」，压不出并发。");
        System.out.println("   不自洽点 B：压测用户享受 "
                + stressUser.getDiscountRate().multiply(new BigDecimal("10")).stripTrailingZeros().toPlainString()
                + " 折，商家配送费率却是演示族的 ￥" + demoMerchant.getDeliveryFee()
                + " → 结算金额远低于真实场景，性能与对账数据双重失真。");
        System.out.println("   不自洽点 C：券门槛 ￥" + demoCoupon.getThreshold()
                + " 远低于压测订单均值，等于每单立减，压测结果无法反映毛利。");
        System.out.println("   结论：抽象工厂的价值正是「不许你混搭」——同族产品只能成套取用。");
    }

    /** 结论小节。 */
    private void printConclusion() {
        System.out.println();
        System.out.println("—— 结论 / 为什么这么设计 ——");
        System.out.println("   1. 抽象工厂保证族内自洽：同一次调用产出的用户、商家、骑手、券参数同向匹配，"
                + "从设计上排除跨环境混搭；");
        System.out.println("   2. 客户端只依赖 PlatformDataFactory，新增一套环境（灰度/回归）只需新增一个工厂类，"
                + "无需修改 buildScene(...) 与已有工厂，符合开闭原则；");
        System.out.println("   3. 与工厂方法的分工：单产品扩展用工厂方法，成套产品族扩展用抽象工厂；");
        System.out.println("   4. 代价：抽象工厂接口一旦新增产品种类（例如再加 createAddress()），"
                + "所有具体工厂都要实现——因此它适合「产品族稳定、族的数量会增长」的场景。");
        System.out.println("============================================================");
    }
}

/** 抽象工厂：声明造出一整族外卖领域对象的能力。 */
abstract class PlatformDataFactory {

    /** 创建用户（与当前数据族匹配）。 */
    abstract User createUser();

    /** 创建商家（与当前数据族匹配）。 */
    abstract Merchant createMerchant();

    /** 创建骑手（与当前数据族匹配）。 */
    abstract Rider createRider();

    /** 创建优惠券（与当前数据族匹配）。 */
    abstract Coupon createCoupon();
}

/** 具体工厂：演示数据族，参数小而直观，便于课堂演示与手工验证。 */
class DemoDataFactory extends PlatformDataFactory {

    @Override
    User createUser() {
        return new User("普通会员-小明", "普通会员", new BigDecimal("1.00"), new BigDecimal("39.00"), 12);
    }

    @Override
    Merchant createMerchant() {
        return new Merchant("川味小厨", new BigDecimal("20.00"), new BigDecimal("4.50"), 15);
    }

    @Override
    Rider createRider() {
        return new Rider("空闲骑手-老张", 2, "空闲", 3.5d);
    }

    @Override
    Coupon createCoupon() {
        return new Coupon("满30减5", new BigDecimal("30.00"), new BigDecimal("5.00"));
    }
}

/** 具体工厂：压测数据族，参数整体拉高，用于压出真实高负载。 */
class StressTestFactory extends PlatformDataFactory {

    @Override
    User createUser() {
        return new User("钻石会员-stress-001", "钻石会员", new BigDecimal("0.88"), new BigDecimal("388.00"), 260);
    }

    @Override
    Merchant createMerchant() {
        return new Merchant("大额订单专营店", new BigDecimal("199.00"), new BigDecimal("9.90"), 6);
    }

    @Override
    Rider createRider() {
        return new Rider("高负载骑手-stress-007", 12, "满载", 1.8d);
    }

    @Override
    Coupon createCoupon() {
        return new Coupon("9折券", new BigDecimal("300.00"), null);
    }
}

/** 抽象产品：用户（字段只读，参数由具体工厂注入）。 */
class User {

    private final String nickname;
    private final String level;
    private final BigDecimal discountRate;
    private final BigDecimal expectedOrderAmount;
    private final int monthlyOrders;

    User(String nickname, String level, BigDecimal discountRate,
         BigDecimal expectedOrderAmount, int monthlyOrders) {
        this.nickname = nickname;
        this.level = level;
        this.discountRate = discountRate;
        this.expectedOrderAmount = expectedOrderAmount;
        this.monthlyOrders = monthlyOrders;
    }

    BigDecimal getDiscountRate() { return discountRate; }
    BigDecimal getExpectedOrderAmount() { return expectedOrderAmount; }

    String describe() {
        return "用户：" + nickname + "｜等级=" + level + "｜折扣=" + discountRate
                + "｜月均下单=" + monthlyOrders + " 单｜预期单值=￥" + expectedOrderAmount;
    }
}

/** 抽象产品：商家。 */
class Merchant {

    private final String shopName;
    private final BigDecimal minOrderAmount;
    private final BigDecimal deliveryFee;
    private final int prepareMinutes;

    Merchant(String shopName, BigDecimal minOrderAmount, BigDecimal deliveryFee, int prepareMinutes) {
        this.shopName = shopName;
        this.minOrderAmount = minOrderAmount;
        this.deliveryFee = deliveryFee;
        this.prepareMinutes = prepareMinutes;
    }

    BigDecimal getMinOrderAmount() { return minOrderAmount; }
    BigDecimal getDeliveryFee() { return deliveryFee; }

    String describe() {
        return "商家：" + shopName + "｜起送价=￥" + minOrderAmount + "｜配送费率=￥" + deliveryFee
                + "｜出餐时长=" + prepareMinutes + " 分钟";
    }
}

/** 抽象产品：骑手。 */
class Rider {

    private final String riderName;
    private final int maxConcurrentOrders;
    private final String status;
    private final double avgDeliveryMinutes;

    Rider(String riderName, int maxConcurrentOrders, String status, double avgDeliveryMinutes) {
        this.riderName = riderName;
        this.maxConcurrentOrders = maxConcurrentOrders;
        this.status = status;
        this.avgDeliveryMinutes = avgDeliveryMinutes;
    }

    String describe() {
        return "骑手：" + riderName + "｜状态=" + status + "｜并发上限=" + maxConcurrentOrders
                + " 单｜平均送达=" + avgDeliveryMinutes + " 分钟";
    }
}

/** 抽象产品：优惠券（折扣券的固定立减额为 null）。 */
class Coupon {

    private final String couponName;
    private final BigDecimal threshold;
    private final BigDecimal discountAmount;

    Coupon(String couponName, BigDecimal threshold, BigDecimal discountAmount) {
        this.couponName = couponName;
        this.threshold = threshold;
        this.discountAmount = discountAmount;
    }

    BigDecimal getThreshold() { return threshold; }

    String describe() {
        String rule = discountAmount == null ? "按比例折扣（无固定立减额）" : "立减 ￥" + discountAmount;
        return "优惠券：" + couponName + "｜使用门槛=￥" + threshold + "｜优惠=" + rule;
    }
}
