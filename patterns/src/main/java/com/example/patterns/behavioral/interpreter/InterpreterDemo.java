package com.example.patterns.behavioral.interpreter;

import com.example.patterns.PatternDemo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 行为型模式 —— 解释器模式（Interpreter）：优惠券规则表达式求值。
 *
 * <p>用了什么模式：把优惠券规则从写死的 if-else 变成一门<b>小语言</b>，例如
 * {@code (满30减5 或 满50减10) 且 会员等级==DIAMOND}。文法里每条产生式对应一个表达式类
 * （终结符是叶子节点，非终结符是组合节点），由 {@link CouponRuleParser}（手写递归下降，无第三方库）
 * 把字符串翻译成表达式树，再由每个节点自己的 interpret 求值。</p>
 *
 * <p>为什么用：① 规则可配置，运营在后台改一行文案即可上线，不需要发版；② 规则可组合，
 * 「且 / 或 / 括号 / 比较」让简单规则拼出复杂规则，避免配置字段爆炸；③ 新增运算符只需新增一个表达式类，
 * 既有节点一行不改；④ AST 本身就是规则的可读解释，可打印给运营看，也可做抵扣上限等静态校验。</p>
 *
 * <p>不用会怎样：只能为每张券写一个类，或用一张「阈值 + 折扣率 + 会员门槛」的配置字段表；
 * 一旦出现「或」与嵌套组合，配置表就会膨胀，每加一种组合方式都要改代码，最终又回到难测的 if-else。</p>
 *
 * <p>文法（递归下降依据）：expr → orExpr → andExpr → compareExpr → primary；
 * primary → 括号 / 满X减Y / X折 / 数字；compareExpr 支持 &gt;= / == / &gt;。</p>
 *
 * <p>运行：{@code java com.example.patterns.behavioral.interpreter.InterpreterDemo}</p>
 */
public class InterpreterDemo implements PatternDemo {

    /** 演示用的 6 条规则文案（模拟运营在后台配置的规则串）。 */
    private static final String[] RULES = {
            "满30减5",
            "满50减10",
            "9折",
            "满30减5 且 会员等级>=GOLD",
            "满50减10 或 会员等级==DIAMOND",
            "(满30减5 或 满50减10) 且 会员等级==DIAMOND"
    };

    /** 求值场景：订单小计 + 会员等级。 */
    private static final Object[][] SCENARIOS = {
            {"28.00", MemberLevel.GOLD},
            {"60.00", MemberLevel.SILVER},
            {"60.00", MemberLevel.GOLD},
            {"120.00", MemberLevel.DIAMOND},
            {"120.00", MemberLevel.NONE}
    };

    @Override
    public String name() {
        return "解释器模式";
    }

    @Override
    public String code() {
        return "10";
    }

    @Override
    public String category() {
        return "行为型";
    }

    @Override
    public String description() {
        return "优惠券规则表达式求值：把「满30减5 且 会员等级>=GOLD」等规则解析成表达式树并按订单金额与会员等级求值";
    }

    /** 单独运行入口：与菜单运行共用同一份 run() 逻辑。 */
    public static void main(String[] args) {
        new InterpreterDemo().run();
    }

    @Override
    public void run() {
        System.out.println("""

                ================ 一、模式名 ================
                  解释器模式（Interpreter）—— 优惠券规则表达式求值

                ================ 二、参与角色 ================
                  [抽象表达式]   CouponExpression       ：interpret(ctx) 返回可抵扣金额；describe() 可读描述
                  [终结符表达式] SubtotalValue          ：小计
                  [终结符表达式] MemberLevelValue       ：会员等级（映射成序号参与比较）
                  [终结符表达式] NumberLiteral          ：数字字面量（如 30、5）
                  [非终结符]     FullReductionExpression ：满X减Y（Y 省略时表示纯门槛条件）
                  [非终结符]     DiscountExpression      ：X折（抵扣 = 小计 × (10-X)/10）
                  [非终结符]     AndExpression / OrExpression                ：且 / 或
                  [非终结符]     GreaterOrEqualExpression / EqualExpression  ：>= / ==
                  [解析器]       CouponRuleParser        ：手写递归下降，字符串 → 表达式树
                  [上下文]       CouponContext           ：subtotal + memberLevel，求值环境
                  [常量]         MemberLevel             ：NONE < SILVER < GOLD < DIAMOND

                ================ 三、调用过程 ================
                  [0] 文法：expr → orExpr → andExpr → compareExpr → primary（括号 / 满X减Y / X折 / 数字）
                      printAst() 用「前序遍历 + 缩进」打印 AST，一眼看懂嵌套层级。""");

        for (int i = 0; i < RULES.length; i++) {
            String rule = RULES[i];
            System.out.println("\n  [" + (i + 1) + "] 规则文案：" + rule);
            CouponExpression ast;
            try {
                ast = CouponRuleParser.parse(rule);
            } catch (RuntimeException ex) {
                System.out.println("      ✗ 解析失败（配置错误）：" + ex.getMessage());
                continue;
            }
            System.out.println("      ├─ 解析出的 AST（缩进树形）：");
            printAst(ast, "      │   ", true);
            System.out.println("      ├─ 归一化表达式：" + ast.describe());
            System.out.println("      ├─ 在不同订单金额 / 会员等级下的求值结果（抵扣不得超过小计）：");
            for (Object[] scenario : SCENARIOS) {
                BigDecimal subtotal = new BigDecimal(scenario[0].toString()).setScale(2, RoundingMode.HALF_UP);
                MemberLevel level = (MemberLevel) scenario[1];
                BigDecimal raw = ast.interpret(new CouponContext(subtotal, level));
                BigDecimal capped = capBySubtotal(raw, subtotal);
                String limit = raw.compareTo(capped) != 0 ? "（触发上限）" : "";
                System.out.println("      │   · 小计 " + money(subtotal) + " + 会员 " + pad(level.name())
                        + " → 可抵扣 " + money(raw) + limit + "，实付 " + money(subtotal.subtract(capped)));
            }
        }

        System.out.println("\n  [附加] 非法规则串的容错演示（解释器应给出可读错误而不是崩溃）：");
        for (String bad : new String[]{"满30减", "(满30减5 且 9折", "满30减5 且 "}) {
            try {
                System.out.println("      · 「" + bad + "」被解析为：" + CouponRuleParser.parse(bad).describe());
            } catch (RuntimeException ex) {
                System.out.println("      · 「" + bad + "」→ 配置错误：" + ex.getMessage());
            }
        }

        System.out.println("""

                ================ 四、结论 ================
                  1. 解释器模式 = 为一个小问题域定义文法，用类层次表示文法，用树表示句子。
                     终结符 → 叶子表达式；非终结符 → 组合表达式；interpret 就是对树做一次后序遍历求值。
                  2. 规则可配置：规则串存数据库/配置中心，运营改文案即可上线，代码不动、不发版。
                  3. 规则可组合：有「且 / 或 / 括号 / 比较」，简单规则能拼出复杂规则，避免配置字段爆炸。
                  4. 新增运算符只需新增一个表达式类 + 在解析器加一处分支，符合开闭原则。
                  5. 必须做抵扣上限 min(抵扣, 小计)，否则折扣表达式会算出负数实付，这是业务兜底。
                  6. 代价：复杂文法手写解析器成本高（本项目只覆盖算术+比较+逻辑三层）；规则极复杂时
                     工业界会用 ANTLR，思路一致 —— 都是「建树 + 遍历」；节点多、规则串长时性能不如
                     编译成字节码的专用引擎，一般需做 AST 缓存。
                  7. 与「组合模式」：AST 本身就是组合结构；与「访问者模式」：可为 AST 增加校验、
                     计价、可视化等新操作而不改节点类。

                  [演示结束] 解释器模式演示完毕。""");
    }

    // ==================== 上下文与常量 ====================

    /** 求值上下文：解释器需要的外部环境（订单小计 + 会员等级）。 */
    static class CouponContext {
        private final BigDecimal subtotal;
        private final MemberLevel memberLevel;

        CouponContext(BigDecimal subtotal, MemberLevel memberLevel) {
            this.subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);
            this.memberLevel = memberLevel;
        }

        BigDecimal getSubtotal() {
            return subtotal;
        }

        MemberLevel getMemberLevel() {
            return memberLevel;
        }

        /** 供 MemberLevelValue 使用：等级映射成可比较的序号。 */
        BigDecimal memberLevelOrdinal() {
            return new BigDecimal(memberLevel.ordinal());
        }
    }

    /** 会员等级：枚举顺序即权益高低，序号用于 >= / == 比较。 */
    enum MemberLevel {
        NONE, SILVER, GOLD, DIAMOND
    }

    // ==================== 词法 ====================

    /** 词法单元：type 为类别，text 为原始文本。 */
    record Token(String type, String text) {
        static final String NUM = "NUM";
        static final String WORD = "WORD";
        static final String GE = "GE";
        static final String EQ = "EQ";
        static final String GT = "GT";
        static final String AND = "AND";
        static final String OR = "OR";
        static final String LPAREN = "LPAREN";
        static final String RPAREN = "RPAREN";
        static final String EOF = "EOF";
    }

    /**
     * 词法扫描器：把规则串切成 Token 序列。中文没有空格分词，这里用「运算符优先 + 连续汉字或字母成词」的
     * 简单策略：遇见运算符直接产出对应 Token，数字单独成 NUM，其余连续字母/汉字归为一个 WORD。
     */
    static class CouponLexer {
        private final String src;
        private int pos;

        CouponLexer(String src) {
            this.src = src;
        }

        /** 扫描全部 Token，末尾附一个 EOF 作为递归下降的终止哨兵。 */
        List<Token> tokenize() {
            List<Token> tokens = new ArrayList<>();
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (Character.isWhitespace(c)) {
                    pos++;
                } else if (c == '(' || c == '（') {
                    tokens.add(new Token(Token.LPAREN, "("));
                    pos++;
                } else if (c == ')' || c == '）') {
                    tokens.add(new Token(Token.RPAREN, ")"));
                    pos++;
                } else if (c == '>' && peek(1) == '=') {
                    tokens.add(new Token(Token.GE, ">="));
                    pos += 2;
                } else if (c == '=' && peek(1) == '=') {
                    tokens.add(new Token(Token.EQ, "=="));
                    pos += 2;
                } else if (c == '>') {
                    tokens.add(new Token(Token.GT, ">"));
                    pos++;
                } else if (c == '&' && peek(1) == '&') {
                    tokens.add(new Token(Token.AND, "&&"));
                    pos += 2;
                } else if (c == '|' && peek(1) == '|') {
                    tokens.add(new Token(Token.OR, "||"));
                    pos += 2;
                } else if (Character.isDigit(c)) {
                    tokens.add(new Token(Token.NUM, readNumber()));
                } else if (Character.isLetter(c)) {
                    tokens.add(new Token(Token.WORD, readWord()));
                } else {
                    tokens.add(new Token(Token.WORD, String.valueOf(c))); // 其他符号当关键字文字，避免死循环
                    pos++;
                }
            }
            tokens.add(new Token(Token.EOF, "<结束>"));
            return tokens;
        }

        private String readNumber() {
            int start = pos;
            boolean dot = false;
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (Character.isDigit(c)) {
                    pos++;
                } else if (c == '.' && !dot) {
                    dot = true;
                    pos++;
                } else {
                    break;
                }
            }
            return src.substring(start, pos);
        }

        private String readWord() {
            int start = pos;
            while (pos < src.length() && Character.isLetter(src.charAt(pos))) {
                pos++;
            }
            return src.substring(start, pos);
        }

        private char peek(int offset) {
            return pos + offset < src.length() ? src.charAt(pos + offset) : '\0';
        }
    }

    // ==================== 抽象表达式 ====================

    /**
     * 抽象表达式：所有节点都能「在自己的上下文里解释出抵扣金额」。
     * 约定：interpret 返回可抵扣金额；条件类节点用「0 = 不成立、正数 = 成立」参与逻辑运算。
     */
    interface CouponExpression {
        /** 求值：返回可抵扣金额（条件节点返回 0 或 1 作为真值）。 */
        BigDecimal interpret(CouponContext ctx);

        /** 归一化描述，用于打印与审计。 */
        String describe();

        /** 子节点列表，供打印 AST 使用（叶子返回空列表）。 */
        default List<CouponExpression> children() {
            return List.of();
        }
    }

    // ==================== 终结符表达式 ====================

    /** 终结符：小计。 */
    static class SubtotalValue implements CouponExpression {
        @Override
        public BigDecimal interpret(CouponContext ctx) {
            return ctx.getSubtotal();
        }

        @Override
        public String describe() {
            return "小计";
        }
    }

    /** 终结符：会员等级（返回等级序号）。 */
    static class MemberLevelValue implements CouponExpression {
        @Override
        public BigDecimal interpret(CouponContext ctx) {
            return ctx.memberLevelOrdinal();
        }

        @Override
        public String describe() {
            return "会员等级";
        }
    }

    /** 终结符：数字字面量。 */
    static class NumberLiteral implements CouponExpression {
        private final BigDecimal value;

        NumberLiteral(BigDecimal value) {
            this.value = value;
        }

        @Override
        public BigDecimal interpret(CouponContext ctx) {
            return value;
        }

        @Override
        public String describe() {
            return plain(value);
        }
    }

    // ==================== 非终结符表达式 ====================

    /**
     * 非终结符：满 X 减 Y。小计 ≥ X 时抵扣 Y，否则抵扣 0；Y 省略时按抵扣 0 处理
     * （即「仅作为门槛条件」），这样它能直接当作「且 / 或」的操作数。
     */
    static class FullReductionExpression implements CouponExpression {
        private final BigDecimal threshold;
        private final BigDecimal reduction;

        FullReductionExpression(BigDecimal threshold, BigDecimal reduction) {
            this.threshold = threshold;
            this.reduction = reduction == null ? BigDecimal.ZERO : reduction;
        }

        @Override
        public BigDecimal interpret(CouponContext ctx) {
            return ctx.getSubtotal().compareTo(threshold) >= 0 ? reduction : BigDecimal.ZERO;
        }

        @Override
        public String describe() {
            return reduction.signum() == 0 ? "满" + plain(threshold)
                    : "满" + plain(threshold) + "减" + plain(reduction);
        }
    }

    /** 非终结符：X 折。抵扣 = 小计 × (10 - X)/10，例如「9折」抵扣 = 小计 × 0.1。 */
    static class DiscountExpression implements CouponExpression {
        private final BigDecimal discount;

        DiscountExpression(BigDecimal discount) {
            this.discount = discount;
        }

        @Override
        public BigDecimal interpret(CouponContext ctx) {
            BigDecimal rate = BigDecimal.TEN.subtract(discount).divide(BigDecimal.TEN, 6, RoundingMode.HALF_UP);
            if (rate.signum() < 0) {
                rate = BigDecimal.ZERO; // 非法折扣（如 11 折）按不打折处理，保证业务安全
            }
            return ctx.getSubtotal().multiply(rate).setScale(2, RoundingMode.HALF_UP);
        }

        @Override
        public String describe() {
            return plain(discount) + "折（抵扣 = 小计 × " + plain(BigDecimal.TEN.subtract(discount)) + "/10）";
        }
    }

    /** 非终结符：逻辑「且」。两边都成立才成立，抵扣取两边较大值（用户只享受一次优惠，不叠加）。 */
    static class AndExpression implements CouponExpression {
        private final CouponExpression left;
        private final CouponExpression right;

        AndExpression(CouponExpression left, CouponExpression right) {
            this.left = left;
            this.right = right;
        }

        @Override
        public BigDecimal interpret(CouponContext ctx) {
            BigDecimal l = left.interpret(ctx);
            if (l.signum() == 0) {
                return BigDecimal.ZERO; // 短路：左边不成立
            }
            BigDecimal r = right.interpret(ctx);
            return r.signum() == 0 ? BigDecimal.ZERO : l.max(r);
        }

        @Override
        public String describe() {
            return "(" + left.describe() + " 且 " + right.describe() + ")";
        }

        @Override
        public List<CouponExpression> children() {
            return List.of(left, right);
        }
    }

    /** 非终结符：逻辑「或」。任一边成立即成立，抵扣取两边较大值。 */
    static class OrExpression implements CouponExpression {
        private final CouponExpression left;
        private final CouponExpression right;

        OrExpression(CouponExpression left, CouponExpression right) {
            this.left = left;
            this.right = right;
        }

        @Override
        public BigDecimal interpret(CouponContext ctx) {
            return left.interpret(ctx).max(right.interpret(ctx));
        }

        @Override
        public String describe() {
            return "(" + left.describe() + " 或 " + right.describe() + ")";
        }

        @Override
        public List<CouponExpression> children() {
            return List.of(left, right);
        }
    }

    /** 非终结符：大于等于比较（如 会员等级>=GOLD）。 */
    static class GreaterOrEqualExpression implements CouponExpression {
        private final CouponExpression left;
        private final BigDecimal threshold;

        GreaterOrEqualExpression(CouponExpression left, BigDecimal threshold) {
            this.left = left;
            this.threshold = threshold;
        }

        @Override
        public BigDecimal interpret(CouponContext ctx) {
            return left.interpret(ctx).compareTo(threshold) >= 0 ? BigDecimal.ONE : BigDecimal.ZERO;
        }

        @Override
        public String describe() {
            return left.describe() + ">=" + plain(threshold);
        }

        @Override
        public List<CouponExpression> children() {
            return List.of(left);
        }
    }

    /** 非终结符：相等比较（如 会员等级==DIAMOND）。 */
    static class EqualExpression implements CouponExpression {
        private final CouponExpression left;
        private final BigDecimal target;

        EqualExpression(CouponExpression left, BigDecimal target) {
            this.left = left;
            this.target = target;
        }

        @Override
        public BigDecimal interpret(CouponContext ctx) {
            return left.interpret(ctx).compareTo(target) == 0 ? BigDecimal.ONE : BigDecimal.ZERO;
        }

        @Override
        public String describe() {
            return left.describe() + "==" + plain(target);
        }

        @Override
        public List<CouponExpression> children() {
            return List.of(left);
        }
    }

    // ==================== 解析器：字符串 → 表达式树 ====================

    /**
     * 解析器：手写递归下降。表达式类只负责「怎么算」，解析器负责「怎么读」；
     * 按优先级 或 &lt; 且 &lt; 比较 &lt; 原子 逐层下降，天然处理了括号与优先级。
     */
    static class CouponRuleParser {
        private final List<Token> tokens;
        private int index;

        private CouponRuleParser(String source) {
            this.tokens = new CouponLexer(source).tokenize();
        }

        /**
         * 对外入口：把规则文案解析成表达式树。
         *
         * @throws IllegalArgumentException 规则串为空或语法错误时抛出，消息可直接展示给运营
         */
        static CouponExpression parse(String source) {
            if (source == null || source.isBlank()) {
                throw new IllegalArgumentException("规则串为空");
            }
            CouponRuleParser parser = new CouponRuleParser(source);
            CouponExpression root = parser.parseOr();
            if (!parser.check(Token.EOF)) {
                throw new IllegalArgumentException("规则串在「" + parser.look().text()
                        + "」处存在多余内容，请检查括号是否匹配或是否缺少运算符");
            }
            return root;
        }

        private CouponExpression parseOr() {
            CouponExpression left = parseAnd();
            while (check(Token.OR) || checkWord("或")) {
                advance();
                left = new OrExpression(left, parseAnd());
            }
            return left;
        }

        private CouponExpression parseAnd() {
            CouponExpression left = parseCompare();
            while (check(Token.AND) || checkWord("且")) {
                advance();
                left = new AndExpression(left, parseCompare());
            }
            return left;
        }

        private CouponExpression parseCompare() {
            CouponExpression left = parsePrimary();
            if (check(Token.GE) || check(Token.EQ) || check(Token.GT)) {
                String operator = advance().type();
                if (!(left instanceof MemberLevelValue) && !(left instanceof SubtotalValue)) {
                    throw new IllegalArgumentException(
                            "比较运算符只能用于「小计」或「会员等级」，当前为「" + left.describe() + "」");
                }
                BigDecimal right = requireNumberOrLevel();
                if (Token.GE.equals(operator)) {
                    return new GreaterOrEqualExpression(left, right);
                }
                if (Token.EQ.equals(operator)) {
                    return new EqualExpression(left, right);
                }
                return new GreaterOrEqualExpression(left, right.add(BigDecimal.ONE)); // a > b 等价于 a >= b+1
            }
            return left;
        }

        /** 原子表达式：括号、满X减Y、X折、小计、会员等级、数字。 */
        private CouponExpression parsePrimary() {
            if (check(Token.LPAREN)) {
                advance();
                CouponExpression inner = parseOr();
                if (!check(Token.RPAREN)) {
                    throw new IllegalArgumentException("括号不匹配：缺少右括号「)」");
                }
                advance();
                return inner;
            }
            if (checkWord("满")) {
                advance();
                BigDecimal threshold = requireNumber("「满」之后");
                BigDecimal reduction = null;
                if (checkWord("减")) {
                    advance();
                    reduction = requireNumber("「减」之后");
                }
                return new FullReductionExpression(threshold, reduction);
            }
            if (check(Token.NUM) && checkType(1, Token.WORD) && "折".equals(peek(1).text())) {
                BigDecimal discount = new BigDecimal(advance().text());
                advance(); // 吃掉「折」
                return new DiscountExpression(discount);
            }
            if (checkWord("小计")) {
                advance();
                return new SubtotalValue();
            }
            if (checkWord("会员等级")) {
                advance();
                return new MemberLevelValue();
            }
            if (check(Token.NUM)) {
                return new FullReductionExpression(new BigDecimal(advance().text()), null); // 纯门槛条件
            }
            if (check(Token.EOF)) {
                throw new IllegalArgumentException("规则串意外结束：可能缺少操作数（例如「满30减」后面缺少数值）");
            }
            throw new IllegalArgumentException("无法识别的语法片段「" + look().text() + "」，"
                    + "支持写法：满30减5 / 9折 / 满30减5 且 会员等级>=GOLD / (…) 或 (…)");
        }

        /** 取一个数字。 */
        private BigDecimal requireNumber(String position) {
            if (!check(Token.NUM)) {
                throw new IllegalArgumentException(position + "应为一个数字，实际遇到「" + look().text() + "」");
            }
            return new BigDecimal(advance().text());
        }

        /** 比较运算符右侧：允许数字（用于小计）或会员等级名（用于会员等级）。 */
        private BigDecimal requireNumberOrLevel() {
            if (check(Token.NUM)) {
                return new BigDecimal(advance().text());
            }
            String word = Token.WORD.equals(look().type()) ? look().text() : "";
            for (MemberLevel level : MemberLevel.values()) {
                if (level.name().equalsIgnoreCase(word)) {
                    advance();
                    return new BigDecimal(level.ordinal());
                }
            }
            throw new IllegalArgumentException("比较运算符右侧应为数字或会员等级（NONE/SILVER/GOLD/DIAMOND），实际遇到「"
                    + look().text() + "」");
        }

        private boolean check(String type) {
            return look().type().equals(type);
        }

        private boolean checkType(int offset, String type) {
            return peek(offset).type().equals(type);
        }

        private boolean checkWord(String word) {
            return look().type().equals(Token.WORD) && look().text().equals(word);
        }

        private Token look() {
            return tokens.get(index);
        }

        private Token peek(int offset) {
            return tokens.get(Math.min(index + offset, tokens.size() - 1));
        }

        private Token advance() {
            return tokens.get(index++);
        }
    }

    // ==================== 输出辅助 ====================

    /** 打印 AST（缩进树形）：用「当前节点 + 子节点」递归，节点自身通过 children() 暴露子节点。 */
    private static void printAst(CouponExpression node, String prefix, boolean isLast) {
        System.out.println(prefix + (isLast ? "└─ " : "├─ ") + node.getClass().getSimpleName() + " " + node.describe());
        List<CouponExpression> children = node.children();
        String childPrefix = prefix + (isLast ? "   " : "│  ");
        for (int i = 0; i < children.size(); i++) {
            printAst(children.get(i), childPrefix, i == children.size() - 1);
        }
    }

    /** 抵扣上限：抵扣金额不得超过订单小计，且不允许为负。 */
    private static BigDecimal capBySubtotal(BigDecimal raw, BigDecimal subtotal) {
        BigDecimal value = raw == null ? BigDecimal.ZERO : raw;
        return value.signum() < 0 ? BigDecimal.ZERO : value.min(subtotal).setScale(2, RoundingMode.HALF_UP);
    }

    /** 金额格式化：统一两位小数、四舍五入（用 CNY 前缀避免控制台编码问题）。 */
    private static String money(BigDecimal value) {
        return "CNY " + value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** 数字去尾零展示，避免打印出 "30.00" 这种与文案不一致的形式。 */
    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    /** 右填充，保证控制台列对齐。 */
    private static String pad(String text) {
        StringBuilder sb = new StringBuilder(text);
        while (sb.length() < 8) {
            sb.append(' ');
        }
        return sb.toString();
    }
}
