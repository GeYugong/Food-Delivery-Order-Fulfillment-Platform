package com.example.patterns.structural.decorator;

import com.example.patterns.PatternDemo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 【结构型】装饰器模式（Decorator）—— 订单价格层层加价。
 *
 * <p>用了什么模式：装饰器模式。PriceDecorator 与被装饰对象 PriceComponent 实现同一接口，
 * 内部持有被装饰对象并在其 cost() 上叠加新费用，因此可以像「套娃」一样递归嵌套。</p>
 * <p>为什么用：实付价 = 菜品小计 + 打包费 + 小费 + 雨天补贴（将来还有夜间服务费、餐具费……），
 * 这些附加项可选、可叠加、顺序由业务配置决定。装饰器把每个附加项做成独立小类，运行期按需组合。</p>
 * <p>不用会怎样（继承方案）：3 个装饰项就有 2^3 = 8 个子类，每新增一项数量翻倍；
 * 「雨天 1.5 倍」这种运行期参数静态子类根本无法表达。</p>
 */
public class DecoratorDemo implements PatternDemo {

    /** 三个装饰项名称：用于生成「2^3 种组合」与全排列说明。 */
    private static final String[] DECORATOR_NAMES = {"打包费", "小费", "雨天补贴"};

    /** 独立运行入口，与菜单入口共用 run()；无参构造器供 PatternShowcase 反射实例化。 */
    public static void main(String[] args) {
        new DecoratorDemo().run();
    }

    public DecoratorDemo() {
        // 无需初始化状态
    }

    @Override
    public String name() { return "装饰器模式"; }

    @Override
    public String code() { return "06"; }

    @Override
    public String category() {
        return "结构型";
    }

    @Override
    public String description() {
        return "订单价格层层加价：打包费/小费/雨天补贴自由叠加，实付价按装饰顺序动态计算";
    }

    /** 全流程演示：角色 → 组合装饰 → 全排列 → 继承对照 → 结论。 */
    @Override
    public void run() {
        System.out.println("==========================================================");
        System.out.println("【" + code() + "】" + name() + "（" + category() + "）");
        System.out.println("场景：" + description());
        System.out.println("==========================================================");
        System.out.println();
        System.out.println("── 一、参与角色 ──");
        System.out.println("  Component（抽象组件）  ：PriceComponent —— describe() / cost()");
        System.out.println("  ConcreteComponent      ：BaseOrderPrice —— 菜品小计（鸡腿堡 19.80 + 薯条 9.90 + 可乐 6.50）");
        System.out.println("  Decorator（抽象装饰器）：PriceDecorator —— 持有 PriceComponent wrapped，实现同一接口");
        System.out.println("  ConcreteDecorator      ：PackagingFeeDecorator(+1.00)、TipDecorator(+2.00)、");
        System.out.println("                           RainSurchargeDecorator(+3.00，支持倍率，1.5 倍 = +4.50)");
        System.out.println();
        System.out.println("  关键点：装饰器与被装饰对象实现同一接口，所以「装饰器也能被装饰」，可无限嵌套。");
        // 组合 1：只有打包费
        System.out.println();
        System.out.println("── 二、调用过程：自由组合装饰顺序（每次包装都打印描述与价格） ──");
        System.out.println();
        System.out.println("[组合 1] 小计 → 打包费");
        printPrice(new PackagingFeeDecorator(new BaseOrderPrice()));
        // 组合 2：逐层包装，每次包装都打印一次
        System.out.println();
        System.out.println("[组合 2] 小计 → 打包费 → 小费 → 雨天补贴(1.5 倍)");
        PriceComponent price = new BaseOrderPrice();
        System.out.println("    -- 原始组件：" + price.describe() + " = " + money(price.cost()) + " 元");
        price = new PackagingFeeDecorator(price);
        System.out.println("    -- 包一层打包费：" + price.describe() + " = " + money(price.cost()) + " 元");
        price = new TipDecorator(price);
        System.out.println("    -- 再包一层小费：" + price.describe() + " = " + money(price.cost()) + " 元");
        price = new RainSurchargeDecorator(price, new BigDecimal("1.5"));
        System.out.println("    -- 再包一层雨天补贴：");
        printPrice(price);
        System.out.println("    （PriceComponent 引用被逐层「重新指向」外层装饰器，这就是递归嵌套）");
        // 组合 3、4：单装饰与双层同类装饰
        System.out.println();
        System.out.println("[组合 3] 只加雨天补贴（基础倍率 1.0）");
        printPrice(new RainSurchargeDecorator(new BaseOrderPrice()));
        System.out.println();
        System.out.println("[组合 4] 双层打包费（一单两袋）：装饰器装饰装饰器");
        printPrice(new TipDecorator(new PackagingFeeDecorator(new PackagingFeeDecorator(new BaseOrderPrice()))));
        // 全排列：3! = 6 种套娃顺序
        System.out.println();
        System.out.println("── 三、顺序也由业务决定：3 个装饰器的全排列（3! = 6 种套娃顺序） ──");
        List<List<String>> all = new ArrayList<>();
        permute(new ArrayList<String>(), all);
        int index = 1;
        for (List<String> order : all) {
            PriceComponent combined = new BaseOrderPrice();
            for (String label : order) {
                combined = wrap(combined, label);
            }
            System.out.println("  (" + index + ") " + combined.describe());
            System.out.println("        实付 " + money(combined.cost()) + " 元   套娃顺序：" + order);
            index++;
        }
        System.out.println();
        System.out.println("  金额相加可交换所以合计一致，但「描述文案顺序」直接展示给用户，业务上仍需固定顺序");
        System.out.println("  （先固定费用、后浮动补贴），装饰器让这个顺序在代码里显式可见。");
        // 继承方案对照：2^n 子类爆炸
        System.out.println();
        System.out.println("── 四、如果改用继承：3 个装饰器 → 2^3 = 8 个子类 ──");
        System.out.println("  1 BaseOrderPriceWithPackaging        （+打包费）");
        System.out.println("  2 BaseOrderPriceWithTip              （+小费）");
        System.out.println("  3 BaseOrderPriceWithRain             （+雨天补贴）");
        System.out.println("  4 BaseOrderPriceWithPackagingAndTip  （+打包费 +小费）");
        System.out.println("  5 BaseOrderPriceWithPackagingAndRain （+打包费 +雨天）");
        System.out.println("  6 BaseOrderPriceWithTipAndRain       （+小费 +雨天）");
        System.out.println("  7 BaseOrderPriceWithPackagingTipRain （三个都要）");
        System.out.println("  8 BaseOrderPrice                     （一个都不要）");
        System.out.println();
        System.out.println("  再加「夜间服务费」→ 16 个，再加「餐具费」→ 32 个；");
        System.out.println("  装饰器方案只要 4 个附加类（3 个具体装饰器 + 1 个抽象装饰器），");
        System.out.println("  且「雨天 1.5 倍」这类运行期参数放进构造器即可，继承方案无法表达。");
        // 结论
        System.out.println();
        System.out.println("── 五、结论 ──");
        System.out.println("  1. 装饰器 = 不修改原类、不用继承，运行期动态给对象添加职责；");
        System.out.println("  2. 三要素：与被装饰对象同接口 + 持有其引用 + 在调用前后叠加行为；");
        System.out.println("  3. 接口一致 = 可递归嵌套，包装顺序与层数由业务在运行时决定；");
        System.out.println("  4. vs 适配器：装饰器接口不变、只加功能；适配器是换接口、功能不变；");
        System.out.println("  5. vs 代理：代理同为「同接口」，但目的是控访问（鉴权/缓存/懒加载），通常单个且不叠加；");
        System.out.println("  6. 代价：小类变多、对象链变长，需要 describe() 这类方法把链路说清楚。");
        System.out.println();
        System.out.println("==========================================================");
    }

    /** 按名称包一层具体装饰器：把「名字 → 装饰器对象」的映射集中在一处。 */
    private static PriceComponent wrap(PriceComponent inner, String label) {
        switch (label) {
            case "打包费":
                return new PackagingFeeDecorator(inner);
            case "小费":
                return new TipDecorator(inner);
            default:
                return new RainSurchargeDecorator(inner);
        }
    }

    /** 生成 DECORATOR_NAMES 的全排列，每个名称只出现一次。 */
    private static void permute(List<String> picked, List<List<String>> out) {
        if (picked.size() == DECORATOR_NAMES.length) {
            out.add(new ArrayList<>(picked));
            return;
        }
        for (String candidate : DECORATOR_NAMES) {
            if (picked.contains(candidate)) {
                continue;
            }
            picked.add(candidate);
            permute(picked, out);
            picked.remove(picked.size() - 1);
        }
    }

    /** 统一打印「描述 + 价格」，让每次包装的结果都能对照。 */
    private static void printPrice(PriceComponent component) {
        System.out.println("    → " + component.describe());
        System.out.println("    → 实付： " + money(component.cost()) + " 元");
    }

    /** 金额格式化到两位小数。 */
    private static String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}

/** Component（抽象组件）：订单价格。装饰器与被装饰对象都实现它，故可互相嵌套。 */
interface PriceComponent {

    String describe();

    BigDecimal cost();
}

/** ConcreteComponent（具体组件）：基础菜品小计，装饰链最内层。 */
class BaseOrderPrice implements PriceComponent {

    private static final BigDecimal BURGER = new BigDecimal("19.80");   // 鸡腿堡
    private static final BigDecimal FRIES = new BigDecimal("9.90");     // 薯条
    private static final BigDecimal COLA = new BigDecimal("6.50");      // 可乐

    @Override
    public String describe() { return "菜品小计(" + BURGER + "+" + FRIES + "+" + COLA + ")"; }

    @Override
    public BigDecimal cost() { return BURGER.add(FRIES).add(COLA); }
}

/** Decorator（抽象装饰器）：持有被装饰对象并把接口原样转发，子类只覆写要增强的部分。 */
abstract class PriceDecorator implements PriceComponent {

    /** 被装饰对象（wrapped / component）。 */
    protected final PriceComponent wrapped;

    /** @param wrapped 被装饰对象，不能为 null */
    protected PriceDecorator(PriceComponent wrapped) {
        if (wrapped == null) {
            throw new IllegalArgumentException("被装饰对象不能为 null");
        }
        this.wrapped = wrapped;
    }

    @Override
    public String describe() { return wrapped.describe(); }

    @Override
    public BigDecimal cost() { return wrapped.cost(); }
}

/** 固定金额装饰器的公共骨架：子类只需给出「名称 + 金额」。 */
abstract class FixedFeeDecorator extends PriceDecorator {

    /** @param wrapped 被装饰对象 */
    protected FixedFeeDecorator(PriceComponent wrapped) {
        super(wrapped);
    }

    /** 费用名称，用于拼描述，例如「打包费」。 */
    abstract String feeName();

    /** 固定金额（元）。 */
    abstract BigDecimal fee();

    @Override
    public String describe() { return wrapped.describe() + " + " + feeName() + " " + fee().toPlainString(); }

    @Override
    public BigDecimal cost() { return wrapped.cost().add(fee()); }
}

/** ConcreteDecorator：打包费 1.00 元。 */
class PackagingFeeDecorator extends FixedFeeDecorator {

    /** @param wrapped 被装饰对象 */
    PackagingFeeDecorator(PriceComponent wrapped) {
        super(wrapped);
    }

    @Override
    String feeName() { return "打包费"; }

    @Override
    BigDecimal fee() { return new BigDecimal("1.00"); }
}

/** ConcreteDecorator：小费 2.00 元（用户自愿打赏骑手）。 */
class TipDecorator extends FixedFeeDecorator {

    /** @param wrapped 被装饰对象 */
    TipDecorator(PriceComponent wrapped) {
        super(wrapped);
    }

    @Override
    String feeName() { return "小费"; }

    @Override
    BigDecimal fee() { return new BigDecimal("2.00"); }
}

/** ConcreteDecorator：雨天补贴，基础 3.00 元并支持倍率（运行期参数，继承做不到）。 */
class RainSurchargeDecorator extends PriceDecorator {

    private static final BigDecimal BASE = new BigDecimal("3.00");   // 基础补贴

    /** 倍率。 */
    private final BigDecimal rate;

    /** 默认倍率 1.0。 */
    RainSurchargeDecorator(PriceComponent wrapped) {
        this(wrapped, BigDecimal.ONE);
    }

    /** @param wrapped 被装饰对象 @param rate 倍率，必须大于 0 */
    RainSurchargeDecorator(PriceComponent wrapped, BigDecimal rate) {
        super(wrapped);
        if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("雨天补贴倍率必须大于 0");
        }
        this.rate = rate;
    }

    @Override
    public String describe() { return wrapped.describe() + " + 雨天补贴 3.00×" + rate.toPlainString(); }

    @Override
    public BigDecimal cost() { return wrapped.cost().add(BASE.multiply(rate)); }
}
