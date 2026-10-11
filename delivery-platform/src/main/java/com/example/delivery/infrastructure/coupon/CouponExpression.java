package com.example.delivery.infrastructure.coupon;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 优惠券规则表达式（解释器模式 Interpreter 的「抽象表达式 AbstractExpression」角色）。
 *
 * <p><b>设计模式：解释器模式（Interpreter）</b></p>
 *
 * <p><b>角色划分：</b></p>
 * <ul>
 *   <li><b>终结符表达式</b>：{@link NumberLiteral}（数字）、{@link MemberLevelValue}（会员等级）、
 *       {@link SubtotalValue}（订单小计）；</li>
 *   <li><b>非终结符表达式</b>：{@link FullReductionExpression}（满减）、
 *       {@link DiscountExpression}（折扣）、{@link CompareExpression}（比较）、
 *       {@link AndExpression}（且）、{@link OrExpression}（或）。</li>
 * </ul>
 *
 * <p><b>本设计的关键决定：区分「值」与「优惠」两种求值结果。</b></p>
 * <p>如果所有节点都返回 {@code BigDecimal}，那么「会员等级 >= GOLD」这种条件的返回值
 * 与「满30减5」的抵扣额就会混在一起，组合子（且/或）无法判断
 * 「这个 5 到底是 5 元优惠，还是等级 5」。因此这里明确两个方法：</p>
 * <ol>
 *   <li>{@link #value(CouponRuleInterpreter.CouponContext)} —— 求「值」：
 *       数字返回自身、会员等级返回等级序号、小计返回订单金额；比较/满减/折扣返回 null（无值语义）；</li>
 *   <li>{@link #contribute(CouponRuleInterpreter.CouponContext)} —— 求「优惠贡献」：
 *       条件节点未满足返回 0，满足时返回其子节点贡献的抵扣额。</li>
 * </ol>
 * <p>这样「且/或」的实现就是纯粹的逻辑组合，不需要猜测返回值的含义。</p>
 *
 * @author 课程大作业
 */
public abstract class CouponExpression {

    /**
     * 求本节点的「值」（供比较表达式使用）。
     *
     * @param context 上下文
     * @return 值；本节点没有值语义时返回 {@code null}
     */
    public BigDecimal value(CouponRuleInterpreter.CouponContext context) {
        return null;
    }

    /**
     * 求本节点对订单的「优惠贡献额」。
     *
     * @param context 上下文
     * @return 抵扣金额（未满足时为 0，两位小数）
     */
    public abstract BigDecimal contribute(CouponRuleInterpreter.CouponContext context);

    /**
     * 本节点的表达式文本（用于回显与报表）。
     *
     * @return 表达式文本，例如 {@code "满30减5"}
     */
    public abstract String text();

    /**
     * 树形描述（多行），用于控制台打印解析结果、课程报告展示 AST。
     *
     * @param indent 缩进前缀
     * @return 描述行列表
     */
    public List<String> describeTree(String indent) {
        List<String> lines = new ArrayList<>();
        lines.add(indent + getClass().getSimpleName() + " : " + text());
        return lines;
    }

    /**
     * 规范化金额：非负、两位小数、四舍五入。
     *
     * @param value 原始金额
     * @return 规范化金额
     */
    protected static BigDecimal money(BigDecimal value) {
        return value.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    /** 零元（避免到处 new）。 */
    protected static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    /**
     * 百分数解析工具：把「9折」的 9 还原为付费比例 0.9。
     *
     * @param rateInTenths 折率（9 表示 9 折，8.5 表示 8.5 折）
     * @return 付费比例，例如 0.9
     */
    protected static BigDecimal paidRate(BigDecimal rateInTenths) {
        return rateInTenths.divide(new BigDecimal("10"), 6, RoundingMode.HALF_UP);
    }

    // ==================================================================
    //                           终结符表达式
    // ==================================================================

    /**
     * 数字字面量（终结符表达式）。
     */
    public static final class NumberLiteral extends CouponExpression {

        /** 数值。 */
        private final BigDecimal number;

        /**
         * @param number 数值
         */
        public NumberLiteral(BigDecimal number) {
            this.number = number;
        }

        @Override
        public BigDecimal value(CouponRuleInterpreter.CouponContext context) {
            return number;
        }

        @Override
        public BigDecimal contribute(CouponRuleInterpreter.CouponContext context) {
            // 纯数字不产生优惠，只参与比较
            return ZERO;
        }

        @Override
        public String text() {
            return number.stripTrailingZeros().toPlainString();
        }

        /** @return 原始数值 */
        public BigDecimal number() {
            return number;
        }
    }

    /**
     * 会员等级终结符：求值为等级序号（NORMAL=0、GOLD=1、DIAMOND=2），
     * 因此「会员等级>=GOLD」可以退化成一次整数比较。
     */
    public static final class MemberLevelValue extends CouponExpression {

        @Override
        public BigDecimal value(CouponRuleInterpreter.CouponContext context) {
            return BigDecimal.valueOf(context.memberRank());
        }

        @Override
        public BigDecimal contribute(CouponRuleInterpreter.CouponContext context) {
            return ZERO;
        }

        @Override
        public String text() {
            return "会员等级";
        }
    }

    /**
     * 订单小计终结符：求值为订单小计的金额。
     */
    public static final class SubtotalValue extends CouponExpression {

        @Override
        public BigDecimal value(CouponRuleInterpreter.CouponContext context) {
            return context.subtotal();
        }

        @Override
        public BigDecimal contribute(CouponRuleInterpreter.CouponContext context) {
            return ZERO;
        }

        @Override
        public String text() {
            return "小计";
        }
    }

    // ==================================================================
    //                           非终结符表达式
    // ==================================================================

    /**
     * 满减表达式：{@code 满X减Y}。小计 ≥ X 时贡献 Y 元，否则贡献 0。
     */
    public static final class FullReductionExpression extends CouponExpression {

        /** 门槛金额。 */
        private final BigDecimal threshold;

        /** 减免金额。 */
        private final BigDecimal discount;

        /**
         * @param threshold 门槛
         * @param discount  减免
         */
        public FullReductionExpression(BigDecimal threshold, BigDecimal discount) {
            this.threshold = threshold;
            this.discount = discount;
        }

        @Override
        public BigDecimal contribute(CouponRuleInterpreter.CouponContext context) {
            return context.subtotal().compareTo(threshold) >= 0 ? money(discount) : ZERO;
        }

        @Override
        public String text() {
            return "满" + threshold.stripTrailingZeros().toPlainString()
                    + "减" + discount.stripTrailingZeros().toPlainString();
        }

        @Override
        public List<String> describeTree(String indent) {
            List<String> lines = new ArrayList<>();
            lines.add(indent + "FullReduction（满减）: " + text());
            lines.add(indent + "  ├─ 条件：小计 >= " + threshold.stripTrailingZeros().toPlainString() + " 元");
            lines.add(indent + "  └─ 满足时抵扣 " + discount.stripTrailingZeros().toPlainString() + " 元");
            return lines;
        }

        /** @return 门槛 */
        public BigDecimal threshold() {
            return threshold;
        }

        /** @return 减免额 */
        public BigDecimal discount() {
            return discount;
        }
    }

    /**
     * 折扣表达式：{@code 9折} 或 {@code 打9折}。
     *
     * <p>贡献额 = 小计 × (1 − 折率/10)；例如 9 折 → 小计 × 0.1。</p>
     */
    public static final class DiscountExpression extends CouponExpression {

        /** 折率（9 表示 9 折）。 */
        private final BigDecimal rateInTenths;

        /**
         * @param rateInTenths 折率
         */
        public DiscountExpression(BigDecimal rateInTenths) {
            this.rateInTenths = rateInTenths;
        }

        @Override
        public BigDecimal contribute(CouponRuleInterpreter.CouponContext context) {
            BigDecimal discount = context.subtotal()
                    .multiply(BigDecimal.ONE.subtract(paidRate(rateInTenths)));
            return money(discount);
        }

        @Override
        public String text() {
            return rateInTenths.stripTrailingZeros().toPlainString() + "折";
        }

        @Override
        public List<String> describeTree(String indent) {
            List<String> lines = new ArrayList<>();
            lines.add(indent + "Discount（折扣）: " + text());
            lines.add(indent + "  └─ 抵扣 = 小计 × (1 − "
                    + rateInTenths.stripTrailingZeros().toPlainString() + "/10)");
            return lines;
        }

        /** @return 折率 */
        public BigDecimal rateInTenths() {
            return rateInTenths;
        }
    }

    /**
     * 比较表达式：{@code 会员等级>=GOLD}、{@code 小计>=100}。
     *
     * <p>本身不产生优惠（贡献 0），只作为「且/或」的开关。</p>
     */
    public static final class CompareExpression extends CouponExpression {

        /** 左操作数。 */
        private final CouponExpression left;

        /** 运算符。 */
        private final String operator;

        /** 右操作数。 */
        private final CouponExpression right;

        /**
         * @param left     左操作数
         * @param operator 运算符（{@code >= <= == > <}）
         * @param right    右操作数
         */
        public CompareExpression(CouponExpression left, String operator, CouponExpression right) {
            this.left = left;
            this.operator = operator;
            this.right = right;
        }

        /**
         * 判断条件是否成立。
         *
         * @param context 上下文
         * @return 成立返回 true
         */
        public boolean test(CouponRuleInterpreter.CouponContext context) {
            BigDecimal a = left.value(context);
            BigDecimal b = right.value(context);
            if (a == null || b == null) {
                return false;
            }
            int cmp = a.compareTo(b);
            return switch (operator) {
                case ">=" -> cmp >= 0;
                case "<=" -> cmp <= 0;
                case ">" -> cmp > 0;
                case "<" -> cmp < 0;
                case "==", "=" -> cmp == 0;
                default -> false;
            };
        }

        @Override
        public BigDecimal contribute(CouponRuleInterpreter.CouponContext context) {
            return ZERO;
        }

        @Override
        public String text() {
            return left.text() + operator + right.text();
        }

        @Override
        public List<String> describeTree(String indent) {
            List<String> lines = new ArrayList<>();
            lines.add(indent + "Compare（比较）: " + text());
            lines.add(indent + "  ├─ 左：" + left.getClass().getSimpleName());
            lines.add(indent + "  └─ 右：" + right.text());
            return lines;
        }
    }

    /**
     * 「且」表达式：两侧都满足才生效；生效时取非零的一侧作为抵扣额。
     *
     * <p>业务语义：{@code 满30减5 且 会员等级>=GOLD} ——
     * 会员且满 30 才减 5 元。</p>
     */
    public static final class AndExpression extends CouponExpression {

        /** 左子表达式。 */
        private final CouponExpression left;

        /** 右子表达式。 */
        private final CouponExpression right;

        /**
         * @param left  左
         * @param right 右
         */
        public AndExpression(CouponExpression left, CouponExpression right) {
            this.left = left;
            this.right = right;
        }

        @Override
        public BigDecimal contribute(CouponRuleInterpreter.CouponContext context) {
            boolean leftOk = satisfied(left, context);
            boolean rightOk = satisfied(right, context);
            if (!leftOk || !rightOk) {
                return ZERO;
            }
            BigDecimal leftAmount = left.contribute(context);
            BigDecimal rightAmount = right.contribute(context);
            // 条件侧贡献为 0，金额侧才是真正的抵扣；两者都是金额时取较大者
            if (leftAmount.signum() > 0 && rightAmount.signum() > 0) {
                return leftAmount.max(rightAmount);
            }
            return leftAmount.signum() > 0 ? leftAmount : rightAmount;
        }

        @Override
        public String text() {
            return "(" + left.text() + " 且 " + right.text() + ")";
        }

        @Override
        public List<String> describeTree(String indent) {
            List<String> lines = new ArrayList<>();
            lines.add(indent + "And（且）:");
            lines.addAll(left.describeTree(indent + "  ├─ "));
            lines.addAll(right.describeTree(indent + "  └─ "));
            return lines;
        }
    }

    /**
     * 「或」表达式：任一侧满足即生效；生效时取较大的抵扣额（对用户更有利）。
     */
    public static final class OrExpression extends CouponExpression {

        /** 左子表达式。 */
        private final CouponExpression left;

        /** 右子表达式。 */
        private final CouponExpression right;

        /**
         * @param left  左
         * @param right 右
         */
        public OrExpression(CouponExpression left, CouponExpression right) {
            this.left = left;
            this.right = right;
        }

        @Override
        public BigDecimal contribute(CouponRuleInterpreter.CouponContext context) {
            boolean leftOk = satisfied(left, context);
            boolean rightOk = satisfied(right, context);
            if (!leftOk && !rightOk) {
                return ZERO;
            }
            BigDecimal leftAmount = left.contribute(context);
            BigDecimal rightAmount = right.contribute(context);
            return leftAmount.max(rightAmount);
        }

        @Override
        public String text() {
            return "(" + left.text() + " 或 " + right.text() + ")";
        }

        @Override
        public List<String> describeTree(String indent) {
            List<String> lines = new ArrayList<>();
            lines.add(indent + "Or（或）:");
            lines.addAll(left.describeTree(indent + "  ├─ "));
            lines.addAll(right.describeTree(indent + "  └─ "));
            return lines;
        }
    }

    /**
     * 判断子表达式是否「满足」。
     *
     * <p>规则：比较表达式看 {@link CompareExpression#test}；
     * 金额表达式（满减/折扣/且/或）看贡献额是否大于 0。</p>
     *
     * @param expression 子表达式
     * @param context    上下文
     * @return 满足返回 true
     */
    protected static boolean satisfied(CouponExpression expression,
                                       CouponRuleInterpreter.CouponContext context) {
        if (expression instanceof CompareExpression compare) {
            return compare.test(context);
        }
        return expression.contribute(context).signum() > 0;
    }
}
