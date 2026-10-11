package com.example.delivery.domain.order;

import com.example.delivery.domain.rider.Rider;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 订单状态快照（不可变）。
 *
 * <p><b>设计模式：备忘录模式（Memento）的备忘录（Memento）角色</b></p>
 * <p><b>为什么用 record：</b>备忘录必须是不可变的，否则「保存的历史状态」会被后续修改污染，
 * 撤销就失去意义。record 天然满足不可变语义，且字段即契约，一目了然。</p>
 *
 * <p>发起人（Originator）= {@link Order}，负责 {@code snapshot()} 与 {@code restore()}；
 * 负责人（Caretaker）= 应用层/仓储，只负责保存快照、从不在快照上做业务判断。</p>
 *
 * <p><b>关键设计决定：快照必须覆盖订单的全部可变状态</b>（含各环节时间戳与取消原因），
 * 否则「回滚」只会回滚一半——例如状态回退到待支付，但 {@code cancelReason} 仍然残留，
 * 快照就不再是「某一时刻的完整状态」，撤销语义被破坏。这是本类字段看起来较多的根本原因。</p>
 *
 * @param orderId         订单号
 * @param status          快照时刻的订单状态
 * @param items           快照时刻的明细列表
 * @param couponId        使用的优惠券编号
 * @param discountAmount  优惠抵扣金额
 * @param deliveryFee     配送费
 * @param paymentMethod   支付方式标识
 * @param paymentTradeNo  支付流水号
 * @param riderId         骑手编号（未派单为 null）
 * @param rider           骑手对象（未派单为 null）；跨进程存档时可只保留 riderId
 * @param deliveryAddress 收货地址
 * @param remark          用户备注
 * @param submittedAt     提交时刻
 * @param paidAt          支付时刻
 * @param assignedAt      接单时刻
 * @param completedAt     完成时刻
 * @param cancelledAt     取消时刻
 * @param cancelReason    取消原因
 * @author 课程大作业
 */
public record OrderSnapshot(
        String orderId,
        OrderStatus status,
        List<OrderItem> items,
        String couponId,
        BigDecimal discountAmount,
        BigDecimal deliveryFee,
        String paymentMethod,
        String paymentTradeNo,
        String riderId,
        Rider rider,
        String deliveryAddress,
        String remark,
        Instant submittedAt,
        Instant paidAt,
        Instant assignedAt,
        Instant completedAt,
        Instant cancelledAt,
        String cancelReason) {

    /**
     * 紧凑构造器：把明细列表复制为不可变列表，防止外部通过原列表修改快照内容；
     * 并保证骑手编号与骑手对象互相自洽（只给其中一个时自动补全另一个）。
     */
    public OrderSnapshot {
        items = items == null ? List.of() : List.copyOf(items);
        if (riderId == null && rider != null) {
            riderId = rider.riderId();
        }
        if (rider == null && riderId != null) {
            rider = Rider.placeholder(riderId);
        }
    }

    /** @return 快照中记录的明细数量（用于控制台展示） */
    public int itemLineCount() {
        return items.size();
    }
}
