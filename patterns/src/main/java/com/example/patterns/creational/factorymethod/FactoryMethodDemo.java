package com.example.patterns.creational.factorymethod;

import com.example.patterns.PatternDemo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 工厂方法模式（Factory Method）演示：外卖平台的支付网关。
 *
 * <p><b>用了什么模式：</b>工厂方法模式——把「创建哪个产品」延迟到子类（具体工厂）决定。
 * 调用方只依赖抽象产品 {@link PaymentGateway} 与抽象工厂 {@link PaymentGatewayFactory}，
 * 运行期传入不同的具体工厂即可切换支付渠道。</p>
 *
 * <p><b>为什么用：</b>平台要接钱包、微信、支付宝……若调用方写成
 * {@code if ("wallet".equals(channel)) new WalletPaymentGateway(); else if ...}，
 * 每加一个渠道（比如云闪付）都要回头改这段「已测试通过」的结算代码，违反开闭原则，
 * 而支付是核心链路，改动风险极高。</p>
 *
 * <p><b>不用会怎样：</b>switch/if-else 随渠道数量线性膨胀；渠道密钥、超时、签名逻辑散落在调用方；
 * 第三方 SDK 依赖渗透进业务层，单元测试无法用桩网关替换。</p>
 *
 * <p>本 Demo 同时给出<b>静态工厂方法</b> {@code PaymentGatewayFactory.of(channel)} 作对比：
 * 它更简洁，但内部仍是一个集中式 switch，新增渠道必须改工厂本身，属于「简单工厂」而非工厂方法。</p>
 *
 * @author 课程大作业
 */
public class FactoryMethodDemo implements PatternDemo {

    /**
     * 单独运行入口：与 {@link #run()} 共用同一段逻辑，保证「单独跑」和「菜单跑」输出一致。
     *
     * @param args 未使用
     */
    public static void main(String[] args) { new FactoryMethodDemo().run(); }

    @Override
    public String name() { return "工厂方法模式"; }

    @Override
    public String code() { return "02"; }

    @Override
    public String category() { return "创建型"; }

    @Override
    public String description() {
        return "支付网关创建：钱包/微信/支付宝各由自己的工厂类生产，对应外卖下单结算时的多渠道支付选择。";
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
        System.out.println("  Product（抽象产品）        ：PaymentGateway —— 统一支付能力 name() / pay()");
        System.out.println("  ConcreteProduct（具体产品） ：WalletPaymentGateway / WechatPayGateway / AlipayGateway");
        System.out.println("  Creator（抽象工厂）        ：PaymentGatewayFactory —— 声明抽象方法 createGateway()");
        System.out.println("  ConcreteCreator（具体工厂） ：WalletGatewayFactory / WechatGatewayFactory / AlipayGatewayFactory");
        System.out.println("  对比用的静态工厂方法        ：PaymentGatewayFactory.of(String channel)（简单工厂）");

        demoFactoryMethod();
        demoStaticFactoryContrast();
        printConclusion();
    }

    /** ① 工厂方法：调用方只持有抽象工厂列表，通过多态创建产品，代码里没有任何 switch。 */
    private void demoFactoryMethod() {
        System.out.println();
        System.out.println("—— 调用过程 ——");
        System.out.println("1) 工厂方法：把具体工厂装进 List，用多态调用 createGateway()，调用方看不到任何 if/switch");
        List<PaymentGatewayFactory> factories = List.of(
                new WalletGatewayFactory(), new WechatGatewayFactory(), new AlipayGatewayFactory());
        int index = 0;
        for (PaymentGatewayFactory factory : factories) {
            index++;
            System.out.println("   " + index + ". 具体工厂类 = " + factory.getClass().getSimpleName()
                    + "  →  形参声明类型 = PaymentGatewayFactory");
            checkout(factory, "ORD-20261010-000" + index, new BigDecimal("38.80"));
        }
        System.out.println("   说明：checkout(...) 的形参是抽象工厂，它不知道也不关心具体产品类，");
        System.out.println("        因此新增渠道时这段结算代码一个字都不用改。");
    }

    /**
     * 结算方法：只依赖抽象，具体产品由传入的工厂决定。
     *
     * @param factory 具体工厂（运行期多态替换）
     * @param orderId 订单号
     * @param amount  支付金额
     */
    private void checkout(PaymentGatewayFactory factory, String orderId, BigDecimal amount) {
        PaymentGateway gateway = factory.createGateway();
        System.out.println("       创建出的产品实际类型 = " + gateway.getClass().getSimpleName());
        System.out.println("       支付结果 = " + (gateway.pay(orderId, amount) ? "成功" : "失败"));
    }

    /** ② 静态工厂方法对比：写法更短，但内部是集中式 switch。 */
    private void demoStaticFactoryContrast() {
        System.out.println();
        System.out.println("2) 对比：静态工厂方法 PaymentGatewayFactory.of(channel)，一行拿到产品");
        for (String channel : List.of("wallet", "wechat", "alipay")) {
            PaymentGateway gateway = PaymentGatewayFactory.of(channel);
            System.out.println("   of(\"" + channel + "\") → " + gateway.getClass().getSimpleName()
                    + "（name()=" + gateway.name() + "）");
        }
        System.out.println("   传入不支持的渠道时抛异常并由调用方兜底（不静默降级，避免走错支付通道）：");
        try {
            PaymentGatewayFactory.of("unionpay");
        } catch (IllegalArgumentException ex) {
            System.out.println("     [已捕获] " + ex.getMessage());
        }
        System.out.println();
        System.out.println("3) 两者差异小结（同一产品的两种创建方式）");
        System.out.println("   静态工厂 of()：调用短、无需多态；但新增渠道必须修改 of() 内部 switch——对修改开放，违反开闭；");
        System.out.println("   工厂方法     ：新增渠道 = 新增一个 XxxGatewayFactory 类，调用方与旧工厂都不动——对扩展开放。");
    }

    /** 结论小节。 */
    private void printConclusion() {
        System.out.println();
        System.out.println("—— 结论 / 为什么这么设计 ——");
        System.out.println("   1. 工厂方法把「使用产品」与「创建产品」解耦：结算链路只认 PaymentGateway 接口，");
        System.out.println("      接入新渠道属于「加类」而不是「改老代码」；");
        System.out.println("   2. 每个具体工厂只负责一个渠道的构造细节（密钥、超时、沙箱开关），职责单一、易测；");
        System.out.println("   3. 单元测试可传入一个假工厂返回桩网关，避免真实调用第三方；");
        System.out.println("   4. 代价值得权衡：每个渠道多一个工厂类，若渠道极少且稳定，"
                + "用简单工厂 of() 或枚举映射反而更划算——按变化频率选模式。");
        System.out.println("============================================================");
    }
}

/** 抽象产品：所有支付渠道对上层暴露的统一能力。 */
interface PaymentGateway {

    /** 渠道中文名，例如「钱包支付」。 */
    String name();

    /**
     * 发起支付。
     *
     * @param orderId 订单号
     * @param amount  支付金额（元）
     * @return 是否支付成功
     */
    boolean pay(String orderId, BigDecimal amount);
}

/** 具体产品：平台钱包支付（余额扣减，同步返回）。 */
class WalletPaymentGateway implements PaymentGateway {

    @Override
    public String name() { return "钱包支付"; }

    @Override
    public boolean pay(String orderId, BigDecimal amount) {
        System.out.println("       [钱包渠道] 扣减余额 ￥" + amount + "，订单 " + orderId
                + " 支付成功（余额支付无第三方回调，同步返回）");
        return true;
    }
}

/** 具体产品：微信支付（预下单 + 异步回调，用日志模拟）。 */
class WechatPayGateway implements PaymentGateway {

    @Override
    public String name() { return "微信支付"; }

    @Override
    public boolean pay(String orderId, BigDecimal amount) {
        System.out.println("       [微信渠道] 调用统一下单接口 prepay_id=wx_" + orderId
                + "，拉起收银台扣款 ￥" + amount + "，回调验签通过 → 支付成功");
        return true;
    }
}

/** 具体产品：支付宝（异步 notify 回调）。 */
class AlipayGateway implements PaymentGateway {

    @Override
    public String name() { return "支付宝"; }

    @Override
    public boolean pay(String orderId, BigDecimal amount) {
        System.out.println("       [支付宝渠道] 创建交易 trade_no=ali_" + orderId
                + "，用户付款 ￥" + amount + "，notify 校验 sign 通过 → 支付成功");
        return true;
    }
}

/**
 * 抽象工厂（Creator）：只声明「怎么创建产品」，不关心产品细节。
 *
 * <p>静态方法 {@link #of(String)} 是刻意加入的<b>对比项</b>：它是简单工厂，
 * 集中式 switch 会让新增渠道必须改动本类，正好反衬工厂方法的开闭优势。</p>
 */
abstract class PaymentGatewayFactory {

    /** 工厂方法：由子类决定实例化哪一个支付网关（以抽象产品类型返回）。 */
    abstract PaymentGateway createGateway();

    /**
     * 静态工厂方法（简单工厂，用于对比）。
     *
     * @param channel 渠道编码，支持 wallet / wechat / alipay
     * @return 对应支付网关
     * @throws IllegalArgumentException 渠道不支持时抛出，避免静默降级
     */
    static PaymentGateway of(String channel) {
        if (channel == null) {
            throw new IllegalArgumentException("支付渠道不能为空");
        }
        return switch (channel.toLowerCase()) {
            case "wallet" -> new WalletPaymentGateway();
            case "wechat" -> new WechatPayGateway();
            case "alipay" -> new AlipayGateway();
            default -> throw new IllegalArgumentException("暂不支持的支付渠道：" + channel);
        };
    }
}

/** 具体工厂：生产钱包支付网关。 */
class WalletGatewayFactory extends PaymentGatewayFactory {

    @Override
    PaymentGateway createGateway() { return new WalletPaymentGateway(); }
}

/** 具体工厂：生产微信支付网关。 */
class WechatGatewayFactory extends PaymentGatewayFactory {

    @Override
    PaymentGateway createGateway() { return new WechatPayGateway(); }
}

/** 具体工厂：生产支付宝网关。 */
class AlipayGatewayFactory extends PaymentGatewayFactory {

    @Override
    PaymentGateway createGateway() { return new AlipayGateway(); }
}
