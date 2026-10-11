package com.example.patterns.behavioral.memento;

import com.example.patterns.PatternDemo;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 备忘录模式（Memento）——订单状态存档与回滚。
 *
 * <p><b>用了什么模式：</b>{@link OrderOriginator}（发起人）负责产生 {@link Memento}（备忘录），
 * {@link Caretaker}（负责人）只保存备忘录引用、负责什么时候存、什么时候取，却<b>永远不打开它看</b>。
 * 三个角色的边界非常清晰：状态内容的读写在发起人手里，时光机的调度在负责人手里，两者互不越界。</p>
 *
 * <p><b>为什么用：</b>外卖订单从「已下单 → 已支付 → 已接单 → 配送中」会不断变更内部状态。
 * 客服改错配送员、误加备注、误调优惠金额之后，需要「一键退回上一个状态」。
 * 如果让业务代码自己在外面保存 {@code oldStatus/oldRider/oldRemark/oldDiscount} 四个局部变量，
 * 每加一个新的可变字段就要改动所有存档点，稍不留神就漏字段、还原出「半新半旧」的脏对象。</p>
 *
 * <p><b>不用会怎样：</b>没有备忘录，回滚逻辑会散落在订单服务的各个方法里，形成重复且易错的
 * 「手工快照 + 手工还原」样板代码；一旦有人把「保存快照」和「修改状态」的顺序写反，回滚就永久失效。
 * 备忘录把「一个时刻的完整内部状态」收敛成一个对象，存与取都是整体操作，不会漏字段。</p>
 *
 * <p><b>与「命令模式撤销」的区别：</b>命令模式保存的是<b>操作</b>（做了什么、怎么逆操作），
 * 撤销靠执行反操作、重放历史；备忘录保存的是<b>对象状态快照</b>（那一刻长什么样），
 * 回滚靠整块替换状态，不需要知道当初是怎么改的。本 Demo 演示的是后者。</p>
 *
 * @author 课程大作业
 */
public class MementoDemo implements PatternDemo {

    /** 菜单短编号。 */
    @Override
    public String code() {
        return "12";
    }

    /** 分类：行为型。 */
    @Override
    public String category() {
        return "行为型";
    }

    /** 模式中文名。 */
    @Override
    public String name() {
        return "备忘录模式";
    }

    /** 对应外卖业务场景。 */
    @Override
    public String description() {
        return "外卖订单状态存档与回滚：把某一刻的订单状态整体存成快照，客服误操作后可逐步撤销";
    }

    /** 独立运行入口，满足「单独跑」与「菜单跑」结果一致。 */
    public static void main(String[] args) {
        new MementoDemo().run();
    }

    @Override
    public void run() {
        printTitle();
        printRoles();
        runProcess();
        printConclusion();
    }

    // ------------------------------------------------------------------
    // 分节打印
    // ------------------------------------------------------------------

    private void printTitle() {
        System.out.println("==================================================");
        System.out.println("  [" + code() + "] " + name() + "（" + category() + "）");
        System.out.println("  " + description());
        System.out.println("==================================================");
    }

    private void printRoles() {
        System.out.println();
        System.out.println("【参与角色】");
        System.out.println("  1) 发起人 Originator ：OrderOriginator —— 持有 status/rider/remark/discount，能存也能还原");
        System.out.println("  2) 备忘录 Memento    ：OrderOriginator.Memento —— 不可变快照，包级私有，只有发起人能读内部数据");
        System.out.println("  3) 负责人 Caretaker  ：Caretaker —— 用 Deque 保存快照，只调度存取，绝不窥探内容");
        System.out.println();
    }

    private void printConclusion() {
        System.out.println();
        System.out.println("【结论】");
        System.out.println("  1) 备忘录模式把「一个时刻的完整内部状态」封装成对象，回滚是整块替换，不会漏字段、不会半新半旧。");
        System.out.println("  2) 恢复不依赖任何中间操作，因此不需要保存「逆操作」，这是它与命令模式撤销的本质差异。");
        System.out.println("  3) 备忘录做成包级私有且不可变后，Caretaker 只能看见「一个快照句柄」，从类型上就杜绝了越界读写，");
        System.out.println("     封装边界由编译器保证，而不是靠开发者自觉。");
    }

    // ------------------------------------------------------------------
    // 演示流程
    // ------------------------------------------------------------------

    private void runProcess() {
        System.out.println("【调用过程】");
        OrderOriginator order = new OrderOriginator("FD20240607001");
        Caretaker caretaker = new Caretaker();
        System.out.println("  初始订单：" + order.stateText());

        System.out.println();
        System.out.println("  1) 连续修改 4 次状态，每次修改前调用 caretaker.backup() 存档；");
        caretaker.backup(order);
        order.pay();
        System.out.println("     修改① " + order.stateText());

        caretaker.backup(order);
        order.setRider("骑手-小王");
        System.out.println("     修改② " + order.stateText());

        caretaker.backup(order);
        order.setRemark("不要香菜，放在门口");
        System.out.println("     修改③ " + order.stateText());

        caretaker.backup(order);
        order.applyDiscount(new BigDecimal("6.00"));
        System.out.println("     修改④ " + order.stateText());

        System.out.println();
        System.out.println("  2) 打印存档历史：historySize=" + caretaker.historySize()
                + "，Caretaker 手里的快照总量，它不知道里面是什么；");

        System.out.println();
        System.out.println("  3) 连续 undo 2 次，观察状态逐步回滚：");
        caretaker.undo(order);
        System.out.println("     undo-1 之后 → " + order.stateText());
        caretaker.undo(order);
        System.out.println("     undo-2 之后 → " + order.stateText());

        System.out.println();
        System.out.println("  4) undoAll() 一次回到最初：");
        caretaker.undoAll(order);
        System.out.println("     undoAll 之后 → " + order.stateText());
        System.out.println("     历史剩余 = " + caretaker.historySize() + "（快照已全部消费）");

        System.out.println();
        System.out.println("  5) 把撤销过程作为轨迹一并展示：");
        List<String> timeline = new ArrayList<>();
        timeline.add("初始 | " + order.stateText());
        timeline.add("4 次修改 | 已支付 → 已接单 → 有备注 → 有优惠");
        timeline.add("undo ×2 | 退回「已接单、未备注、无优惠」");
        timeline.add("undoAll | 退回「已下单、未支付、无骑手、无备注、无优惠」");
        for (String line : timeline) {
            System.out.println("     - " + line);
        }
    }

    // ==================================================================
    // 角色实现（同文件包级类，避免影响其他同学正在写的包）
    // ==================================================================

    /**
     * 负责人（Caretaker）：只负责「什么时候存档、什么时候取档」。
     *
     * <p>它持有 {@code Deque<Memento>}，但从不读取备忘录里的 status/rider 等字段，
     * 因此订单内部结构怎么变都不会影响它。用栈（双端队列头部）实现「后进先出」的撤销顺序。</p>
     */
    static class Caretaker {

        /** 历史快照栈：栈顶是最近一次存档。 */
        private final Deque<OrderOriginator.Memento> history = new ArrayDeque<>();

        /** 备份：把发起人当前的完整内部状态压入栈顶。 */
        void backup(OrderOriginator originator) {
            history.push(originator.save());
        }

        /** 撤销一步：弹出栈顶快照，交还给发起人自行还原。 */
        void undo(OrderOriginator originator) {
            if (history.isEmpty()) {
                System.out.println("     [Caretaker] 没有可撤销的快照");
                return;
            }
            originator.restore(history.pop());
        }

        /** 一路撤销到底：把剩余快照全部按「后进先出」顺序还回去。 */
        void undoAll(OrderOriginator originator) {
            if (history.isEmpty()) {
                System.out.println("     [Caretaker] 没有可撤销的快照");
                return;
            }
            // 栈顶是最新的快照，栈底是最老的那次备份。一次退回最初，
            // 就等价于「取栈底的那一张快照还原」；中间的快照被整体丢掉，不再逐步消费。
            OrderOriginator.Memento oldest = history.getLast();
            history.clear();
            originator.restore(oldest);
        }

        /** 快照数量，只暴露数量、不暴露内容。 */
        int historySize() {
            return history.size();
        }
    }

    /**
     * 发起人（Originator）：订单本体，唯一有权读写备忘录内部数据的角色。
     */
    static class OrderOriginator {

        /** 订单号，不参与存档（业务主键不随快照回滚）。 */
        private final String orderId;

        private String status;
        private String rider;
        private String remark;
        private BigDecimal discount;

        OrderOriginator(String orderId) {
            this.orderId = orderId;
            this.status = "已下单";
            this.rider = "未分配";
            this.remark = "无";
            this.discount = BigDecimal.ZERO;
        }

        /** 存档：生成一个不可变快照，涵盖全部可回滚字段。 */
        Memento save() {
            return new Memento(status, rider, remark, discount);
        }

        /** 还原：整块覆盖内部状态，不需要知道中间执行过哪些操作。 */
        void restore(Memento memento) {
            if (memento == null) {
                return;
            }
            this.status = memento.status;
            this.rider = memento.rider;
            this.remark = memento.remark;
            this.discount = memento.discount;
        }

        void setStatus(String status) {
            this.status = status;
        }

        void pay() {
            this.status = "已支付";
        }

        void setRider(String rider) {
            this.rider = rider;
            this.status = "已接单";
        }

        void setRemark(String remark) {
            this.remark = remark;
        }

        void applyDiscount(BigDecimal discount) {
            this.discount = discount;
        }

        /** 当前状态的文字描述，用于打印与对比。 */
        String stateText() {
            return "订单号=" + orderId
                    + ", 状态=" + status
                    + ", 骑手=" + rider
                    + ", 备注=" + remark
                    + ", 优惠=￥" + discount;
        }

        /**
         * 备忘录（Memento）：不可变快照，且为<b>包级私有嵌套类</b>。
         *
         * <p>字段全部 final 且无 setter —— 快照一旦生成就永远不会被改写；
         * 构造器与字段均为 private / 包可见，因此只有同包的 {@link OrderOriginator} 能读取内部数据，
         * {@link Caretaker} 只能把它当成一个不透明的句柄来搬运。</p>
         */
        static final class Memento {

            private final String status;
            private final String rider;
            private final String remark;
            private final BigDecimal discount;

            private Memento(String status, String rider, String remark, BigDecimal discount) {
                this.status = status;
                this.rider = rider;
                this.remark = remark;
                this.discount = discount;
            }
        }
    }
}
