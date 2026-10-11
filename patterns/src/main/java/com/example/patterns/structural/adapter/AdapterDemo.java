package com.example.patterns.structural.adapter;

import com.example.patterns.PatternDemo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 【结构型】适配器模式（Adapter，对象适配器）—— 第三方支付渠道接入。
 *
 * <p>用了什么模式：对象适配器。适配器类「持有」第三方 SDK 实例（组合而非继承，故称对象适配器），
 * 同时实现平台自己的接口，在中间做「参数翻译 + 返回值翻译」，让互不兼容的接口可以协作。</p>
 * <p>为什么用：微信/支付宝/银行三家的方法名、金额单位（元/分）、返回结构（Map 报文 / String / int）都不同。
 * 不让业务代码直接 new 这些 SDK，平台才能换渠道、加渠道而不改下单流程。</p>
 * <p>不用会怎样：下单流程里到处是 if (channel == WECHAT) 分支，要认识 3 套 API、记 3 种单位换算、
 * 写 3 种成功判断；渠道 SDK 一升级，所有调用点全要改，违反开闭原则。</p>
 */
public class AdapterDemo implements PatternDemo {

    /** 订单号，贯穿「异构调用」与「统一调用」两段演示。 */
    private static final String ORDER_NO = "WM20240517001";

    /** 订单金额（元），用于演示元 → 分的单位翻译。 */
    private static final BigDecimal AMOUNT_YUAN = new BigDecimal("46.70");

    /** public 无参构造器：PatternShowcase 注册表通过反射实例化需要它。 */
    public AdapterDemo() {
        // 无需初始化状态
    }

    /** 独立运行入口，与菜单入口共用 run()。 */
    public static void main(String[] args) {
        new AdapterDemo().run();
    }

    @Override
    public String name() {
        return "适配器模式";
    }

    @Override
    public String code() {
        return "04";
    }

    @Override
    public String category() {
        return "结构型";
    }

    @Override
    public String description() {
        return "第三方支付渠道接入：把微信/支付宝/银行三套异构 SDK 统一成平台 PaymentGateway 接口";
    }

    @Override
    public void run() {
        printTitle();
        printRoles();
        printHeterogeneousCalls();
        printUnifiedCalls();
        printComparison();
        printConclusion();
    }

    private void printTitle() {
        System.out.println("==========================================================");
        System.out.println("【" + code() + "】" + name() + "（" + category() + "）");
        System.out.println("场景：" + description());
        System.out.println("==========================================================");
    }

    private void printRoles() {
        System.out.println();
        System.out.println("── 一、参与角色 ──");
        System.out.println("  Target（目标接口）      ：PaymentGateway —— 平台统一支付入口");
        System.out.println("  Adaptee（被适配者/异构）：WechatSdk、AliPayClient、BankGateway（方法名/单位/返回值都不同）");
        System.out.println("  Adapter（适配器）       ：WechatPayAdapter、AlipayAdapter、BankCardAdapter（持有被适配者做翻译）");
        System.out.println("  Client（客户端）        ：下单流程 —— 只依赖 PaymentGateway，不认识任何 SDK");
        System.out.println();
        System.out.println("  本 Demo 订单号 = " + ORDER_NO + "，金额 = " + AMOUNT_YUAN + " 元（" + MoneyUtil.toFen(AMOUNT_YUAN) + " 分）");
    }

    /** 第二段：直接调用异构 SDK，展示「不统一」的麻烦。 */
    private void printHeterogeneousCalls() {
        System.out.println();
        System.out.println("── 二、异构调用日志（不统一：客户端要逐个认识 3 套 API） ──");

        System.out.println();
        System.out.println("[1] 微信：Map<String,Object> unifiedOrder(String outTradeNo, int totalFen)");
        System.out.println("    ← 返回报文：" + new WechatSdk().unifiedOrder(ORDER_NO, MoneyUtil.toFen(AMOUNT_YUAN)));
        System.out.println("    客户端要自己判断 return_code 是否 SUCCESS，还要自己把「元」换成「分」");

        System.out.println();
        System.out.println("[2] 支付宝：String trade(String subject, String outBizNo, double yuan)");
        System.out.println("    ← 返回字符串：" + new AliPayClient()
            .trade("外卖订单-" + ORDER_NO, ORDER_NO, AMOUNT_YUAN.doubleValue()));
        System.out.println("    客户端要自己判断 equals(\"TRADE_SUCCESS\")，还要自己拼 subject 文案");

        System.out.println();
        System.out.println("[3] 银行卡：int debit(String accountRef, long amountInCent)");
        System.out.println("    ← 返回 int 码：" + new BankGateway().debit("ACC-8823", MoneyUtil.toFen(AMOUNT_YUAN)));
        System.out.println("    客户端要自己判断是否等于 0（0 才是成功），又是第三套约定");
    }

    /** 第三段：通过适配器调用统一接口。 */
    private void printUnifiedCalls() {
        System.out.println();
        System.out.println("── 三、统一接口调用结果（客户端只认识 PaymentGateway） ──");
        System.out.println("  统一接口：String channel();  boolean pay(String orderId, BigDecimal amount);");
        System.out.println();

        PaymentGateway[] gateways = {
            new WechatPayAdapter(new WechatSdk()),
            new AlipayAdapter(new AliPayClient()),
            new BankCardAdapter(new BankGateway())
        };
        int index = 1;
        for (PaymentGateway gateway : gateways) {
            boolean success = gateway.pay(ORDER_NO, AMOUNT_YUAN);
            System.out.println("[适配器 " + index + "] channel=" + gateway.channel()
                + "  pay(\"" + ORDER_NO + "\", " + AMOUNT_YUAN + ") → " + success);
            index++;
        }
        System.out.println();
        System.out.println("  客户端代码形态：for (PaymentGateway g : gateways) { g.pay(orderId, amount); }");
        System.out.println("  没有任何 if/else 分支，也没有单位换算与报文解析。");
    }

    private void printComparison() {
        System.out.println();
        System.out.println("── 四、对照表 ──");
        System.out.println("  渠道     直接调用（异构）                        适配后（统一）");
        System.out.println("  微信     unifiedOrder(no, int fen) → Map 报文    pay(orderId, BigDecimal) → boolean");
        System.out.println("  支付宝   trade(subject, no, double) → String     pay(orderId, BigDecimal) → boolean");
        System.out.println("  银行卡   debit(acc, long cent) → int 码          pay(orderId, BigDecimal) → boolean");
        System.out.println();
        System.out.println("  地址（方法名）、语言（金额单位）、回执（返回值）三者都不同，");
        System.out.println("  适配器就是「翻译官」，把 3 套方言翻译成平台听得懂的一种话。");
    }

    private void printConclusion() {
        System.out.println();
        System.out.println("── 五、结论 ──");
        System.out.println("  1. 适配器解决「接口不兼容」：不改变被适配者行为，只做转换；");
        System.out.println("  2. 翻译是双向的：入参 元 → 分，出参 报文/字符串/int → boolean；");
        System.out.println("  3. vs 装饰器：装饰器与被装饰者同接口、目的是「加功能」；适配器是「改接口」、功能不变；");
        System.out.println("  4. vs 代理：代理与被代理对象同接口、接口不变，目的是「控访问」（鉴权/缓存/懒加载）；");
        System.out.println("  5. 新增渠道只需再加一个 XxxAdapter，业务代码零改动，满足开闭原则。");
        System.out.println();
        System.out.println("==========================================================");
    }
}

/** 包级工具：元 → 分。三个适配器都要做这次换算，抽出来避免重复。 */
final class MoneyUtil {

    private MoneyUtil() {
        // 工具类禁止实例化
    }

    /** 元转分：适配器必须做的参数翻译。 */
    static int toFen(BigDecimal yuan) {
        return yuan.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).intValueExact();
    }
}

/** Target（目标接口）：平台统一支付网关。业务只依赖它，绝不依赖 SDK 类型。 */
interface PaymentGateway {

    /** 渠道名称，例如「微信支付」。 */
    String channel();

    /** 统一支付入口：订单号 + 金额（元），返回是否成功。 */
    boolean pay(String orderId, BigDecimal amount);
}

/** Adaptee（被适配者）：微信 SDK —— Map 报文、金额单位为分。 */
class WechatSdk {

    /** 微信统一下单：outTradeNo + 金额（分），返回微信风格报文。 */
    Map<String, Object> unifiedOrder(String outTradeNo, int totalFen) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("return_code", "SUCCESS");
        report.put("result_code", "SUCCESS");
        report.put("prepay_id", "wx_prepay_" + outTradeNo.toLowerCase());
        report.put("total_fee", totalFen);
        return report;
    }
}

/** Adaptee（被适配者）：支付宝客户端 —— String 返回值、金额为元的 double、需要 subject。 */
class AliPayClient {

    /** 支付宝交易：subject + 订单号 + 金额（元），成功时返回以 TRADE_SUCCESS 开头的字符串。 */
    String trade(String subject, String outBizNo, double yuan) {
        return "TRADE_SUCCESS:2024051722001:" + subject + ":" + outBizNo + ":" + yuan;
    }
}

/** Adaptee（被适配者）：银行网关 —— int 错误码、金额为分的 long、需要账号引用。 */
class BankGateway {

    /** 银行扣款：账号引用 + 金额（分），返回错误码，0 表示成功。 */
    int debit(String accountRef, long amountInCent) {
        return amountInCent > 0 && accountRef != null && !accountRef.isEmpty() ? 0 : -1;
    }
}

/** Adapter：微信 → PaymentGateway。翻译单位（元→分）与返回值（报文→boolean）。 */
class WechatPayAdapter implements PaymentGateway {

    /** 被适配者：持有的第三方 SDK 实例。 */
    private final WechatSdk sdk;

    /** @param sdk 微信 SDK */
    WechatPayAdapter(WechatSdk sdk) {
        this.sdk = sdk;
    }

    @Override
    public String channel() {
        return "微信支付";
    }

    @Override
    public boolean pay(String orderId, BigDecimal amount) {
        Map<String, Object> report = sdk.unifiedOrder(orderId, MoneyUtil.toFen(amount));
        return "SUCCESS".equals(report.get("return_code")) && "SUCCESS".equals(report.get("result_code"));
    }
}

/** Adapter：支付宝 → PaymentGateway。补 subject、BigDecimal → double、状态串 → boolean。 */
class AlipayAdapter implements PaymentGateway {

    /** 被适配者：支付宝客户端。 */
    private final AliPayClient client;

    /** @param client 支付宝客户端 */
    AlipayAdapter(AliPayClient client) {
        this.client = client;
    }

    @Override
    public String channel() {
        return "支付宝";
    }

    @Override
    public boolean pay(String orderId, BigDecimal amount) {
        String status = client.trade("外卖订单-" + orderId, orderId, amount.doubleValue());
        return status != null && status.startsWith("TRADE_SUCCESS");
    }
}

/** Adapter：银行卡 → PaymentGateway。派生账号引用、元 → 分、int 码 → boolean。 */
class BankCardAdapter implements PaymentGateway {

    /** 被适配者：银行网关。 */
    private final BankGateway gateway;

    /** @param gateway 银行网关 */
    BankCardAdapter(BankGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public String channel() {
        return "银行卡";
    }

    @Override
    public boolean pay(String orderId, BigDecimal amount) {
        return gateway.debit("ACC-" + orderId, MoneyUtil.toFen(amount)) == 0;
    }
}
