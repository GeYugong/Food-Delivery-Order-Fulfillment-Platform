package com.example.patterns.behavioral.mediator;

import com.example.patterns.PatternDemo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 行为型模式 —— 中介者模式（Mediator）：派单中心协调商家、骑手、订单。
 *
 * <p>用了什么模式：订单、商家、骑手三个同事对象彼此<b>互不引用</b>，它们只认识中介者
 * {@link DispatchMediator}；任何跨对象协作都以「事件」形式丢给中介者，由中介者决定通知谁、按什么顺序通知。
 * 输出里每条转发都打印成「A → 中介者 → B」，便于直观看到耦合被收拢。</p>
 *
 * <p>为什么用：① 把 N 个对象两两交互的网状结构（n(n-1)/2 组关系）压成星形结构（n 条链路）；
 * ② 同事类只关心自己的业务，不需要持有别人的引用，可独立复用与单测；
 * ③ 协作流程集中在中介者里，改流程只改一处，符合迪米特法则（最少知识原则）。</p>
 *
 * <p>不用会怎样：订单要持有商家和骑手、商家要持有骑手和订单、骑手要持有订单和商家 ——
 * 3 个同事两两调用就是 3 组关系、6 条有向通信链路；一旦「送达」流程要加一个「结算中心」同事，
 * 4 个对象就是 6 组关系、12 条链路，每加一个同事都要改所有人的代码。</p>
 *
 * <p>运行：{@code java com.example.patterns.behavioral.mediator.MediatorDemo}</p>
 */
public class MediatorDemo implements PatternDemo {

    @Override
    public String name() {
        return "中介者模式";
    }

    @Override
    public String code() {
        return "11";
    }

    @Override
    public String category() {
        return "行为型";
    }

    @Override
    public String description() {
        return "派单中心协调商家、骑手、订单三个同事：支付→商家接单→派给最空闲骑手→送达，全部经由中介者转发";
    }

    /** 单独运行入口：与菜单运行共用同一份 run() 逻辑。 */
    public static void main(String[] args) {
        new MediatorDemo().run();
    }

    @Override
    public void run() {
        System.out.println("""

                ================ 一、模式名 ================
                  中介者模式（Mediator）—— 派单中心协调商家、骑手、订单

                ================ 二、参与角色 ================
                  [抽象中介者] DispatchMediator ：register(Colleague) / notify(sender, event, data)
                  [具体中介者] DispatchCenter   ：维护同事列表与骑手池，负责全部事件路由
                  [抽象同事]   Colleague        ：持有 protected DispatchMediator mediator 与 name()
                  [具体同事]   OrderColleague   ：订单，只负责改自己的状态
                  [具体同事]   MerchantColleague：商家，接单/拒单/出餐
                  [具体同事]   RiderColleague   ：骑手，抢单/接单/到店/送达
                  [事件]       ORDER_PAID / MERCHANT_ACCEPTED / RIDER_ACCEPTED / RIDER_ARRIVED / ORDER_DELIVERED

                ================ 三、调用过程 ================""");
        DispatchCenter center = new DispatchCenter();
        OrderColleague order = new OrderColleague("OD20240517001", new BigDecimal("68.00"));
        MerchantColleague merchant = new MerchantColleague("川味小厨");
        RiderColleague riderA = new RiderColleague("李强");
        RiderColleague riderB = new RiderColleague("王敏");
        for (Colleague colleague : List.of(order, merchant, riderA, riderB)) {
            center.register(colleague);
        }
        System.out.println("  [0] 装配完成：4 个同事都只认识中介者，彼此之间没有任何直接引用");
        System.out.println("      " + order.referenceInfo());
        System.out.println("      " + merchant.referenceInfo());
        System.out.println("      " + riderA.referenceInfo());
        printCouplingCompare();

        System.out.println("\n  [1] 用户完成支付 → 订单只喊一声 ORDER_PAID，剩下由中介者安排");
        order.pay();

        System.out.println("\n  [2] 商家 decide(true)：接单，再把 MERCHANT_ACCEPTED 交给中介者派单");
        merchant.decide(true);

        System.out.println("\n  [3] 骑手接单 / 到店：骑手只上报事件，不关心订单与商家的细节");
        RiderColleague assigned = center.assignedRiderOf(order);
        assigned.accept();
        assigned.arrived();

        System.out.println("\n  [4] 商家出餐 + 骑手送达：ORDER_DELIVERED 由中介者统一收口");
        merchant.cookDone();
        assigned.delivered();
        System.out.println("\n  [5] 最终状态：" + order.statusLine());
        System.out.println("      " + merchant.statusLine() + "；" + riderA.statusLine() + "；" + riderB.statusLine());

        System.out.println("\n  [6] 异常分支：同一个中介者、同一批同事，换成「商家拒单」流程");
        OrderColleague order2 = new OrderColleague("OD20240517002", new BigDecimal("39.90"));
        MerchantColleague merchant2 = new MerchantColleague("川味小厨");
        RiderColleague riderC = new RiderColleague("赵磊");
        DispatchCenter center2 = new DispatchCenter();
        for (Colleague colleague : List.of(order2, merchant2, riderC)) {
            center2.register(colleague);
        }
        order2.pay();
        merchant2.decide(false);
        System.out.println("      最终状态：" + order2.statusLine());

        System.out.println("\n  [7] 扩展性演示：新增一个「结算中心」同事，只需 register 一次，既有同事代码一行不改");
        OrderColleague order3 = new OrderColleague("OD20240517003", new BigDecimal("88.00"));
        MerchantColleague merchant3 = new MerchantColleague("川味小厨");
        RiderColleague riderD = new RiderColleague("孙倩");
        DispatchCenter center3 = new DispatchCenter();
        for (Colleague colleague : List.of(order3, merchant3, riderD)) {
            center3.register(colleague);
        }
        order3.pay();
        merchant3.decide(true);
        center3.assignedRiderOf(order3).accept();
        System.out.println("      （若没有中介者，新增同事需要与既有 3 个同事都建立双向链路，总链路从 6 条涨到 12 条）");

        System.out.println("""

                ================ 四、结论 ================
                  1. 中介者模式把「多对多」的网状耦合改造成「多对一 + 一对多」的星形结构。
                  2. 同事之间零引用，只依赖抽象中介者；所有跨对象流程集中在中介者，改流程只改一处。
                  3. 通信链路数：不用中介者，3 个同事两两耦合 = 3 组关系 / 6 条有向链路；
                     用中介者 = 3 条链路（每个同事只连中介者）。
                  4. 代价：中介者容易膨胀成「上帝对象」，需要按业务域拆分（派单中心、结算中心、客服中心）；
                     中介者本身是单点，必须保证线程安全与可用性。
                  5. 与「观察者模式」的区别：观察者是广播式一对多、订阅者互不关心；中介者掌握协作流程与业务规则，
                     是「有大脑」的协调者；与「外观模式」的区别：外观是单向简化调用，不承载对象间双向交互。

                  [演示结束] 中介者模式演示完毕。""");
    }

    /** 打印「不用中介者 vs 用中介者」的耦合对比。 */
    private void printCouplingCompare() {
        int colleagues = 3; // 商家、骑手、订单
        int pairs = colleagues * (colleagues - 1) / 2;
        System.out.println("      ┌─ 不用中介者：3 个同事两两耦合 → " + pairs + " 组关系 / " + pairs * 2
                + " 条有向通信链路（订单⇄商家、商家⇄骑手、骑手⇄订单）");
        System.out.println("      └─ 用中介者：每个同事只连中介者 → " + colleagues + " 条链路，同事之间 0 条直接链路");
    }

    // ==================== 事件常量 ====================

    /** 事件名常量：用字符串常量而不是枚举，便于中间者按名路由与打印链路。 */
    static final class Events {
        static final String ORDER_PAID = "ORDER_PAID";
        static final String MERCHANT_ACCEPTED = "MERCHANT_ACCEPTED";
        static final String MERCHANT_REJECTED = "MERCHANT_REJECTED";
        static final String MERCHANT_COOK_DONE = "MERCHANT_COOK_DONE";
        static final String RIDER_ACCEPTED = "RIDER_ACCEPTED";
        static final String RIDER_ARRIVED = "RIDER_ARRIVED";
        static final String ORDER_DELIVERED = "ORDER_DELIVERED";

        private Events() {
        }
    }

    // ==================== 抽象中介者 ====================

    /**
     * 抽象中介者：定义「注册同事」与「接收事件」两个能力。
     * 同事只依赖这个接口，因此可以替换成「带风控的派单中心」而不动任何同事代码。
     */
    interface DispatchMediator {

        /** 注册同事。 */
        void register(Colleague colleague);

        /** 接收同事上报的事件，并由中介者决定后续转发。 */
        void notify(Colleague sender, String event, Object data);
    }

    // ==================== 抽象同事 ====================

    /**
     * 抽象同事：只持有中介者引用与自己的名字。
     * 关键点：这里没有任何指向其他同事的字段 —— 这正是中介者模式的价值所在。
     */
    abstract static class Colleague {

        /** 同事唯一认识的「外部对象」，由中介者的 register 注入。 */
        protected DispatchMediator mediator;

        /** 同事名称（打印转发链路时使用）。 */
        public abstract String name();

        /** 同事角色描述。 */
        public abstract String role();

        /** 上报事件给中介者，并打印「发送者 → 中介者」这一跳。 */
        protected void send(String event, Object data) {
            System.out.println("      " + role() + "【" + name() + "】→ 中介者：" + event
                    + (data == null ? "" : "（" + data + "）"));
            mediator.notify(this, event, data);
        }

        /** 描述自己持有哪些引用，用于证明「同事之间没有直接引用」。 */
        public String referenceInfo() {
            return role() + "【" + name() + "】只持有：DispatchMediator（没有其他同事的引用）";
        }
    }

    // ==================== 具体同事 ====================

    /** 具体同事：订单。只维护自身状态，跨对象动作全部委托给中介者。 */
    static class OrderColleague extends Colleague {

        private final String orderId;
        private final BigDecimal amount;
        private String status = "待支付";
        private String rider;

        OrderColleague(String orderId, BigDecimal amount) {
            this.orderId = orderId;
            this.amount = amount.setScale(2, RoundingMode.HALF_UP);
        }

        public String getOrderId() {
            return orderId;
        }

        @Override
        public String name() {
            return orderId;
        }

        @Override
        public String role() {
            return "订单";
        }

        /** 用户支付：只上报 ORDER_PAID。 */
        void pay() {
            updateStatus("已支付待商家接单", "金额 " + money(amount));
            send(Events.ORDER_PAID, orderId);
        }

        /** 状态更新（由中介者按流程驱动）。 */
        void updateStatus(String newStatus, String detail) {
            this.status = newStatus;
            System.out.println("      中介者 → 订单【" + orderId + "】：状态更新为「" + newStatus + "」（" + detail + "）");
        }

        /** 绑定骑手（由中介者派单后写入）。 */
        void bindRider(String riderName) {
            this.rider = riderName;
            System.out.println("      中介者 → 订单【" + orderId + "】：绑定骑手【" + riderName + "】");
        }

        /** 骑手已取餐。 */
        void pickedUp() {
            updateStatus("配送中", "骑手已取餐");
        }

        /** 送达。 */
        void delivered() {
            updateStatus("已送达", "骑手" + (rider == null ? "（未知）" : rider) + "已完成配送");
        }

        /** 取消（商家拒单或无人可派时）。 */
        void cancelled(String reason) {
            updateStatus("已取消", reason);
        }

        /** 单行状态。 */
        String statusLine() {
            return "订单 " + orderId + " | 状态=" + status + " | 骑手=" + (rider == null ? "无" : rider)
                    + " | 金额=" + money(amount);
        }
    }

    /** 具体同事：商家。只负责接单/拒单/出餐，并把结果作为事件上报。 */
    static class MerchantColleague extends Colleague {

        private final String shopName;
        private String status = "待接单";

        MerchantColleague(String shopName) {
            this.shopName = shopName;
        }

        @Override
        public String name() {
            return shopName;
        }

        @Override
        public String role() {
            return "商家";
        }

        /** 商家决策：接单或拒单。 */
        void decide(boolean accepted) {
            this.status = accepted ? "已接单，备餐中" : "已拒单";
            System.out.println("      商家【" + shopName + "】状态 → " + status);
            send(accepted ? Events.MERCHANT_ACCEPTED : Events.MERCHANT_REJECTED, accepted ? shopName : "菜品售罄");
        }

        /** 出餐完成。 */
        void cookDone() {
            this.status = "已出餐";
            System.out.println("      商家【" + shopName + "】状态 → " + status);
            send(Events.MERCHANT_COOK_DONE, shopName);
        }

        /** 单行状态。 */
        String statusLine() {
            return "商家 " + shopName + " | 状态=" + status;
        }
    }

    /** 具体同事：骑手。只上报自己的动作，不关心订单与商家的细节。 */
    static class RiderColleague extends Colleague {

        private final String riderName;
        private String status = "空闲";
        private String currentOrderId;
        private int finishedOrders;
        private int rejectCount;

        RiderColleague(String riderName) {
            this.riderName = riderName;
        }

        @Override
        public String name() {
            return riderName;
        }

        @Override
        public String role() {
            return "骑手";
        }

        /** 是否空闲（中介者派单时查询）。 */
        boolean isIdle() {
            return currentOrderId == null;
        }

        int getFinishedOrders() {
            return finishedOrders;
        }

        int getRejectCount() {
            return rejectCount;
        }

        /** 派单通知：由中介者调用。 */
        void notifyOffer(String orderId) {
            System.out.println("      中介者 → 骑手【" + riderName + "】：有新订单 " + orderId + "，请接单");
        }

        /** 接单。 */
        void accept() {
            this.status = "已接单，赶往商家";
            send(Events.RIDER_ACCEPTED, riderName);
        }

        /** 拒单。 */
        void refuse() {
            this.rejectCount++;
            this.currentOrderId = null;
            this.status = "空闲";
            send(Events.RIDER_ACCEPTED + ":REFUSE", riderName);
        }

        /** 到店。 */
        void arrived() {
            this.status = "已到店待取餐";
            send(Events.RIDER_ARRIVED, riderName);
        }

        /** 送达。 */
        void delivered() {
            this.status = "空闲";
            this.finishedOrders++;
            String finished = currentOrderId;
            this.currentOrderId = null;
            send(Events.ORDER_DELIVERED, finished);
        }

        /** 中介者写入的派单结果。 */
        void assign(String orderId) {
            this.currentOrderId = orderId;
            this.status = "已派单待接单";
        }

        /** 单行状态。 */
        String statusLine() {
            return "骑手 " + riderName + " | 状态=" + status + " | 当前订单="
                    + (currentOrderId == null ? "无" : currentOrderId) + " | 完单=" + finishedOrders;
        }
    }

    // ==================== 具体中介者 ====================

    /**
     * 具体中介者：派单中心。它是唯一知道「支付后通知商家、商家接单后派给最空闲骑手、
     * 骑手到店后通知商家、骑手送达后通知订单」的对象，所有协作流程都集中在 notify 里。
     */
    static class DispatchCenter implements DispatchMediator {

        /** 全部同事（按注册顺序保存，保证演示输出稳定）。 */
        private final List<Colleague> colleagues = new ArrayList<>();

        /** 骑手池：订单 ID → 骑手。 */
        private final Map<String, RiderColleague> assignment = new LinkedHashMap<>();

        /** 已拒绝过派单的骑手，重派时降低优先级。 */
        private final List<RiderColleague> refusedRiders = new ArrayList<>();

        @Override
        public void register(Colleague colleague) {
            colleagues.add(colleague);
            // 注入中介者：同事之间互不认识，但都认识中介者（真实项目里通常由构造函数或 Spring 注入）
            colleague.mediator = this;
            System.out.println("      [注册] " + colleague.role() + "【" + colleague.name() + "】已在中介者处登记");
        }

        @Override
        public void notify(Colleague sender, String event, Object data) {
            System.out.println("      中介者收到事件 [" + event + "]，发件人=" + sender.role()
                    + "【" + sender.name() + "】，开始路由……");
            switch (event) {
                case Events.ORDER_PAID -> onOrderPaid(sender);
                case Events.MERCHANT_ACCEPTED -> onMerchantAccepted(sender);
                case Events.MERCHANT_REJECTED -> onMerchantRejected(sender, data);
                case Events.MERCHANT_COOK_DONE -> onCookDone(sender);
                case Events.RIDER_ACCEPTED, Events.RIDER_ARRIVED -> onRiderProgress(sender, event);
                case Events.ORDER_DELIVERED -> onDelivered(sender);
                default -> System.out.println("      中介者：暂不处理事件 [" + event + "]");
            }
        }

        /** 规则一：订单支付成功 → 通知商家接单。 */
        private void onOrderPaid(Colleague sender) {
            MerchantColleague merchant = firstMerchant();
            if (merchant == null) {
                System.out.println("      中介者：没有可用商家，流程终止");
                return;
            }
            System.out.println("      转发链路：订单【" + sender.name() + "】→ 中介者 → 商家【" + merchant.name() + "】（请接单）");
        }

        /** 规则二：商家接单 → 派给最空闲的骑手。 */
        private void onMerchantAccepted(Colleague sender) {
            OrderColleague order = firstOrder();
            if (order == null) {
                System.out.println("      中介者：没有待派单订单，流程终止");
                return;
            }
            order.updateStatus("商家已接单，待骑手接单", "商家【" + sender.name() + "】已接单");
            RiderColleague rider = pickIdleRider();
            if (rider == null) {
                System.out.println("      中介者：骑手池暂无空闲骑手，订单转入「待重派」队列");
                order.updateStatus("待重派（暂无空闲骑手）", "稍后重试");
                return;
            }
            System.out.println("      选人策略：最空闲骑手 = 当前空闲中完单量最少者");
            System.out.println("      转发链路：商家【" + sender.name() + "】→ 中介者 → 骑手【" + rider.name() + "】（派单）");
            rider.assign(order.getOrderId());
            order.bindRider(rider.name());
            assignment.put(order.getOrderId(), rider);
            rider.notifyOffer(order.getOrderId());
        }

        /** 规则三：商家拒单 → 通知订单取消。 */
        private void onMerchantRejected(Colleague sender, Object data) {
            System.out.println("      转发链路：商家【" + sender.name() + "】→ 中介者 → 订单（拒单通知）");
            OrderColleague order = firstOrder();
            if (order != null) {
                order.cancelled("商家拒单：" + data);
            }
        }

        /** 规则四：商家出餐 → 通知骑手取餐。 */
        private void onCookDone(Colleague sender) {
            OrderColleague order = firstOrder();
            RiderColleague rider = order == null ? null : assignment.get(order.getOrderId());
            System.out.println("      转发链路：商家【" + sender.name() + "】→ 中介者 → 骑手【"
                    + (rider == null ? "未派单" : rider.name()) + "】（餐已出，可取餐）");
        }

        /** 规则五：骑手接单 → 通知商家备餐；骑手到店 → 通知商家交付并更新订单状态。 */
        private void onRiderProgress(Colleague sender, String event) {
            OrderColleague order = firstOrder();
            if (Events.RIDER_ACCEPTED.equals(event)) {
                System.out.println("      转发链路：骑手【" + sender.name() + "】→ 中介者 → 商家【"
                        + merchantName() + "】（骑手已接单，请备餐）");
                return;
            }
            System.out.println("      转发链路：骑手【" + sender.name() + "】→ 中介者 → 商家【" + merchantName() + "】（骑手到店取餐）");
            if (order != null) {
                order.pickedUp();
            }
        }

        /** 规则六：骑手送达 → 更新订单状态并释放骑手。 */
        private void onDelivered(Colleague sender) {
            OrderColleague order = firstOrder();
            System.out.println("      转发链路：骑手【" + sender.name() + "】→ 中介者 → 订单【"
                    + (order == null ? "未知" : order.name()) + "】（送达确认）");
            if (order != null) {
                order.delivered();
                assignment.remove(order.getOrderId());
            }
        }

        /** 最空闲骑手：优先选未拒单、当前空闲、完单量最少者。 */
        private RiderColleague pickIdleRider() {
            RiderColleague best = null;
            for (Colleague colleague : colleagues) {
                if (!(colleague instanceof RiderColleague rider) || !rider.isIdle() || refusedRiders.contains(rider)) {
                    continue;
                }
                if (best == null || rider.getFinishedOrders() < best.getFinishedOrders()
                        || (rider.getFinishedOrders() == best.getFinishedOrders()
                                && rider.getRejectCount() < best.getRejectCount())) {
                    best = rider;
                }
            }
            return best;
        }

        /** 查询某订单当前分配的骑手（演示中用于驱动骑手动作）。 */
        RiderColleague assignedRiderOf(OrderColleague order) {
            return assignment.get(order.getOrderId());
        }

        private OrderColleague firstOrder() {
            for (Colleague colleague : colleagues) {
                if (colleague instanceof OrderColleague order) {
                    return order;
                }
            }
            return null;
        }

        private MerchantColleague firstMerchant() {
            for (Colleague colleague : colleagues) {
                if (colleague instanceof MerchantColleague merchant) {
                    return merchant;
                }
            }
            return null;
        }

        private String merchantName() {
            MerchantColleague merchant = firstMerchant();
            return merchant == null ? "无商家" : merchant.name();
        }
    }

    /** 金额格式化：统一两位小数、四舍五入（用 CNY 前缀避免控制台编码问题）。 */
    static String money(BigDecimal value) {
        return "CNY " + value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
