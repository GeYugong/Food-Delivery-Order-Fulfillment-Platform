package com.example.delivery.bootstrap;

import com.example.delivery.domain.coupon.Coupon;
import com.example.delivery.domain.merchant.Dish;
import com.example.delivery.domain.merchant.Merchant;
import com.example.delivery.domain.order.Order;
import com.example.delivery.domain.order.OrderItem;
import com.example.delivery.domain.order.OrderSnapshot;
import com.example.delivery.domain.order.OrderStatus;
import com.example.delivery.domain.payment.Payment;
import com.example.delivery.domain.payment.PaymentMethod;
import com.example.delivery.domain.rider.Rider;
import com.example.delivery.domain.user.User;

import java.math.BigDecimal;
import java.util.List;

/**
 * 领域模型冒烟演示（阶段一入口）。
 *
 * <p><b>作用：</b>在业务服务与设计模式尚未展开之前，先用纯领域对象跑通
 * 「下单 → 加菜 → 应用优惠 → 提交 → 支付 → 派单 → 取餐 → 送达」这条主干，
 * 验证 {@link Order} 聚合的不变量、状态迁移矩阵、金额计算与快照/复制是否可靠。</p>
 *
 * <p><b>本类演示的设计模式（对应实现见后续阶段）：</b></p>
 * <ul>
 *   <li>建造者模式：{@code Order.OrderBuilder} 组装订单；</li>
 *   <li>状态模式：{@code OrderStatus} 迁移矩阵守住每一次状态流转；</li>
 *   <li>原型模式：{@code copyAsDraft} 实现「再来一单」；</li>
 *   <li>备忘录模式：{@code snapshot()/restore()} 实现状态回滚。</li>
 * </ul>
 *
 * <p>运行方式（仓库根目录，先 {@code mvn -q package}）：</p>
 * <pre>
 *   java -cp delivery-platform\target\classes com.example.delivery.bootstrap.OrderDomainGuide
 * </pre>
 *
 * @author 课程大作业
 */
public final class OrderDomainGuide {

    /** 工具类不允许实例化。 */
    private OrderDomainGuide() {
    }

    /**
     * 主方法：串起一整条订单业务主干，并把每步结果打印到控制台。
     *
     * @param args 未使用
     */
    public static void main(String[] args) {
        printTitle("外卖订单履约平台 · 领域模型冒烟演示（阶段一）");

        // ---------- 1. 准备基础数据（后续由抽象工厂统一创建） ----------
        User user = new User("U-1001", "张三", "13812348888",
                "杭州市西湖区文一西路 969 号", new BigDecimal("200.00"), User.MemberLevel.GOLD);
        Merchant merchant = new Merchant("M-2001", "川味小厨", "杭州市西湖区文三路 100 号",
                List.of(
                        Dish.available("DISH-001", "香辣鸡腿堡", "19.80", 50, "主食"),
                        Dish.available("DISH-002", "黄金薯条", "9.90", 100, "小食"),
                        Dish.available("DISH-003", "冰镇可乐", "6.50", 200, "饮品")),
                new BigDecimal("20.00"), new BigDecimal("5.00"));
        Rider rider = Rider.idle("R-3001", "李骑手", "13900001111", "1.20", "4.90");
        Coupon coupon = Coupon.fullReduction("C-9001", "30", "5");

        section("1. 基础数据");
        System.out.println("  用户：" + user.describe());
        System.out.println("  商家：" + merchant.describe());
        System.out.println("  骑手：" + rider.describe());
        System.out.println("  优惠券：" + coupon.describe());

        // ---------- 2. 建造者模式：组装订单 ----------
        section("2. 建造者模式组装订单");
        Order order = new Order.OrderBuilder()
                .customer(user)
                .merchant(merchant)
                .deliveryAddress(user.address())
                .remark("不要香菜")
                .build();
        order.addItem(OrderItem.of("DISH-001", "香辣鸡腿堡", "19.80", 1));
        order.addItem(OrderItem.of("DISH-002", "黄金薯条", "9.90", 1));
        // 同一菜品再次添加会被自动合并为一行（数量累加）
        order.addItem(OrderItem.of("DISH-001", "香辣鸡腿堡", "19.80", 1));
        System.out.println("  订单号：" + order.orderId());
        order.items().forEach(item -> System.out.println("    · " + item.describe()));
        System.out.println("  菜品小计：" + order.subtotal().toPlainString() + " 元，共 " + order.totalQuantity() + " 份");

        // ---------- 3. 优惠与配送费（后续由解释器 + 策略计算，这里直接给结论） ----------
        section("3. 优惠抵扣与配送费");
        order.applyCoupon(coupon.couponId(), new BigDecimal("5.00"));
        order.setDeliveryFee(merchant.deliveryFee());
        System.out.println("  使用优惠券：" + coupon.name() + "，抵扣 " + order.discountAmount().toPlainString() + " 元");
        System.out.println("  配送费：" + order.deliveryFee().toPlainString() + " 元");
        System.out.println("  实付金额：" + order.totalAmount().toPlainString() + " 元");

        // ---------- 4. 备忘录模式：先存快照，再模拟误取消并回滚 ----------
        section("4. 备忘录模式：误操作回滚");
        OrderSnapshot snapshot = order.snapshot();
        System.out.println("  已保存快照：状态=" + snapshot.status().label()
                + "，明细行数=" + snapshot.itemLineCount());
        order.cancel("用户误点取消");
        System.out.println("  模拟误取消后状态：" + order.status().label()
                + "，取消原因=" + order.cancelReason().orElse("-"));
        order.restore(snapshot);
        System.out.println("  从快照回滚后状态：" + order.status().label());

        // ---------- 5. 状态模式：提交 → 支付 → 派单 → 取餐 → 送达 ----------
        section("5. 状态模式：完整流转");
        printStatus(order);
        order.submit();
        printStatus(order);

        // 支付适配（后续由适配器模式统一多渠道）
        order.bindPaymentMethod(PaymentMethod.WECHAT_PAY.name());
        Payment payment = Payment.success("WX" + System.currentTimeMillis(), order.orderId(),
                order.totalAmount(), PaymentMethod.WECHAT_PAY);
        order.markPaid(payment.method().name(), payment.tradeNo());
        System.out.println("  " + payment.describe());
        printStatus(order);

        // 派单（后续由中介者 + 命令 + 策略完成）
        order.assignRider(rider);
        System.out.println("  派单成功，骑手：" + rider.name() + "（" + rider.maskedPhone() + "）");
        printStatus(order);

        order.startDelivering();
        printStatus(order);
        order.complete();
        printStatus(order);

        // ---------- 6. 原型模式：再来一单 ----------
        section("6. 原型模式：再来一单");
        Order reorder = order.copyAsDraft("ORD-REORDER-01");
        System.out.println("  新订单：" + reorder.orderId() + "，状态=" + reorder.status().label()
                + "，复用明细 " + reorder.items().size() + " 行，实付=" + reorder.totalAmount().toPlainString() + " 元");
        System.out.println("  原订单状态未被影响：" + order.status().label());

        // ---------- 7. 非法流转必须被拦住 ----------
        section("7. 非法流转防御（终态不可再流转）");
        try {
            order.cancel("已完成订单还想取消");
        } catch (RuntimeException e) {
            System.out.println("  已按预期拦截：" + e.getMessage());
        }
        try {
            reorder.complete();
        } catch (RuntimeException e) {
            System.out.println("  已按预期拦截：" + e.getMessage());
        }

        // ---------- 8. 状态迁移矩阵总览 ----------
        section("8. 状态迁移矩阵");
        for (OrderStatus status : OrderStatus.values()) {
            System.out.printf("  %-10s 可取消防:%s, 终态:%s, 可加菜:%s, 下一状态=%s%n",
                    status.label(), status.cancellable(), status.isFinalState(),
                    status.canAddItems(), status.nextStatuses());
        }

        printTitle("演示结束：领域主干跑通");
    }

    /** 打印订单当前状态与关键金额。 */
    private static void printStatus(Order order) {
        System.out.printf("  [%s] 实付=%s 元，时间线：提交=%s 支付=%s 接单=%s 完成=%s%n",
                order.status().label(),
                order.totalAmount().toPlainString(),
                order.submittedAt().map(Object::toString).orElse("-"),
                order.paidAt().map(Object::toString).orElse("-"),
                order.assignedAt().map(Object::toString).orElse("-"),
                order.completedAt().map(Object::toString).orElse("-"));
    }

    /** 打印分节标题。 */
    private static void section(String title) {
        System.out.println();
        System.out.println("── " + title + " " + "─".repeat(Math.max(0, 58 - title.length())));
    }

    /** 打印大标题。 */
    private static void printTitle(String title) {
        System.out.println();
        System.out.println("=".repeat(64));
        System.out.println("  " + title);
        System.out.println("=".repeat(64));
    }
}
