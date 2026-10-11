package com.example.patterns.behavioral.state;
import com.example.patterns.PatternDemo;
import java.util.ArrayList;
import java.util.List;
/**
 * 状态模式（State）——订单状态机。
 *
 * <p><b>用了什么模式：</b>把订单状态迁移矩阵「对象化」。{@link OrderState} 是抽象状态，
 * 每个具体状态（草稿、待支付、已支付、已接单、配送中、已完成、已取消）只覆盖<b>自己允许</b>的操作，
 * 其余动作落到基类默认实现上直接抛异常；{@link OrderContext} 是上下文，持有当前状态并把动作委托给它。</p>
 *
 * <p><b>为什么用：</b>外卖订单的合法动作完全由当前状态决定：「草稿」可以提交，「配送中」不能取消，
 * 「已完成」什么都不能做。若写成 {@code switch (status)} 大分支，每新增一个状态都要回到那个
 * 巨大分支里改所有 case，极易漏改，改动风险集中在一个方法上，违反开闭原则。</p>
 *
 * <p><b>不用会怎样：</b>状态散成字符串 + 到处 if/switch 后，「某个状态允许哪些操作」这个知识被撕成碎片；
 * 新增「已退款」状态时忘记在取消分支里处理它，线上就会出现「已完成订单还能被取消」的脏数据。</p>
 *
 * <p>状态模式把每个状态的行为收进各自的类：新增状态 = 新增一个类，老状态一行不改，
 * 每个状态只关心自己的合法行为，非法行为由自己的继承体系拒绝。</p>
 *
 * @author 课程大作业
 */

public class StateDemo implements PatternDemo {
    @Override
    public String code() {
        return "14";
    }
    @Override
    public String category() {
        return "行为型";
    }
    @Override
    public String name() {
        return "状态模式";
    }
    @Override
    public String description() {
        return "外卖订单状态机：订单从草稿到完成的合法流转由当前状态自己决定，非法操作当场被拒绝";
    }
    /** 独立运行入口，满足「单独跑」与「菜单跑」结果一致。 */
    public static void main(String[] args) {
        new StateDemo().run();
    }
    @Override
    public void run() {
        System.out.println("==================================================");
        System.out.println("  [" + code() + "] " + name() + "（" + category() + "）");
        System.out.println("  " + description());
        System.out.println("==================================================");
        System.out.println();
        System.out.println("【参与角色】");
        System.out.println("  1) 抽象状态 State ：OrderState —— 声明 7 个动作，默认实现一律抛 IllegalStateException");
        System.out.println("  2) 具体状态 ×7    ：DraftState / PendingPaymentState / PaidState / AssignedState");
        System.out.println("                      / DeliveringState / CompletedState / CancelledState");
        System.out.println("  3) 上下文 Context ：OrderContext —— 持有当前状态、订单号、骑手与轨迹日志，动作全部委托给状态");
        System.out.println();
        System.out.println("【调用过程】");
        System.out.println();
        System.out.println("  ① 正常链路：草稿 → 待支付 → 已支付 → 已接单 → 配送中 → 已完成");
        OrderContext order = new OrderContext("FD20240607001");
        order.submit();
        order.pay();
        order.assignRider("骑手-小李");
        order.startDelivery();
        order.complete();
        System.out.println("     订单号=" + order.orderNo() + "，骑手=" + order.rider() + "，最终=" + order.statusText());
        System.out.println();
        System.out.println("  ② 再次尝试 cancel：被终态 CompletedState 自己拒绝");
        try {
            order.cancel();
        } catch (IllegalStateException ex) {
            System.out.println("     [已捕获] " + ex.getMessage());
        }
        System.out.println();
        System.out.println("  ③ 草稿直接 complete：被 DraftState 自己拒绝");
        OrderContext draft = new OrderContext("FD20240607002");
        System.out.println("     当前=" + draft.statusText() + "，非法尝试 complete()");
        try {
            draft.complete();
        } catch (IllegalStateException ex) {
            System.out.println("     [已捕获] " + ex.getMessage());
        }
        System.out.println("     拒绝后状态未被破坏：" + draft.statusText());
        System.out.println();
        System.out.println("  ④ 配送中试图 cancel：被 DeliveringState 自己拒绝");
        OrderContext delivering = new OrderContext("FD20240607003");
        delivering.submit();
        delivering.pay();
        delivering.assignRider("骑手-阿强");
        delivering.startDelivery();
        try {
            delivering.cancel();
        } catch (IllegalStateException ex) {
            System.out.println("     [已捕获] " + ex.getMessage());
        }
        System.out.println("     配送中仍可从正常路径收尾：");
        delivering.complete();
        System.out.println();
        System.out.println("  ⑤ 状态迁移矩阵（每个状态允许的操作）：");
        for (String row : MATRIX) {
            System.out.println("     " + row);
        }
        System.out.println("  ⑥ 上下文轨迹日志 log（共 " + order.log().size() + " 条）：");
        int index = 1;
        for (String line : order.log()) {
            System.out.println("     " + (index++) + ". " + line);
        }
        System.out.println();
        System.out.println("【结论】");
        System.out.println("  1) 状态模式把 switch(status) 的分支搬进各自的类：新增状态只需新增一个类，老状态一行不改，符合开闭原则。");
        System.out.println("  2) 每个状态只关心自己的合法行为，非法行为由基类默认抛出，规则不会漏在某个 case 里。");
        System.out.println("  3) 「谁允许这个动作」的知识从分散的 if/switch 收敛为一张可读的迁移矩阵，测试与答辩都能直接对照。");
        System.out.println("  4) 上下文只做委托与轨迹记录，不含任何业务判断，因此状态机可以被独立单测。");
    }
    /** 状态迁移矩阵：每行说明「状态 | 允许的操作」，与具体状态类的覆盖情况一一对应。 */
    private static final String[] MATRIX = {
        "+------------------+------------------------------------------+",
        "| 状态             | 允许的操作                               |",
        "+------------------+------------------------------------------+",
        "| 草稿             | submit(提交) / cancel(取消)              |",
        "| 待支付           | pay(支付) / cancel(取消)                 |",
        "| 已支付           | assignRider(指派骑手) / cancel(取消)     |",
        "| 已接单           | startDelivery(开始配送) / cancel(取消)   |",
        "| 配送中           | complete(完成)                           |",
        "| 已完成（终态）   | 无，所有操作抛 IllegalStateException     |",
        "| 已取消（终态）   | 无，所有操作抛 IllegalStateException     |",
        "+------------------+------------------------------------------+",
        "迁移路径：草稿 → 待支付 → 已支付 → 已接单 → 配送中 → 已完成；任意非终态均可 → 已取消"
    };
    // ==================================================================
    // 角色实现（同文件包级类，避免影响其他同学正在写的包）
    // ==================================================================
    /**
     * 上下文（Context）：订单本体，把全部动作委托给当前状态对象。
     *
     * <p>它自己<b>不含任何 if/switch 判断</b>，只做三件事：保存当前状态、把动作转给状态、
     * 在状态切换时替换引用并打印迁移日志。委托用函数式接口 {@link Transition} 完成，
     * 因此 6 个动作方法各只有一行，不存在复制粘贴。</p>
     */
    static class OrderContext {
        /** 一次动作的委托逻辑，由调用点提供，从而把「调用哪个状态方法」也推迟到调用点。 */
        @FunctionalInterface
        interface Transition {
            OrderState apply(OrderState state);
        }
        private final String orderNo;
        private final List<String> log = new ArrayList<>();
        private OrderState state;
        private String rider;
        OrderContext(String orderNo) {
            this.orderNo = orderNo;
            this.rider = "未分配";
            this.state = new DraftState();
            log.add("订单创建，初始状态=草稿");
        }
        void submit() {
            update("提交订单", s -> s.submit(this));
        }
        void pay() {
            update("支付", s -> s.pay(this));
        }
        void assignRider(String riderName) {
            update("指派骑手", s -> s.assignRider(this, riderName));
        }
        void startDelivery() {
            update("开始配送", s -> s.startDelivery(this));
        }
        void complete() {
            update("确认送达", s -> s.complete(this));
        }
        void cancel() {
            update("取消订单", s -> s.cancel(this));
        }
        /**
         * 委托模板：调用当前状态的对应方法，成功后替换状态并打印「已支付 → 已接单」这类迁移日志。
         *
         * <p>非法操作会由具体状态（或其基类默认实现）抛 {@code IllegalStateException}，
         * 此时状态引用不会被替换、也不写日志，订单对象保持在原状态不被破坏。</p>
         */
        private void update(String actionLabel, Transition transition) {
            OrderState previous = this.state;
            OrderState next = transition.apply(previous);
            this.state = (next == null) ? previous : next;
            log.add((previous == next)
                    ? actionLabel + "：仍在「" + next.name() + "」"
                    : actionLabel + "：" + previous.name() + " → " + next.name());
            System.out.println("     [" + actionLabel + "] " + previous.name() + " → " + next.name());
        }
        OrderState state() {
            return state;
        }
        String orderNo() {
            return orderNo;
        }
        /** 骑手字段由状态在合法的指派动作里写入。 */
        void setRider(String riderName) {
            this.rider = riderName;
        }
        String rider() {
            return rider;
        }
        List<String> log() {
            return new ArrayList<>(log);
        }
        String statusText() {
            return "订单 " + orderNo + "，状态=" + state.name() + "，骑手=" + rider;
        }
    }
    /**
     * 抽象状态（State）：声明全部合法动作，默认实现一律拒绝。
     *
     * <p>默认抛异常而不是静默返回自身，是为了让「非法迁移」在开发期立刻暴露，
     * 而不是悄悄写进数据库变成脏数据。具体状态只覆盖自己允许的操作。</p>
     */
    abstract static class OrderState {
        /** 状态中文名。 */
        abstract String name();
        OrderState submit(OrderContext ctx) {
            throw reject("submit");
        }
        OrderState pay(OrderContext ctx) {
            throw reject("pay");
        }
        OrderState assignRider(OrderContext ctx, String rider) {
            throw reject("assignRider");
        }
        OrderState startDelivery(OrderContext ctx) {
            throw reject("startDelivery");
        }
        OrderState complete(OrderContext ctx) {
            throw reject("complete");
        }
        OrderState cancel(OrderContext ctx) {
            throw reject("cancel");
        }
        /** 统一构造拒绝异常，消息格式固定便于演示对照。 */
        private IllegalStateException reject(String operation) {
            return new IllegalStateException("当前状态[" + name() + "]不允许该操作：" + operation);
        }
        @Override
        public String toString() {
            return name();
        }
    }
    /** 草稿：允许 submit（提交）、cancel（取消）。 */
    static class DraftState extends OrderState {
        @Override
        String name() { return "草稿"; }
        @Override
        OrderState submit(OrderContext ctx) {
            return new PendingPaymentState();
        }
        @Override
        OrderState cancel(OrderContext ctx) {
            return new CancelledState();
        }
    }
    /** 待支付：允许 pay（支付）、cancel（取消）。 */
    static class PendingPaymentState extends OrderState {
        @Override
        String name() { return "待支付"; }
        @Override
        OrderState pay(OrderContext ctx) {
            return new PaidState();
        }
        @Override
        OrderState cancel(OrderContext ctx) {
            return new CancelledState();
        }
    }
    /** 已支付：允许 assignRider（指派骑手）、cancel（取消）。 */
    static class PaidState extends OrderState {
        @Override
        String name() { return "已支付"; }
        @Override
        OrderState assignRider(OrderContext ctx, String rider) {
            ctx.setRider(rider);
            return new AssignedState();
        }
        @Override
        OrderState cancel(OrderContext ctx) {
            return new CancelledState();
        }
    }
    /** 已接单：允许 startDelivery（开始配送）、cancel（取消）。 */
    static class AssignedState extends OrderState {
        @Override
        String name() { return "已接单"; }
        @Override
        OrderState startDelivery(OrderContext ctx) {
            return new DeliveringState();
        }
        @Override
        OrderState cancel(OrderContext ctx) {
            return new CancelledState();
        }
    }
    /** 配送中：只允许 complete（确认送达），cancel 由自身继承体系拒绝。 */
    static class DeliveringState extends OrderState {
        @Override
        String name() { return "配送中"; }
        @Override
        OrderState complete(OrderContext ctx) {
            return new CompletedState();
        }
    }
    /** 已完成：终态，不覆盖任何动作，全部由基类拒绝。 */
    static class CompletedState extends OrderState {
        @Override
        String name() { return "已完成"; }
    }
    /** 已取消：终态，不覆盖任何动作，全部由基类拒绝。 */
    static class CancelledState extends OrderState {
        @Override
        String name() { return "已取消"; }
    }
}
