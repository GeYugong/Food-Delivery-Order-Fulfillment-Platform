package com.example.delivery.infrastructure.coupon;

import com.example.delivery.domain.coupon.Coupon;
import com.example.delivery.domain.order.Order;
import com.example.delivery.domain.support.CouponEvaluator;
import com.example.delivery.domain.user.User;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 优惠券规则解释器（解释器模式 Interpreter 的「客户端」角色：驱动解析与求值）。
 *
 * <p><b>设计模式：解释器模式（Interpreter）</b></p>
 *
 * <p><b>它解决的问题：</b>运营希望「改优惠规则不改代码、不发版」。
 * 于是规则被写成字符串（{@code "满30减5"}、{@code "9折"}、
 * {@code "满50减10 或 会员等级==DIAMOND"}），由本类完成三步：</p>
 * <ol>
 *   <li><b>解析</b>：{@link CouponRuleParser} 递归下降解析成表达式树（AST）；</li>
 *   <li><b>求值</b>：调用根节点的 {@code contribute(context)} 递归求抵扣额；</li>
 *   <li><b>封顶</b>：抵扣额不得为负、不得超过订单小计（防止实付变负数）。</li>
 * </ol>
 *
 * <p><b>为什么自研而不接第三方表达式引擎（Aviator / QLExpress / Groovy）：</b>
 * 课程作业要展示解释器模式本身的实现细节（词法 → 语法 → AST → 解释执行）；
 * 用第三方库会把知识点藏进依赖里。生产系统则通常反过来选第三方库，
 * 因为它更成熟、支持更复杂的表达式——这是本工程刻意保留的权衡说明。</p>
 *
 * <p><b>与领域层的关系：</b>本类实现领域层的 {@link CouponEvaluator} 接口，
 * 因此订单聚合完全不知道规则语法的存在；把本类换成任何其它实现（甚至脚本引擎），
 * 业务代码零改动（依赖倒置）。</p>
 *
 * @author 课程大作业
 */
public class CouponRuleInterpreter implements CouponEvaluator {

    /** 券库：编号 → 券对象（由应用装配时注入）。 */
    private final Map<String, Coupon> couponLibrary = new LinkedHashMap<>();

    /** 最近一次求值的说明（含 AST 与中间结果）。 */
    private String lastExplanation = "";

    /** 最近一次解析出的根表达式。 */
    private CouponExpression lastRoot;

    /** 最近一次使用的规则文本。 */
    private String lastRuleText = "";

    /**
     * 注册一张可用优惠券（应用装配时调用）。
     *
     * @param coupon 优惠券
     * @return this
     */
    public CouponRuleInterpreter register(Coupon coupon) {
        couponLibrary.put(coupon.couponId(), coupon);
        return this;
    }

    /**
     * 按订单求值：订单上已绑定优惠券时按其规则计算，否则返回 0。
     *
     * @param order 订单
     * @return 可抵扣金额
     */
    @Override
    public BigDecimal evaluate(Order order) {
        String couponId = order.couponId().orElse(null);
        if (couponId == null) {
            lastExplanation = "订单未绑定优惠券，抵扣 0.00 元";
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        Coupon coupon = couponLibrary.get(couponId);
        if (coupon == null) {
            lastExplanation = "找不到优惠券[" + couponId + "]，抵扣 0.00 元";
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return evaluateCoupon(coupon, order);
    }

    /**
     * 对「指定规则文本 + 订单」求值（演示与测试的主入口）。
     *
     * @param ruleText 规则文本，例如 {@code "满30减5"}
     * @param order    订单
     * @return 可抵扣金额
     */
    public BigDecimal evaluate(String ruleText, Order order) {
        return evaluateDetailed(ruleText, order);
    }

    /**
     * 对「优惠券 + 订单」求值，并校验有效期与门槛。
     *
     * @param coupon 优惠券
     * @param order  订单
     * @return 可抵扣金额
     */
    public BigDecimal evaluateCoupon(Coupon coupon, Order order) {
        if (!coupon.validOnToday()) {
            lastExplanation = String.format("券[%s]不在有效期内（%s ~ %s），抵扣 0.00 元",
                    coupon.couponId(), coupon.validFrom(), coupon.validTo());
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal discount = evaluateDetailed(coupon.ruleExpression(), order);
        lastExplanation = String.format("券[%s]（%s）规则「%s」→ 抵扣 %s 元%n%s",
                coupon.couponId(), coupon.name(), coupon.ruleExpression(),
                discount.toPlainString(), lastExplanation);
        return discount;
    }

    /**
     * 解析规则文本并求值，同时把「AST 结构 + 中间结果」写入 {@link #explain()}。
     *
     * @param ruleText 规则文本
     * @param order    订单
     * @return 可抵扣金额
     */
    public BigDecimal evaluateDetailed(String ruleText, Order order) {
        this.lastRuleText = ruleText;
        CouponExpression root = parse(ruleText);
        this.lastRoot = root;

        CouponContext context = new CouponContext(order.subtotal(),
                order.customer().memberLevel().name());
        BigDecimal raw = root.contribute(context);

        // 三重保护：非负 → 不超过小计 → 两位小数
        BigDecimal capped = raw.max(BigDecimal.ZERO).min(order.subtotal())
                .setScale(2, RoundingMode.HALF_UP);

        List<String> lines = new ArrayList<>();
        lines.add("解析出的抽象语法树（AST）：");
        lines.addAll(root.describeTree("  "));
        lines.add(String.format("求值上下文：小计=%s 元，会员等级=%s",
                context.subtotal().toPlainString(), context.memberLevel()));
        lines.add(String.format("求值结果：原始抵扣=%s 元 → 封顶后抵扣=%s 元（上限为小计 %s 元）",
                raw.setScale(2, RoundingMode.HALF_UP).toPlainString(), capped.toPlainString(),
                order.subtotal().toPlainString()));
        this.lastExplanation = String.join(System.lineSeparator(), lines);
        return capped;
    }

    /**
     * 解析规则文本为表达式树（供 Demo 与单元测试直接使用）。
     *
     * @param ruleText 规则文本
     * @return 表达式树根节点
     * @throws IllegalArgumentException 语法错误时抛出（含位置信息）
     */
    public CouponExpression parse(String ruleText) {
        if (ruleText == null || ruleText.isBlank()) {
            throw new IllegalArgumentException("优惠券规则不能为空");
        }
        CouponRuleParser parser = new CouponRuleParser(ruleText);
        CouponExpression root = parser.parseExpression();
        parser.expectEnd();
        return root;
    }

    @Override
    public String explain() {
        return lastExplanation;
    }

    /** @return 最近一次解析出的 AST 根节点 */
    public CouponExpression lastRoot() {
        return lastRoot;
    }

    /** @return 最近一次求值使用的规则文本 */
    public String lastRuleText() {
        return lastRuleText;
    }

    /** @return 已注册的券库（只读） */
    public Map<String, Coupon> couponLibrary() {
        return java.util.Collections.unmodifiableMap(couponLibrary);
    }

    /**
     * 直接对「规则 + 小计 + 会员等级」求值（不依赖订单对象，便于单元测试与 Demo）。
     *
     * @param ruleText    规则文本
     * @param subtotal    小计
     * @param memberLevel 会员等级名（{@code NORMAL/GOLD/DIAMOND}）
     * @return 抵扣金额
     */
    public BigDecimal evaluateForTest(String ruleText, BigDecimal subtotal, String memberLevel) {
        CouponExpression root = parse(ruleText);
        CouponContext context = new CouponContext(subtotal, memberLevel);
        return root.contribute(context).max(BigDecimal.ZERO).min(subtotal)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 内置示例规则（控制台菜单与文档使用）。
     *
     * @return 规则文本列表
     */
    public static List<String> sampleRules() {
        return List.of(
                "满30减5",
                "满50减10",
                "9折",
                "8.5折",
                "满30减5 且 会员等级>=1",
                "满50减10 或 会员等级==2",
                "(满30减5 或 满50减10) 且 会员等级==2",
                "小计>=100 且 8折");
    }

    /**
     * 优惠券求值上下文（解释器模式中的「上下文 Context」角色）。
     *
     * <p>上下文存放解释过程需要的<b>全局信息</b>（这里是小计与会员等级），
     * 表达式节点只从上下文读数，不直接依赖订单对象——
     * 于是表达式树可以脱离订单独立测试（见 {@link #evaluateForTest}）。</p>
     *
     * @param subtotal    订单小计
     * @param memberLevel 会员等级名（{@code NORMAL/GOLD/DIAMOND}）
     * @author 课程大作业
     */
    public record CouponContext(BigDecimal subtotal, String memberLevel) {

        /** 紧凑构造器：补默认值。 */
        public CouponContext {
            subtotal = subtotal == null ? BigDecimal.ZERO : subtotal;
            memberLevel = (memberLevel == null || memberLevel.isBlank())
                    ? User.MemberLevel.NORMAL.name() : memberLevel.toUpperCase();
        }

        /**
         * 会员等级序号：NORMAL=0、GOLD=1、DIAMOND=2。
         *
         * <p>把「等级」映射为「序号」是让比较表达式可以统一用数值比较的关键设计：</p>
         * <pre>
         *   会员等级 >= 1   等价于   会员等级 ∈ {GOLD, DIAMOND}
         *   会员等级 == 2   等价于   会员等级 = DIAMOND
         * </pre>
         *
         * @return 等级序号
         */
        public int memberRank() {
            return switch (memberLevel) {
                case "GOLD" -> 1;
                case "DIAMOND" -> 2;
                default -> 0;
            };
        }
    }
}
