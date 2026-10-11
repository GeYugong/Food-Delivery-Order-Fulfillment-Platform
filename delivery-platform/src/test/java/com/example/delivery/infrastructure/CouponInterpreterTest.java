package com.example.delivery.infrastructure;

import com.example.delivery.infrastructure.coupon.CouponExpression;
import com.example.delivery.infrastructure.coupon.CouponRuleInterpreter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 优惠券规则解释器的单元测试（解释器模式）。
 *
 * <p><b>为什么可以直接对「规则文本 + 小计 + 会员等级」求值：</b>
 * {@link CouponRuleInterpreter#evaluateForTest(String, BigDecimal, String)} 把解释过程需要的
 * 全局信息收敛成上下文（小明与会员等级），因此表达式树可以脱离 {@code Order} 独立测试——
 * 这正是解释器模式把「语法」与「业务对象」解耦后带来的可测性。</p>
 *
 * <p><b>实测行为说明：</b>求值结果有三重保护——
 * 非负 → 不得超过订单小计 → 保留两位小数（见 {@code evaluateDetailed}）。
 * 因此本测试既断言规则本身的语义，也断言封顶行为。</p>
 *
 * @author 课程大作业
 */
@DisplayName("优惠券规则解释器（解释器模式）")
class CouponInterpreterTest {

    /** 被测解释器（每个用例一个干净实例）。 */
    private CouponRuleInterpreter interpreter;

    /** 每个用例前新建解释器，避免上一次求值的说明与 AST 混入。 */
    @BeforeEach
    void setUp() {
        interpreter = new CouponRuleInterpreter();
    }

    /**
     * 便捷求值：把字符串小计与会员等级交给解释器。
     *
     * @param rule        规则文本
     * @param subtotal    小计（字符串形式）
     * @param memberLevel 会员等级名（NORMAL/GOLD/DIAMOND）
     * @return 抵扣金额（两位小数）
     */
    private BigDecimal evaluate(String rule, String subtotal, String memberLevel) {
        return interpreter.evaluateForTest(rule, new BigDecimal(subtotal), memberLevel);
    }

    // ==================================================================
    //                          用例分组
    // ==================================================================

    @Nested
    @DisplayName("满减表达式")
    class FullReduction {

        @Test
        @DisplayName("「满30减5」：小计 29.99 不满足门槛得 0.00，小计 30.00 得 5.00")
        void shouldEvaluateFullReductionBoundary() {
            assertAll(
                    () -> assertEquals(new BigDecimal("0.00"), evaluate("满30减5", "29.99", "NORMAL"),
                            "差 1 分未达门槛，不应产生抵扣"),
                    () -> assertEquals(new BigDecimal("5.00"), evaluate("满30减5", "30.00", "NORMAL"),
                            "刚好达到门槛应足额抵扣 5.00 元"),
                    () -> assertEquals(new BigDecimal("5.00"), evaluate("满30减5", "88.00", "NORMAL"),
                            "超过门槛仍是固定抵扣 5.00 元"));
        }

        @Test
        @DisplayName("「满50减10」：小计 50.00 得 10.00")
        void shouldEvaluateFullReductionFifty() {
            assertAll(
                    () -> assertEquals(new BigDecimal("10.00"), evaluate("满50减10", "50.00", "NORMAL"),
                            "达到门槛应抵扣 10.00 元"),
                    () -> assertEquals(new BigDecimal("0.00"), evaluate("满50减10", "49.99", "NORMAL"),
                            "未达门槛不抵扣"));
        }
    }

    @Nested
    @DisplayName("折扣表达式")
    class Discount {

        @Test
        @DisplayName("「9折」：100.00 → 10.00，56.00 → 5.60")
        void shouldEvaluateNinetyPercentDiscount() {
            assertAll(
                    () -> assertEquals(new BigDecimal("10.00"), evaluate("9折", "100.00", "NORMAL"),
                            "100 元打 9 折应抵扣 10.00 元"),
                    () -> assertEquals(new BigDecimal("5.60"), evaluate("9折", "56.00", "NORMAL"),
                            "56 元打 9 折应抵扣 5.60 元（两位小数四舍五入）"));
        }

        @Test
        @DisplayName("「8.5折」：100.00 → 15.00")
        void shouldEvaluateEightyFivePercentDiscount() {
            assertEquals(new BigDecimal("15.00"), evaluate("8.5折", "100.00", "NORMAL"),
                    "100 元打 8.5 折应抵扣 15.00 元");
        }

        @Test
        @DisplayName("折扣的抵扣额恒小于小计（不会出现负数实付）")
        void shouldNeverExceedSubtotal() {
            assertAll(
                    () -> assertTrue(evaluate("9折", "5.00", "NORMAL").compareTo(new BigDecimal("5.00")) < 0,
                            "9 折抵扣 0.50 元，小于小计"),
                    () -> assertTrue(evaluate("9折", "0.01", "NORMAL").signum() >= 0,
                            "极小金额下抵扣仍非负"));
        }
    }

    @Nested
    @DisplayName("且 / 或 组合表达式")
    class LogicalComposition {

        @Test
        @DisplayName("「满30减5 且 会员等级>=1」：GOLD 得 5.00，NORMAL 得 0.00")
        void shouldEvaluateAndWithMemberLevel() {
            assertAll(
                    () -> assertEquals(new BigDecimal("5.00"), evaluate("满30减5 且 会员等级>=1", "30.00", "GOLD"),
                            "黄金会员（等级 1）满足 >=1，应抵扣 5.00 元"),
                    () -> assertEquals(new BigDecimal("5.00"), evaluate("满30减5 且 会员等级>=1", "30.00", "DIAMOND"),
                            "钻石会员（等级 2）也满足 >=1"),
                    () -> assertEquals(new BigDecimal("0.00"), evaluate("满30减5 且 会员等级>=1", "30.00", "NORMAL"),
                            "普通会员（等级 0）不满足条件，不抵扣"),
                    () -> assertEquals(new BigDecimal("0.00"), evaluate("满30减5 且 会员等级>=1", "29.99", "GOLD"),
                            "会员满足但小计未达门槛，同样不抵扣"));
        }

        @Test
        @DisplayName("「满50减10 或 会员等级==2」：NORMAL 小计 50 得 10.00")
        void shouldEvaluateOrWithSubtotalCondition() {
            assertEquals(new BigDecimal("10.00"), evaluate("满50减10 或 会员等级==2", "50.00", "NORMAL"),
                    "满足满减一侧即抵扣 10.00 元");
        }

        @Test
        @DisplayName("「满50减10 或 会员等级==2」：DIAMOND 小计 10 实测得 0.00")
        void shouldEvaluateOrWithMemberConditionOnly() {
            // 实测行为（与直觉不同，以真实实现为准）：
            // 1) 小计 10.00 < 50.00，满减一侧不成立；
            // 2) 「会员等级==2」是纯比较表达式，它只作为逻辑开关，自身贡献额恒为 0.00；
            // 3) OrExpression 取两侧贡献额的较大者 → max(0.00, 0.00) = 0.00，
            //    所以不会因为「封顶到小计」而变成 10.00。
            BigDecimal discount = evaluate("满50减10 或 会员等级==2", "10.00", "DIAMOND");

            assertAll(
                    () -> assertEquals(new BigDecimal("0.00"), discount,
                            "只满足比较条件（无金额侧）时不产生抵扣"),
                    () -> assertEquals(new BigDecimal("10.00"), evaluate("满50减10 或 会员等级==2", "60.00", "DIAMOND"),
                            "小计达到 50 元时由满减一侧产生 10.00 元抵扣"));
        }

        @Test
        @DisplayName("「(满30减5 或 满50减10) 且 会员等级==2」：DIAMOND 60 得 10.00，GOLD 60 得 0.00")
        void shouldRespectParenthesesAndPrecedence() {
            assertAll(
                    () -> assertEquals(new BigDecimal("10.00"),
                            evaluate("(满30减5 或 满50减10) 且 会员等级==2", "60.00", "DIAMOND"),
                            "括号内取较大抵扣（10.00），且钻石会员条件成立"),
                    () -> assertEquals(new BigDecimal("0.00"),
                            evaluate("(满30减5 或 满50减10) 且 会员等级==2", "60.00", "GOLD"),
                            "黄金会员不满足 ==2，整条规则不生效"),
                    () -> assertEquals(new BigDecimal("5.00"),
                            evaluate("(满30减5 或 满50减10) 且 会员等级==2", "30.00", "DIAMOND"),
                            "小计 30 元时括号内只有满30减5 成立，抵扣 5.00 元"));
        }
    }

    @Nested
    @DisplayName("抵扣封顶保护")
    class Capping {

        @Test
        @DisplayName("「满50减10」在小计 5.00 时得 0.00（门槛未达）")
        void shouldReturnZeroWhenThresholdNotReached() {
            assertEquals(new BigDecimal("0.00"), evaluate("满50减10", "5.00", "NORMAL"),
                    "小计远低于门槛，抵扣应为 0.00 元");
        }

        @Test
        @DisplayName("抵扣额不得超过小计：规则「满0减100」在小计 5.00 时被封顶为 5.00")
        void shouldCapDiscountBySubtotal() {
            assertEquals(new BigDecimal("5.00"), evaluate("满0减100", "5.00", "NORMAL"),
                    "原始抵扣 100.00 元应被小计封顶为 5.00 元，避免实付为负");
        }

        @Test
        @DisplayName("任何规则在任何小计下：抵扣都非负且不超过小计")
        void shouldAlwaysStayWithinSubtotal() {
            List<String> rules = List.of(
                    "满30减5", "满50减10", "9折", "8.5折",
                    "满30减5 且 会员等级>=1", "满50减10 或 会员等级==2",
                    "(满30减5 或 满50减10) 且 会员等级==2");
            List<BigDecimal> subtotals = List.of(
                    new BigDecimal("0.01"), new BigDecimal("5.00"),
                    new BigDecimal("30.00"), new BigDecimal("100.00"));
            List<String> levels = List.of("NORMAL", "GOLD", "DIAMOND");

            for (String rule : rules) {
                for (BigDecimal subtotal : subtotals) {
                    for (String level : levels) {
                        BigDecimal discount = interpreter.evaluateForTest(rule, subtotal, level);
                        String scene = String.format("规则[%s] 小计=%s 等级=%s", rule,
                                subtotal.toPlainString(), level);
                        assertAll(
                                () -> assertTrue(discount.signum() >= 0, "抵扣不应为负：" + scene),
                                () -> assertTrue(discount.compareTo(subtotal) <= 0,
                                        "抵扣不得超过小计：" + scene + " 实际=" + discount.toPlainString()),
                                () -> assertEquals(2, discount.scale(), "抵扣金额应保留两位小数：" + scene));
                    }
                }
            }
        }
    }

    @Nested
    @DisplayName("解析（词法 + 语法 + AST）")
    class Parsing {

        @Test
        @DisplayName("非法规则抛 IllegalArgumentException，消息包含「解析失败」")
        void shouldRejectIllegalRule() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> interpreter.parse("满30减"), "缺少减免金额的规则应解析失败");

            assertAll(
                    () -> assertTrue(ex.getMessage().contains("解析失败"),
                            "异常消息应包含「解析失败」：" + ex.getMessage()),
                    () -> assertTrue(ex.getMessage().contains("满30减"),
                            "异常消息应回显原始规则，便于运营自助定位"));
        }

        @Test
        @DisplayName("括号不闭合、空规则等非法输入同样被拒")
        void shouldRejectMoreIllegalRules() {
            assertAll(
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> interpreter.parse("(满30减5"), "缺少右括号应解析失败"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> interpreter.parse("满30减5 或"), "运算符后缺表达式应解析失败"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> interpreter.parse("   "), "空规则应被拒绝"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> interpreter.parse(null), "null 规则应被拒绝"));
        }

        @Test
        @DisplayName("parse 返回的 AST：text() 与 describeTree() 都应回显「满30减5」")
        void shouldExposeAstText() {
            CouponExpression root = interpreter.parse("满30减5");

            assertAll(
                    () -> assertTrue(root.text().contains("满30减5"),
                            "AST 文本应包含规则原文：" + root.text()),
                    () -> assertTrue(String.join("\n", root.describeTree("")).contains("满30减5"),
                            "AST 树描述应包含规则原文"),
                    () -> assertTrue(root instanceof CouponExpression.FullReductionExpression,
                            "「满30减5」应被解析为满减表达式节点"));
        }

        @Test
        @DisplayName("复杂规则应解析成 And/Or 嵌套的表达式树")
        void shouldParseNestedAst() {
            CouponExpression root = interpreter.parse("(满30减5 或 满50减10) 且 会员等级==2");

            assertAll(
                    () -> assertTrue(root instanceof CouponExpression.AndExpression,
                            "最外层应为「且」节点"),
                    () -> assertTrue(root.text().contains("满30减5"), "AST 文本应包含左侧满减规则"),
                    () -> assertTrue(String.join("\n", root.describeTree("")).contains("Or（或）"),
                            "树描述应体现嵌套的「或」节点"));
        }

        @Test
        @DisplayName("内置示例规则都应能被解析")
        void shouldParseAllSampleRules() {
            List<String> samples = CouponRuleInterpreter.sampleRules();

            assertAll(
                    () -> assertTrue(samples.size() >= 7, "内置示例规则应有足够覆盖"),
                    () -> samples.forEach(rule -> assertTrue(interpreter.parse(rule) != null,
                            "示例规则应可解析：" + rule)));
        }
    }
}
