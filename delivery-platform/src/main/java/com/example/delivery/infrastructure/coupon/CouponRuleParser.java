package com.example.delivery.infrastructure.coupon;

import java.math.BigDecimal;

/**
 * 优惠券规则的递归下降解析器（解释器模式的「语法分析」部分）。
 *
 * <p><b>为什么解释器模式通常要配一个解析器：</b>解释器模式本身描述的是
 * 「如何用对象表示语法并解释执行」，但真正把
 * {@code "满30减5 且 会员等级>=GOLD"} 这种文本变成表达式树的，是词法分析 + 语法分析。
 * 两者合起来才是一个可用的解释器实现（否则只能手工 new 表达式对象，运营无法配置规则）。</p>
 *
 * <p><b>递归下降（Recursive Descent）核心思想：</b>文法里每条产生式写成一个方法，
 * 方法之间递归调用；<b>运算符优先级由调用层次决定</b>——</p>
 * <pre>
 *   parseExpression → parseOr       （或：优先级最低，最外层）
 *                   → parseAnd      （且：优先级较高）
 *                   → parsePrimary  （括号 / 原子条件：优先级最高）
 * </pre>
 *
 * <p><b>支持的文法：</b></p>
 * <pre>
 *   expr      := orExpr
 *   orExpr    := andExpr (("或" | "||") andExpr)*
 *   andExpr   := primary (("且" | "并且" | "&amp;&amp;") primary)*
 *   primary   := "(" expr ")" | condition
 *   condition := "满" NUM "减" NUM                      满减
 *              | ("打" NUM "折") | (NUM "折")           折扣
 *              | "会员等级" CMP NUM                     等级比较（右侧为等级序号 0/1/2）
 *              | "小计" CMP NUM                         小计比较
 *              | NUM CMP NUM                            常量比较
 *   CMP       := "&gt;=" | "&lt;=" | "==" | "&gt;" | "&lt;" | "="
 * </pre>
 *
 * <p><b>容错与可诊断性：</b>解析失败时抛出带字符位置的
 * {@link IllegalArgumentException}，例如「第 8 个字符附近：缺少关键字「减」」，
 * 这样运营配置错规则时能自助定位，而不是拿到一句「解析失败」。</p>
 *
 * @author 课程大作业
 */
class CouponRuleParser {

    /** 规则原文。 */
    private final String source;

    /** 当前扫描位置（字符下标）。 */
    private int pos;

    /**
     * @param source 规则原文
     */
    CouponRuleParser(String source) {
        this.source = source;
        this.pos = 0;
    }

    // ==================================================================
    //                            文法入口
    // ==================================================================

    /**
     * 解析整个表达式。
     *
     * @return 表达式树根节点
     */
    CouponExpression parseExpression() {
        return parseOr();
    }

    /**
     * 解析「或」。
     *
     * @return 表达式节点
     */
    private CouponExpression parseOr() {
        CouponExpression left = parseAnd();
        while (tryConsume("或") || tryConsume("||")) {
            CouponExpression right = parseAnd();
            left = new CouponExpression.OrExpression(left, right);
        }
        return left;
    }

    /**
     * 解析「且」。
     *
     * @return 表达式节点
     */
    private CouponExpression parseAnd() {
        CouponExpression left = parsePrimary();
        while (tryConsume("且") || tryConsume("并且") || tryConsume("&&")) {
            CouponExpression right = parsePrimary();
            left = new CouponExpression.AndExpression(left, right);
        }
        return left;
    }

    /**
     * 解析括号或原子条件。
     *
     * @return 表达式节点
     */
    private CouponExpression parsePrimary() {
        skipSpaces();
        if (tryConsume("(")) {
            CouponExpression inner = parseExpression();
            if (!tryConsume(")")) {
                throw error("缺少右括号 ')'");
            }
            return inner;
        }
        return parseCondition();
    }

    // ==================================================================
    //                            条件解析
    // ==================================================================

    /**
     * 解析一个原子条件。
     *
     * @return 表达式节点
     */
    private CouponExpression parseCondition() {
        skipSpaces();

        // 形态 1：满X减Y
        if (tryConsume("满")) {
            BigDecimal threshold = readNumber("满减门槛金额");
            requireKeyword("减");
            BigDecimal discount = readNumber("满减的减免金额");
            return new CouponExpression.FullReductionExpression(threshold, discount);
        }

        // 形态 2：打X折 / X折
        if (tryConsume("打")) {
            BigDecimal rate = readNumber("折扣率");
            requireKeyword("折");
            return new CouponExpression.DiscountExpression(rate);
        }

        // 形态 3：会员等级 >= N
        if (tryConsume("会员等级") || tryConsume("等级")) {
            String operator = readComparator();
            BigDecimal levelRank = readNumber("会员等级序号（NORMAL=0、GOLD=1、DIAMOND=2）");
            return new CouponExpression.CompareExpression(
                    new CouponExpression.MemberLevelValue(), operator,
                    new CouponExpression.NumberLiteral(levelRank));
        }

        // 形态 4：小计 >= X
        if (tryConsume("小计")) {
            String operator = readComparator();
            BigDecimal threshold = readNumber("小计阈值");
            return new CouponExpression.CompareExpression(
                    new CouponExpression.SubtotalValue(), operator,
                    new CouponExpression.NumberLiteral(threshold));
        }

        // 形态 5：以数字开头 —— 「9折」或「100 >= 50」
        BigDecimal number = readNumber("数值");
        // 注意：这里要「先看是不是折」，因为「折」字紧跟在数字后面
        if (tryConsume("折")) {
            return new CouponExpression.DiscountExpression(number);
        }
        String operator = tryReadComparator();
        if (operator != null) {
            BigDecimal right = readNumber("比较的右值");
            return new CouponExpression.CompareExpression(
                    new CouponExpression.NumberLiteral(number), operator,
                    new CouponExpression.NumberLiteral(right));
        }
        // 只有一个数字：语法允许，语义上不产生优惠（容错，不抛异常）
        return new CouponExpression.NumberLiteral(number);
    }

    // ==================================================================
    //                            词法工具
    // ==================================================================

    /** 跳过空白。 */
    private void skipSpaces() {
        while (pos < source.length() && Character.isWhitespace(source.charAt(pos))) {
            pos++;
        }
    }

    /**
     * 尝试消费一个关键字或符号（成功则前进指针）。
     *
     * @param token 关键字或符号
     * @return 是否消费
     */
    private boolean tryConsume(String token) {
        skipSpaces();
        if (source.startsWith(token, pos)) {
            pos += token.length();
            return true;
        }
        return false;
    }

    /**
     * 强制要求出现某关键字。
     *
     * @param keyword 关键字
     * @throws IllegalArgumentException 缺失时抛出
     */
    private void requireKeyword(String keyword) {
        if (!tryConsume(keyword)) {
            throw error("缺少关键字「" + keyword + "」");
        }
    }

    /**
     * 读取数字（支持小数）。
     *
     * @param what 错误提示用的语义名
     * @return 数值
     * @throws IllegalArgumentException 此处不是数字时抛出
     */
    private BigDecimal readNumber(String what) {
        skipSpaces();
        int start = pos;
        while (pos < source.length()) {
            char c = source.charAt(pos);
            if (Character.isDigit(c) || c == '.') {
                pos++;
            } else {
                break;
            }
        }
        if (start == pos) {
            throw error("期望" + what + "，但读到「" + restPreview() + "」");
        }
        String text = source.substring(start, pos);
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            throw error("非法数字：" + text);
        }
    }

    /**
     * 读取比较运算符（必须存在）。
     *
     * @return 运算符
     * @throws IllegalArgumentException 缺失时抛出
     */
    private String readComparator() {
        String operator = tryReadComparator();
        if (operator == null) {
            throw error("缺少比较运算符（>=、<=、==、>、<）");
        }
        return operator;
    }

    /**
     * 尝试读取比较运算符。
     *
     * @return 运算符；不存在时返回 null
     */
    private String tryReadComparator() {
        skipSpaces();
        // 先长后短，保证 ">=" 不会被误读成 ">"
        for (String candidate : new String[]{">=", "<=", "==", ">", "<", "="}) {
            if (source.startsWith(candidate, pos)) {
                pos += candidate.length();
                return "=".equals(candidate) ? "==" : candidate;
            }
        }
        return null;
    }

    /**
     * 断言表达式已解析完毕。
     *
     * @throws IllegalArgumentException 存在多余字符时抛出
     */
    void expectEnd() {
        skipSpaces();
        if (pos < source.length()) {
            throw error("表达式存在无法解析的多余内容：「" + restPreview() + "」");
        }
    }

    /**
     * 生成带位置信息的异常。
     *
     * @param message 错误说明
     * @return 异常
     */
    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(String.format(
                "优惠券规则解析失败（第 %d 个字符附近）：%s。原始规则=「%s」",
                pos + 1, message, source));
    }

    /**
     * 当前位置之后剩余文本的预览。
     *
     * @return 最多 12 个字符的预览
     */
    private String restPreview() {
        String rest = source.substring(Math.min(pos, source.length()));
        return rest.length() > 12 ? rest.substring(0, 12) + "…" : rest;
    }
}
