package com.example.delivery.domain.coupon;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * 优惠券领域模型。
 *
 * <p><b>核心设计模式：解释器模式（Interpreter）的「表达式载体」</b></p>
 * <p><b>为什么把规则存成字符串：</b>{@link #ruleExpression()} 保存的是可读的规则表达式，
 * 例如 {@code "满30减5"}、{@code "满50减10 且 会员等级>=GOLD"}、{@code "9折" }。
 * 解释器模式会把它解析成抽象语法树（终结符表达式 + 非终结符表达式）后求值。
 * 这样做的价值是：</p>
 * <ol>
 *   <li>运营改规则不用改代码、不用重新发版（把表达式配到数据库即可）；</li>
 *   <li>规则可组合：{@code 满减}、{@code 折扣}、{@code 且}、{@code 或} 都是表达式节点，
 *       新增一种运算符只需新增一个表达式类；</li>
 *   <li>课程演示时，可以边打印语法树边讲解递归下降解析。</li>
 * </ol>
 *
 * <p><b>其他关联模式：</b>原型模式（复制券生成新券）、建造者模式（券字段较多）、
 * 策略模式（券的叠加与优先级排序）。</p>
 *
 * @param couponId       券编号，例如 {@code "C-9001"}
 * @param name           券名称，例如 {@code "满30减5"}
 * @param ruleExpression 规则表达式（解释器模式的输入）
 * @param discountAmount 面额（元）；折扣券时表示最大抵扣额，可为 0
 * @param threshold      使用门槛（元），订单小计必须达到该金额
 * @param validFrom      生效日期
 * @param validTo        失效日期
 * @author 课程大作业
 */
public record Coupon(
        String couponId,
        String name,
        String ruleExpression,
        BigDecimal discountAmount,
        BigDecimal threshold,
        LocalDate validFrom,
        LocalDate validTo) {

    /** 紧凑构造器：校验不变量，缺省门槛视为 0 元。 */
    public Coupon {
        if (couponId == null || couponId.isBlank()) {
            throw new IllegalArgumentException("优惠券编号不能为空");
        }
        if (ruleExpression == null || ruleExpression.isBlank()) {
            throw new IllegalArgumentException("优惠券规则表达式不能为空（解释器模式需要它）");
        }
        discountAmount = discountAmount == null ? BigDecimal.ZERO : discountAmount.setScale(2, RoundingMode.HALF_UP);
        threshold = threshold == null ? BigDecimal.ZERO : threshold.setScale(2, RoundingMode.HALF_UP);
        if (discountAmount.signum() < 0 || threshold.signum() < 0) {
            throw new IllegalArgumentException("券面额与门槛不能为负数");
        }
        validFrom = validFrom == null ? LocalDate.now().minusDays(30) : validFrom;
        validTo = validTo == null ? LocalDate.now().plusDays(30) : validTo;
        if (validTo.isBefore(validFrom)) {
            throw new IllegalArgumentException("失效日期不能早于生效日期");
        }
    }

    /**
     * 便捷工厂：满减券。
     *
     * @param couponId 券编号
     * @param threshold 门槛（元，字符串形式）
     * @param discount  抵扣（元，字符串形式）
     * @return 满减券
     */
    public static Coupon fullReduction(String couponId, String threshold, String discount) {
        String rule = "满" + stripZero(threshold) + "减" + stripZero(discount);
        return new Coupon(couponId, rule, rule, new BigDecimal(discount),
                new BigDecimal(threshold), LocalDate.now().minusDays(1), LocalDate.now().plusDays(30));
    }

    /**
     * 便捷工厂：折扣券。
     *
     * @param couponId 券编号
     * @param rate     折扣率（例如 {@code "0.9"} 表示 9 折）
     * @param cap      最大抵扣额（元）
     * @return 折扣券
     */
    public static Coupon discount(String couponId, String rate, String cap) {
        String rule = stripZero(rate) + "折";
        return new Coupon(couponId, rule, rule, new BigDecimal(cap), BigDecimal.ZERO,
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(30));
    }

    /**
     * 判断券当前是否在有效期内。
     *
     * @return 有效返回 {@code true}
     */
    public boolean validOnToday() {
        LocalDate today = LocalDate.now();
        return !today.isBefore(validFrom) && !today.isAfter(validTo);
    }

    /**
     * 判断订单小计是否满足使用门槛。
     *
     * @param subtotal 订单小计
     * @return 满足返回 {@code true}
     */
    public boolean reachThreshold(BigDecimal subtotal) {
        return subtotal != null && subtotal.compareTo(threshold) >= 0;
    }

    /** @return 人类可读描述 */
    public String describe() {
        return String.format("%s[%s], 规则=%s, 门槛=%s 元, 有效期 %s ~ %s",
                name, couponId, ruleExpression, threshold.toPlainString(), validFrom, validTo);
    }

    /** 去掉小数末尾多余的 0，让规则表达式更好读（30.00 → 30）。 */
    private static String stripZero(String value) {
        return new BigDecimal(value).stripTrailingZeros().toPlainString();
    }
}
