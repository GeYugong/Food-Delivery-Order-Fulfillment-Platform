package com.example.delivery.infrastructure.payment;

import com.example.delivery.domain.payment.PaymentMethod;
import com.example.delivery.domain.support.PaymentGateway;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 支付网关注册表（支付适配器的「策略选择器」）。
 *
 * <p><b>设计模式关联：</b></p>
 * <ul>
 *   <li><b>单例模式</b>：{@link #getInstance()} 全平台唯一注册表；</li>
 *   <li><b>工厂方法 / 注册表</b>：按 {@link PaymentMethod} 取出对应的 {@link PaymentGateway}
 *       适配器；业务代码只依赖接口与枚举，不出现任何 {@code if ("WECHAT".equals(...))}；</li>
 *   <li><b>策略模式</b>：每个适配器就是一种「支付策略」，注册表是选择器。</li>
 * </ul>
 *
 * <p><b>扩展方式：</b>新增渠道只需实现 {@link PaymentGateway} 并调用
 * {@link #register(PaymentGateway)}，订单服务、控制台、报表都无需改动。</p>
 *
 * @author 课程大作业
 */
public class PaymentGatewayRegistry {

    /** 单例。 */
    private static final PaymentGatewayRegistry INSTANCE = new PaymentGatewayRegistry();

    /** 渠道标识 → 适配器（用渠道标识字符串做 key，新增渠道无需改动本类）。 */
    private final Map<String, PaymentGateway> gateways = new java.util.LinkedHashMap<>();

    /** 私有构造器。 */
    private PaymentGatewayRegistry() {
    }

    /**
     * @return 单例
     */
    public static PaymentGatewayRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * 注册（或覆盖）一个支付适配器。
     *
     * @param gateway 适配器
     * @return this，便于链式注册
     */
    public PaymentGatewayRegistry register(PaymentGateway gateway) {
        gateways.put(gateway.channel(), gateway);
        return this;
    }

    /**
     * 按支付方式取出适配器。
     *
     * @param method 支付方式
     * @return 可能为空的适配器
     */
    public Optional<PaymentGateway> find(PaymentMethod method) {
        return Optional.ofNullable(gateways.get(method.name()));
    }

    /**
     * 按支付方式取出适配器，找不到时抛出异常。
     *
     * @param method 支付方式
     * @return 适配器
     * @throws IllegalArgumentException 未注册时抛出
     */
    public PaymentGateway require(PaymentMethod method) {
        return find(method).orElseThrow(() -> new IllegalArgumentException(
                "未注册的支付渠道：" + method.label() + "（请先调用 register 注册适配器）"));
    }

    /**
     * 按标识字符串解析并取出适配器（控制台输入用）。
     *
     * @param raw 用户输入，例如 {@code "wallet"}、{@code "微信支付"}
     * @return 适配器
     */
    public PaymentGateway requireByRaw(String raw) {
        return require(PaymentMethod.parse(raw));
    }

    /** @return 已注册的渠道标识列表 */
    public List<String> registeredChannels() {
        return gateways.values().stream().map(PaymentGateway::channelLabel).toList();
    }
}
