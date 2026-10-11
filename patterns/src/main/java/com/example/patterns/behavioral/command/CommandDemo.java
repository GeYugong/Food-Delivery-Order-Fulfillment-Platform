package com.example.patterns.behavioral.command;

import com.example.patterns.PatternDemo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * 行为型模式 —— 命令模式（Command）：派单 / 撤单 / 改派 / 取消。
 *
 * <p>用了什么模式：把「一次配送操作」封装成对象 —— {@link OrderCommand} 同时携带请求参数与
 * 「怎么做（execute）」「怎么反悔（undo）」两份逻辑；调用者 {@link CommandInvoker} 不认识订单、也不认识骑手，
 * 只会执行命令、压入历史栈、撤销上一个命令。</p>
 *
 * <p>为什么用：① 请求对象化，请求从「方法调用」变成可保存、可传递、可排队的一等公民；② 天然支持撤销/重做，
 * 只要命令自己实现 undo，调用者压栈即可回滚；③ 宏命令可把多个命令打包成一次执行、一次撤销；
 * ④ 历史栈落盘就是操作日志，队列化就是任务调度。</p>
 *
 * <p>不用会怎样：只能在派单入口写 {@code if (type.equals("dispatch")) ... else if ...}，每加一种操作都要改调用方；
 * 撤销要么没实现，要么在业务层维护一套「反操作」映射表；宏操作与操作日志更无处安放。</p>
 *
 * <p>运行：{@code java com.example.patterns.behavioral.command.CommandDemo}</p>
 */
public class CommandDemo implements PatternDemo {

    @Override
    public String name() {
        return "命令模式";
    }

    @Override
    public String code() {
        return "09";
    }

    @Override
    public String category() {
        return "行为型";
    }

    @Override
    public String description() {
        return "派单中心的派单/撤单/改派/取消：请求对象化，支持撤销回滚、命令时间线与宏命令";
    }

    /** 单独运行入口：与菜单运行共用同一份 run() 逻辑。 */
    public static void main(String[] args) {
        new CommandDemo().run();
    }

    @Override
    public void run() {
        System.out.println("""

                ================ 一、模式名 ================
                  命令模式（Command）—— 派单中心的派单 / 撤单 / 改派 / 取消

                ================ 二、参与角色 ================
                  [命令接口] OrderCommand      ：name() / execute() / undo()
                  [接收者]   OrderReceiver     ：真正干活的订单对象，维护 orderId / rider / status
                  [具体命令] DispatchCommand   ：派单给骑手
                  [具体命令] RevokeCommand     ：撤销派单（撤回骑手）
                  [具体命令] ChangeRiderCommand：改派给另一名骑手（undo 恢复原骑手）
                  [具体命令] CancelOrderCommand：取消订单（undo 恢复原状态与原骑手）
                  [调用者]   CommandInvoker    ：执行命令并压入历史栈，支持 undoLast() 与 timeline()
                  [宏命令]   MacroCommand      ：组合多个命令，一次 execute / 一次 undo

                ================ 三、调用过程 ================""");
        OrderReceiver order = new OrderReceiver("OD20240517001");
        CommandInvoker invoker = new CommandInvoker();
        System.out.println("  [0] 初始状态：" + order.statusLine());

        System.out.println("\n  [1] 依次执行 4 个命令（调用者只认识 OrderCommand，不认识订单与骑手）：");
        invoker.execute(new DispatchCommand(order, "李强"));                       // ① 派单
        invoker.execute(new ChangeRiderCommand(order, "王敏"));                    // ② 改派
        invoker.execute(new RevokeCommand(order, "商家缺货，撤销派单"));            // ③ 撤单
        invoker.execute(new CancelOrderCommand(order, "用户主动取消"));            // ④ 取消
        System.out.println("      >>> 4 个命令执行完毕，订单状态：" + order.statusLine());

        System.out.println("\n  [2] 连续撤销（undoLast），验证状态是否完全回滚：");
        int step = 1;
        while (invoker.canUndo()) {
            OrderCommand undone = invoker.undoLast();
            System.out.println("      undo-" + (step++) + " 撤销命令[" + undone.name() + "] → " + order.statusLine());
        }
        boolean rolledBack = OrderReceiver.STATUS_WAIT_DISPATCH.equals(order.getStatus()) && order.getRider() == null;
        System.out.println("      >>> 历史栈已空，最终状态：" + order.statusLine());
        System.out.println("      >>> 与初始状态比较：" + (rolledBack ? "完全一致，回滚成功" : "不一致，回滚失败"));

        System.out.println("\n  [3] 命令时间线（调用者维护的历史栈，天然就是操作日志）：");
        CommandInvoker invoker2 = new CommandInvoker();
        invoker2.execute(new DispatchCommand(order, "赵磊"));
        invoker2.execute(new ChangeRiderCommand(order, "孙倩"));
        invoker2.execute(new CancelOrderCommand(order, "商家打烊"));
        invoker2.timeline();
        System.out.println("      >>> 把历史栈序列化落盘，就得到一份可直接回放的操作日志。");

        System.out.println("\n  [4] 宏命令：把「派单 + 改派 + 取消」打包成一个原子操作，一次执行、一次撤销");
        CommandInvoker invoker3 = new CommandInvoker();
        MacroCommand batch = new MacroCommand("高峰期异常订单处理包",
                new DispatchCommand(order, "周涛"),
                new ChangeRiderCommand(order, "吴迪"),
                new CancelOrderCommand(order, "宏内批量取消"));
        System.out.println("      >>> 宏执行前：" + order.statusLine());
        invoker3.execute(batch);
        System.out.println("      >>> 宏执行后：" + order.statusLine());
        invoker3.undoLast();
        System.out.println("      >>> 宏撤销后：" + order.statusLine());
        System.out.println("      >>> 宏命令按「逆序」撤销子命令，才能保证状态一致（先撤最后一步）。");

        System.out.println("""

                ================ 四、结论 ================
                  1. 命令模式把「请求」对象化：参数、执行逻辑、撤销逻辑被封在同一个对象里。
                  2. 因为请求是对象，所以可以撤销/重做（历史栈）、排队（队列）、记日志（落盘）、批量（宏命令）。
                  3. 调用者与接收者解耦：CommandInvoker 完全不知道 OrderReceiver 的存在，符合开闭原则。
                  4. 代价：类数量增加（每个操作一个命令类）；undo 需要保存足够旧状态，复杂时可配合备忘录模式。
                  5. 与「策略模式」的区别：策略通常无状态、无撤销；命令强调「一次请求的完整生命周期」。

                  [演示结束] 命令模式演示完毕。""");
    }

    // ==================== 命令接口 ====================

    /**
     * 命令接口。三个方法缺一不可：name() 供时间线打印，execute() 正向操作，undo() 反向操作。
     * 撤销能力是命令模式相对「回调 / 策略」最大的增量价值。
     */
    interface OrderCommand {
        String name();

        void execute();

        void undo();
    }

    // ==================== 接收者 ====================

    /**
     * 接收者：订单本身。它只知道「怎么改自己的字段」，不知道谁在调它、也不知道撤销栈的存在，
     * 因此可以被任意命令复用。
     */
    static class OrderReceiver {

        static final String STATUS_WAIT_DISPATCH = "待派单";
        static final String STATUS_WAIT_ACCEPT = "待骑手接单";
        static final String STATUS_CANCELLED = "已取消";

        private final String orderId;
        private String rider;
        private String status = STATUS_WAIT_DISPATCH;

        OrderReceiver(String orderId) {
            this.orderId = orderId;
        }

        public String getOrderId() {
            return orderId;
        }

        public String getRider() {
            return rider;
        }

        public String getStatus() {
            return status;
        }

        /** 派单：绑定骑手并把状态推进到「待骑手接单」。 */
        void assign(String newRider) {
            this.rider = newRider;
            this.status = STATUS_WAIT_ACCEPT;
            System.out.println("        接收者：订单 " + orderId + " 派给骑手【" + newRider + "】");
        }

        /** 撤单：撤回骑手，回到「待派单」。 */
        void revoke() {
            System.out.println("        接收者：订单 " + orderId + " 撤销派单，骑手【" + text(rider) + "】被释放");
            this.rider = null;
            this.status = STATUS_WAIT_DISPATCH;
        }

        /** 改派：直接替换骑手（旧骑手由调用方负责告知）。 */
        void changeRider(String newRider) {
            String old = this.rider;
            this.rider = newRider;
            if (!STATUS_CANCELLED.equals(status)) {
                this.status = STATUS_WAIT_ACCEPT;
            }
            System.out.println("        接收者：订单 " + orderId + " 改派，" + text(old) + " → 骑手【" + newRider + "】");
        }

        /** 取消订单：清空骑手并置为「已取消」。 */
        void cancel() {
            System.out.println("        接收者：订单 " + orderId + " 已取消（骑手【" + text(rider) + "】释放）");
            this.rider = null;
            this.status = STATUS_CANCELLED;
        }

        /** 恢复状态：供命令 undo 时精确还原快照。 */
        void restore(String rider, String status) {
            this.rider = rider;
            this.status = status;
            System.out.println("        接收者：订单 " + orderId + " 状态回滚为【" + status + "】，骑手=" + text(rider));
        }

        /** 单行状态描述。 */
        String statusLine() {
            return "订单 " + orderId + " | 状态=" + status + " | 骑手=" + text(rider);
        }

        private static String text(String rider) {
            return rider == null ? "无" : "骑手" + rider;
        }
    }

    // ==================== 具体命令 ====================

    /** 具体命令：派单。execute 前先保存接收者旧状态，undo 时还原（命令自身承担备忘录职责）。 */
    static class DispatchCommand implements OrderCommand {
        private final OrderReceiver receiver;
        private final String rider;
        private String snapshotRider;
        private String snapshotStatus;

        DispatchCommand(OrderReceiver receiver, String rider) {
            this.receiver = receiver;
            this.rider = rider;
        }

        @Override
        public String name() {
            return "派单→" + rider;
        }

        @Override
        public void execute() {
            this.snapshotRider = receiver.getRider();
            this.snapshotStatus = receiver.getStatus();
            System.out.println("      [执行] 命令[" + name() + "]");
            receiver.assign(rider);
        }

        @Override
        public void undo() {
            System.out.println("      [撤销] 命令[" + name() + "]");
            receiver.restore(snapshotRider, snapshotStatus);
        }
    }

    /** 具体命令：撤销派单。 */
    static class RevokeCommand implements OrderCommand {
        private final OrderReceiver receiver;
        private final String reason;
        private String snapshotRider;
        private String snapshotStatus;

        RevokeCommand(OrderReceiver receiver, String reason) {
            this.receiver = receiver;
            this.reason = reason;
        }

        @Override
        public String name() {
            return "撤单(" + reason + ")";
        }

        @Override
        public void execute() {
            this.snapshotRider = receiver.getRider();
            this.snapshotStatus = receiver.getStatus();
            System.out.println("      [执行] 命令[" + name() + "]");
            receiver.revoke();
        }

        @Override
        public void undo() {
            System.out.println("      [撤销] 命令[" + name() + "] 恢复撤销前的骑手与状态");
            receiver.restore(snapshotRider, snapshotStatus);
        }
    }

    /** 具体命令：改派骑手。 */
    static class ChangeRiderCommand implements OrderCommand {
        private final OrderReceiver receiver;
        private final String newRider;
        private String snapshotRider;
        private String snapshotStatus;

        ChangeRiderCommand(OrderReceiver receiver, String newRider) {
            this.receiver = receiver;
            this.newRider = newRider;
        }

        @Override
        public String name() {
            return "改派→" + newRider;
        }

        @Override
        public void execute() {
            this.snapshotRider = receiver.getRider();
            this.snapshotStatus = receiver.getStatus();
            System.out.println("      [执行] 命令[" + name() + "]");
            receiver.changeRider(newRider);
        }

        @Override
        public void undo() {
            System.out.println("      [撤销] 命令[" + name() + "] 恢复原骑手【"
                    + (snapshotRider == null ? "无" : snapshotRider) + "】");
            receiver.restore(snapshotRider, snapshotStatus);
        }
    }

    /** 具体命令：取消订单。 */
    static class CancelOrderCommand implements OrderCommand {
        private final OrderReceiver receiver;
        private final String reason;
        private String snapshotRider;
        private String snapshotStatus;

        CancelOrderCommand(OrderReceiver receiver, String reason) {
            this.receiver = receiver;
            this.reason = reason;
        }

        @Override
        public String name() {
            return "取消订单(" + reason + ")";
        }

        @Override
        public void execute() {
            this.snapshotRider = receiver.getRider();
            this.snapshotStatus = receiver.getStatus();
            System.out.println("      [执行] 命令[" + name() + "]");
            receiver.cancel();
        }

        @Override
        public void undo() {
            System.out.println("      [撤销] 命令[" + name() + "] 订单复活，状态恢复为【" + snapshotStatus + "】");
            receiver.restore(snapshotRider, snapshotStatus);
        }
    }

    /**
     * 宏命令：把一组命令当成一个命令使用。
     * undo 必须逆序撤销子命令，否则前一个子命令的 undo 会覆盖后一个子命令恢复的状态。
     */
    static class MacroCommand implements OrderCommand {
        private final String macroName;
        private final List<OrderCommand> children = new ArrayList<>();

        MacroCommand(String macroName, OrderCommand... commands) {
            this.macroName = macroName;
            Collections.addAll(children, commands);
        }

        @Override
        public String name() {
            return "宏命令[" + macroName + "]，含 " + children.size() + " 个子命令";
        }

        @Override
        public void execute() {
            System.out.println("      [执行] " + name());
            for (OrderCommand child : children) {
                child.execute();
            }
        }

        @Override
        public void undo() {
            System.out.println("      [撤销] " + name() + "（逆序撤销子命令）");
            for (int i = children.size() - 1; i >= 0; i--) {
                children.get(i).undo();
            }
        }
    }

    // ==================== 调用者 ====================

    /**
     * 调用者：只认识 {@link OrderCommand} 抽象。用双端队列当历史栈，execute 压栈、undoLast 弹栈并撤销；
     * 因为历史是命令对象的序列，很容易再扩展出重做栈、批量回滚、日志落盘。
     */
    static class CommandInvoker {

        private final Deque<OrderCommand> history = new ArrayDeque<>();

        /** 执行命令并记录历史。 */
        void execute(OrderCommand command) {
            command.execute();
            history.push(command);
        }

        /** 撤销最近一次命令；历史为空时返回 null。 */
        OrderCommand undoLast() {
            if (history.isEmpty()) {
                System.out.println("      [撤销] 历史栈为空，无可撤销命令");
                return null;
            }
            OrderCommand last = history.pop();
            last.undo();
            return last;
        }

        /** 是否还有可撤销的命令。 */
        boolean canUndo() {
            return !history.isEmpty();
        }

        /** 打印执行历史（时间线）。栈是 LIFO，展示时按执行先后正序输出。 */
        void timeline() {
            if (history.isEmpty()) {
                System.out.println("      (时间线为空)");
                return;
            }
            List<OrderCommand> ordered = new ArrayList<>(history);
            Collections.reverse(ordered);
            for (int i = 0; i < ordered.size(); i++) {
                System.out.println("      T+" + (i + 1) + " 执行命令：" + ordered.get(i).name());
            }
            System.out.println("      （当前栈深 " + history.size() + "，栈顶即下一次 undoLast 的目标）");
        }
    }
}
