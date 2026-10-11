package com.example.delivery.infrastructure.payment;

import com.example.delivery.domain.payment.Payment;
import com.example.delivery.domain.payment.PaymentMethod;
import com.example.delivery.domain.support.OrderStore;
import com.example.delivery.domain.support.PaymentGateway;
import com.example.delivery.domain.user.User;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 余额支付适配器。
 *
 * <p><b>设计模式：适配器模式（Adapter）</b>——本渠道的「被适配者」是平台自身的用户账户，
 * 它并不需要翻译第三方协议，但**同样要适配成统一的 {@link PaymentGateway} 契约**：
 * 把「扣余额」的失败语义（余额不足）翻译成成功的 {@link Payment} 或失败的 {@link Payment}，
 * 而不是抛异常。这样订单服务对待四个渠道的方式完全一致。</p>
 *
 * <p><b>为什么余额支付也要写成适配器：</b>如果对余额支付特殊处理（直接调用、
 * 其他渠道走适配器），订单服务里就会出现「本地渠道一个分支、第三方渠道另一个分支」，
 * 适配器模式的价值立刻被抵消。统一契约比「少写一个类」更重要。</p>
 *
 * @author 课程大作业
 */
public class WalletPaymentAdapter implements PaymentGateway {

    /** 用户仓储：扣款后需要回写新的用户对象（余额是 record 字段）。 */
    private final OrderStore.UserStore userStore;

    /**
     * @param userStore 用户仓储
     */
    public WalletPaymentAdapter(OrderStore.UserStore userStore) {
        this.userStore = userStore;
    }

    @Override
    public String channel() {
        return PaymentMethod.WALLET.name();
    }

    @Override
    public String channelLabel() {
        return PaymentMethod.WALLET.label();
    }

    @Override
    public Payment pay(String orderId, BigDecimal amount, String payerRef) {
        Optional<User> found = userStore.findById(payerRef);
        if (found.isEmpty()) {
            return Payment.failure("WALLET-" + orderId, orderId, amount,
                    PaymentMethod.WALLET, "用户不存在：" + payerRef);
        }
        User user = found.get();
        if (user.balance().compareTo(amount) < 0) {
            System.out.printf("    [余额支付] 扣款失败：余额 %s 元 < 应付 %s 元%n",
                    user.balance().toPlainString(), amount.toPlainString());
            return Payment.failure("WALLET-" + orderId, orderId, amount,
                    PaymentMethod.WALLET, "余额不足");
        }
        // record 不可变：扣款产生新对象并回写仓储
        userStore.save(user.deductBalance(amount));
        System.out.printf("    [余额支付] 扣款成功：%s 元，余额 %s → %s 元%n",
                amount.toPlainString(), user.balance().toPlainString(),
                userStore.findById(payerRef).orElseThrow().balance().toPlainString());
        return Payment.success("WALLET-" + Math.abs(orderId.hashCode()), orderId, amount, PaymentMethod.WALLET);
    }
}
