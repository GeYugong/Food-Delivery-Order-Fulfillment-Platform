package com.example.patterns.behavioral.chain;

import com.example.patterns.PatternDemo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 行为型模式 —— 职责链模式（Chain of Responsibility）：下单校验链。
 *
 * <p>用了什么模式：把「无菜品 → 未达起送价 → 库存不足 → 地址异常 → 下单过频」串成单向链表，
 * 每条规则是一个独立处理者；请求 {@link OrderContext} 从链头进入，可被处理，也可继续传给下一位。</p>
 *
 * <p>为什么用：① 单一职责，每个校验器只关心一件事；② 开闭原则，新增规则只需新写一个处理者并插到链上；
 * ③ 顺序可调，把频控放最前即可在进入昂贵校验前拦掉刷单；④ 调用方只认识链头，不关心链上有几个人、谁拦下了。</p>
 *
 * <p>不用会怎样：退化成下单方法里一大段 if / else if 堆叠 —— 加规则要改这个方法、顺序写死在代码里、
 * 想给某些商家跳过「起送价校验」只能再加开关，最终变成几百行的面条代码。</p>
 *
 * <p>运行：{@code java com.example.patterns.behavioral.chain.ChainOfResponsibilityDemo}</p>
 */
public class ChainOfResponsibilityDemo implements PatternDemo {

    @Override
    public String name() {
        return "职责链模式";
    }

    @Override
    public String code() {
        return "08";
    }

    @Override
    public String category() {
        return "行为型";
    }

    @Override
    public String description() {
        return "下单校验链：无菜品/未达起送价/库存不足/地址异常/下单过频逐环校验，链条可增删、顺序可调整";
    }

    /** 单独运行入口：与菜单运行共用同一份 run() 逻辑。 */
    public static void main(String[] args) {
        new ChainOfResponsibilityDemo().run();
    }

    @Override
    public void run() {
        System.out.println("""

                ================ 一、模式名 ================
                  职责链模式（Chain of Responsibility）—— 下单校验链

                ================ 二、参与角色 ================
                  [抽象处理者] OrderValidator        ：持有 next，提供 setNext / validate / passToNext
                  [具体处理者] EmptyOrderValidator   ：无菜品，直接否决
                  [具体处理者] MinimumAmountValidator：小计未达起送价，否决
                  [具体处理者] StockValidator        ：任一菜品库存不足，否决
                  [具体处理者] AddressValidator      ：地址为空或过短，否决
                  [具体处理者] FrequencyLimitValidator：同一用户 1 分钟内超过 3 单，否决
                  [请求对象]   OrderContext          ：用户/商家/菜品/金额/地址/最近下单次数 + 校验轨迹
                  [业务载体]   OrderReceipt          ：可选的库存来源（可变更的外部状态）

                ================ 三、调用过程 ================""");
        OrderReceipt receipt = OrderReceipt.sample();
        System.out.println("  [预备] 商家库存快照：" + receipt.snapshot());
        System.out.println("  [预备] 最近 1 分钟下单记录：U1001=2 单，U1003/U1004=0 单");

        // 构建链条：顺序即校验顺序；调用方只拿到链头，链上有几个人由装配处决定
        OrderValidator chain = buildStandardChain();
        System.out.println("  [0] 组装标准校验链：" + chain.chainName());

        System.out.println("\n  [场景 1] 全部通过：菜品齐全、小计 68.00、地址正常、该用户 1 分钟内只下过 1 单");
        runScenario(chain, new OrderContext("U1001", "川味小厨", List.of("水煮牛肉", "蒜泥白肉", "米饭"),
                new BigDecimal("68.00"), "上海市浦东新区张江路 88 号 3 号楼 502 室", 1), receipt);

        System.out.println("\n  [场景 2] 被起送价拦下：小计只有 18.00，低于商家起送价 20.00");
        runScenario(chain, new OrderContext("U1002", "川味小厨", List.of("米饭", "酸梅汤"),
                new BigDecimal("18.00"), "上海市徐汇区漕溪北路 100 号 A 座 1801", 0), receipt);

        System.out.println("\n  [场景 3] 被库存拦下：香辣蟹售罄，前两环已通过");
        runScenario(chain, new OrderContext("U1003", "川味小厨", List.of("香辣蟹", "米饭"),
                new BigDecimal("158.00"), "上海市静安区南京西路 1266 号 20 楼", 0), receipt);

        System.out.println("\n  [场景 4] 被频控拦下：同一用户 1 分钟内已下 4 单，本次是第 5 单");
        runScenario(chain, new OrderContext("U1001", "川味小厨", List.of("水煮牛肉", "米饭"),
                new BigDecimal("78.00"), "上海市浦东新区张江路 88 号 3 号楼 502 室", 4), receipt);

        System.out.println("\n  [场景 5] 证明「链条可增删、顺序可调整，调用方不用改」：");
        System.out.println("      A. 换一条链：频控提到最前，并移除地址校验（到店自取不需要地址）");
        // 换链只换装配顺序：处理者类一个没改，调用方式也一个没改
        OrderValidator light = new FrequencyLimitValidator(3);
        OrderValidator tail = linkToChain(light, new EmptyOrderValidator());
        tail = linkToChain(tail, new MinimumAmountValidator(new BigDecimal("20.00")));
        linkToChain(tail, new StockValidator());
        System.out.println("         新链顺序：" + light.chainName());
        System.out.println("         地址为空的订单在「旧链」上被地址校验否决：");
        runScenario(chain, new OrderContext("U1004", "川味小厨", List.of("水煮牛肉", "米饭"),
                new BigDecimal("78.00"), "", 0), receipt);
        System.out.println("         同一个 ctx 在「新链」上直接通过（新链没有地址校验者）：");
        runScenario(light, new OrderContext("U1004", "川味小厨", List.of("水煮牛肉", "米饭"),
                new BigDecimal("78.00"), "", 0), receipt);
        System.out.println("      B. 调用方代码完全没变：始终只有一句 chain.validate(ctx)，链的构成由装配处决定。");

        System.out.println("""

                ================ 四、结论 ================
                  1. 每个校验规则被封装成独立对象，规则之间互不知晓，只认识「下一位」。
                  2. 请求沿链流动，遇到第一个否决点即短路返回，省掉后续无效计算。
                  3. 新增规则 = 新增一个处理者 + 在装配处插入，符合开闭原则；调整顺序只需改装配顺序。
                  4. 代价：请求可能「无人处理」需自行兜底；链过长时逐环传递有开销，调试要顺着链走。
                  5. 与「装饰器」的区别：装饰器层层包裹并增强返回值；职责链可中途截断，也可以无人处理。

                  [演示结束] 职责链模式演示完毕。""");
    }

    /**
     * 组装标准链：无菜品 → 起送价 → 库存 → 地址 → 频控。
     *
     * <p>易踩的坑：不能写 a.setNext(b).setNext(c) 再指望它保留顺序 —— setNext 是「设置自己的下一位」，
     * 继续在同一个对象上调用会把前一次刚建立的链接覆盖掉，最后链上只剩「第一个 → 最后一个」。
     * 这里沿链尾追加，保证顺序与书写顺序一致。</p>
     */
    private static OrderValidator buildStandardChain() {
        OrderValidator head = new EmptyOrderValidator();
        OrderValidator tail = linkToChain(head, new MinimumAmountValidator(new BigDecimal("20.00")));
        tail = linkToChain(tail, new StockValidator());
        tail = linkToChain(tail, new AddressValidator(10));
        linkToChain(tail, new FrequencyLimitValidator(3));
        return head;
    }

    /** 把一个处理者接到链尾之后并返回新链尾；这样可无限追加而不会覆盖已有链接。 */
    private static OrderValidator linkToChain(OrderValidator tail, OrderValidator next) {
        tail.setNext(next);
        return tail.getNext();
    }

    /** 跑一个场景并打印本次校验的完整轨迹。 */
    private void runScenario(OrderValidator chain, OrderContext ctx, OrderReceipt receipt) {
        ctx.setReceipt(receipt);
        chain.validate(ctx);
        List<String> trace = ctx.getTrace();
        for (int i = 0; i < trace.size(); i++) {
            System.out.println("        (" + (i + 1) + ") " + trace.get(i));
        }
        System.out.println("      >>> 校验结果：" + (ctx.isApproved() ? "通过，允许下单" : "否决 —— " + ctx.getRejectReason()));
    }

    // ==================== 抽象处理者 ====================

    /**
     * 抽象处理者：只做两件事 —— 判断「我这一环是否放行」，以及放行后把请求交给下一位。
     * 它不引用任何具体业务服务，所以任何校验规则都能插进这条链。
     */
    abstract static class OrderValidator {

        /** 下一位处理者；null 表示本处理者是链尾。 */
        protected OrderValidator next;

        /** 设置下一位处理者，返回传入的 next（便于沿链向后接，见 linkToChain）。 */
        public OrderValidator setNext(OrderValidator next) {
            this.next = next;
            return next;
        }

        /** 读取下一位处理者（装配链条时用于定位链尾）。 */
        public OrderValidator getNext() {
            return next;
        }

        /** 校验入口：先做自身判断，通过则把请求传给下一位。 */
        public final void validate(OrderContext ctx) {
            if (!ctx.isApproved()) {
                return; // 已被上游否决，短路返回
            }
            check(ctx);
            if (ctx.isApproved()) {
                passToNext(ctx);
            }
        }

        /** 本处理者的具体校验逻辑，由子类实现。 */
        protected abstract void check(OrderContext ctx);

        /** 把请求交给下一位；没有下一位则说明整条链全部放行。 */
        protected void passToNext(OrderContext ctx) {
            if (next != null) {
                next.validate(ctx);
            } else {
                ctx.markChainFinished();
            }
        }

        /** 处理者名称，用于打印轨迹。 */
        protected abstract String roleName();

        /** 拼出整条链的可读名称，例如「无菜品校验 → 起送价校验」。 */
        protected String chainName() {
            StringBuilder sb = new StringBuilder(roleName());
            for (OrderValidator cursor = next; cursor != null; cursor = cursor.next) {
                sb.append(" → ").append(cursor.roleName());
            }
            return sb.toString();
        }

        /** 记录「通过」轨迹。 */
        protected void pass(OrderContext ctx) {
            ctx.addTrace("【通过】" + roleName() + "：" + passDetail(ctx));
        }

        /** 通过时的说明文案，子类可覆写。 */
        protected String passDetail(OrderContext ctx) {
            return "满足要求";
        }

        /** 记录「否决」轨迹并终止链条。 */
        protected void reject(OrderContext ctx, String reason) {
            ctx.addTrace("【否决】" + roleName() + "：" + reason);
            ctx.reject(reason);
        }
    }

    // ==================== 具体处理者 ====================

    /** 无菜品时否决。 */
    static class EmptyOrderValidator extends OrderValidator {
        @Override
        protected String roleName() {
            return "无菜品校验";
        }

        @Override
        protected void check(OrderContext ctx) {
            if (ctx.getDishNames().isEmpty()) {
                reject(ctx, "购物车为空，请先选择菜品");
            } else {
                pass(ctx);
            }
        }

        @Override
        protected String passDetail(OrderContext ctx) {
            return "共 " + ctx.getDishNames().size() + " 个菜品";
        }
    }

    /** 小计未达起送价时否决。 */
    static class MinimumAmountValidator extends OrderValidator {
        private final BigDecimal minimum;

        MinimumAmountValidator(BigDecimal minimum) {
            this.minimum = minimum;
        }

        @Override
        protected String roleName() {
            return "起送价校验";
        }

        @Override
        protected void check(OrderContext ctx) {
            if (ctx.getSubtotal().compareTo(minimum) < 0) {
                reject(ctx, "小计 " + money(ctx.getSubtotal()) + " 未达商家起送价 " + money(minimum));
            } else {
                pass(ctx);
            }
        }

        @Override
        protected String passDetail(OrderContext ctx) {
            return "小计 " + money(ctx.getSubtotal()) + " ≥ 起送价 " + money(minimum);
        }
    }

    /** 任一菜品库存不足时否决。 */
    static class StockValidator extends OrderValidator {
        @Override
        protected String roleName() {
            return "库存校验";
        }

        @Override
        protected void check(OrderContext ctx) {
            OrderReceipt receipt = ctx.getReceipt();
            if (receipt == null) {
                pass(ctx);
                return;
            }
            for (String dish : ctx.getDishNames()) {
                if (!receipt.hasStock(dish)) {
                    reject(ctx, "菜品「" + dish + "」已售罄（当前库存 " + receipt.stockOf(dish) + " 份）");
                    return;
                }
            }
            pass(ctx);
        }

        @Override
        protected String passDetail(OrderContext ctx) {
            return ctx.getDishNames().size() + " 个菜品全部有货";
        }
    }

    /** 收货地址为空或过短时否决。 */
    static class AddressValidator extends OrderValidator {
        private final int minLength;

        AddressValidator(int minLength) {
            this.minLength = minLength;
        }

        @Override
        protected String roleName() {
            return "地址校验";
        }

        @Override
        protected void check(OrderContext ctx) {
            String address = ctx.getAddress() == null ? "" : ctx.getAddress().trim();
            if (address.isEmpty()) {
                reject(ctx, "收货地址为空，骑手无法送达");
            } else if (address.length() < minLength) {
                reject(ctx, "收货地址「" + address + "」过于简略（少于 " + minLength + " 个字符），请补充门牌号");
            } else {
                pass(ctx);
            }
        }

        @Override
        protected String passDetail(OrderContext ctx) {
            return "地址有效（长度 " + ctx.getAddress().trim().length() + "）";
        }
    }

    /** 同一用户在 1 分钟内下单次数超限时否决（防刷单）。 */
    static class FrequencyLimitValidator extends OrderValidator {
        private final int maxPerMinute;

        FrequencyLimitValidator(int maxPerMinute) {
            this.maxPerMinute = maxPerMinute;
        }

        @Override
        protected String roleName() {
            return "频控校验";
        }

        @Override
        protected void check(OrderContext ctx) {
            int total = ctx.getRecentOrderCount() + 1; // 本次下单后窗口内的总单数
            if (total > maxPerMinute) {
                reject(ctx, "用户 " + ctx.getUserId() + " 1 分钟内第 " + total + " 单，超过上限 " + maxPerMinute + " 单，疑似刷单");
            } else {
                pass(ctx);
            }
        }

        @Override
        protected String passDetail(OrderContext ctx) {
            return "用户 " + ctx.getUserId() + " 1 分钟内第 " + (ctx.getRecentOrderCount() + 1) + " 单，未超限";
        }
    }

    // ==================== 请求对象与业务载体 ====================

    /**
     * 请求对象：一次下单请求的全部上下文，自带「结果状态 + 处理轨迹」，
     * 这样处理者之间不用互相回传参数，调用方也能事后复盘是哪一环拦下的。
     */
    static class OrderContext {
        private final String userId;
        private final String merchantName;
        private final List<String> dishNames;
        private final BigDecimal subtotal;
        private final String address;
        private final int recentOrderCount;
        private OrderReceipt receipt;          // 可选的库存载体
        private boolean approved = true;
        private String rejectReason;
        private final List<String> trace = new ArrayList<>();

        OrderContext(String userId, String merchantName, List<String> dishNames, BigDecimal subtotal,
                     String address, int recentOrderCount) {
            this.userId = Objects.requireNonNull(userId, "userId");
            this.merchantName = merchantName;
            this.dishNames = new ArrayList<>(dishNames);
            this.subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);
            this.address = address;
            this.recentOrderCount = recentOrderCount;
        }

        public String getUserId() {
            return userId;
        }

        public String getMerchantName() {
            return merchantName;
        }

        public List<String> getDishNames() {
            return Collections.unmodifiableList(dishNames);
        }

        public BigDecimal getSubtotal() {
            return subtotal;
        }

        public String getAddress() {
            return address;
        }

        public int getRecentOrderCount() {
            return recentOrderCount;
        }

        public OrderReceipt getReceipt() {
            return receipt;
        }

        public void setReceipt(OrderReceipt receipt) {
            this.receipt = receipt;
        }

        public boolean isApproved() {
            return approved;
        }

        public String getRejectReason() {
            return rejectReason;
        }

        public List<String> getTrace() {
            return Collections.unmodifiableList(trace);
        }

        void addTrace(String line) {
            trace.add(line);
        }

        void reject(String reason) {
            this.approved = false;
            this.rejectReason = reason;
        }

        void markChainFinished() {
            trace.add("【末端】已到达链尾，所有校验环节均放行");
        }
    }

    /** 业务载体：商家库存快照（可变的外部状态，由调用方注入而不是写死在处理者里）。 */
    static class OrderReceipt {
        private final Map<String, Integer> stock = new LinkedHashMap<>();

        static OrderReceipt sample() {
            OrderReceipt r = new OrderReceipt();
            r.stock.put("水煮牛肉", 12);
            r.stock.put("蒜泥白肉", 8);
            r.stock.put("米饭", 200);
            r.stock.put("酸梅汤", 50);
            r.stock.put("香辣蟹", 0); // 售罄，用于演示库存否决
            return r;
        }

        boolean hasStock(String dish) {
            return stockOf(dish) > 0;
        }

        int stockOf(String dish) {
            return stock.getOrDefault(dish, 0);
        }

        String snapshot() {
            return stock.toString();
        }
    }

    /** 金额格式化：统一两位小数、四舍五入（用 CNY 前缀避免控制台编码问题）。 */
    static String money(BigDecimal value) {
        return "CNY " + value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
