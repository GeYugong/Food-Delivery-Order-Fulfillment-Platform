package com.example.delivery.application.validator;

import com.example.delivery.application.OrderValidationContext;

/**
 * 收货地址校验：地址不能为空，且长度足够。
 *
 * <p><b>职责链中的位置：第四环。</b>真实系统会进一步做行政区划校验、
 * 坐标落点校验、是否在配送范围内校验；这些属于基础设施能力（地图服务），
 * 通过扩展本校验器接入，而不需要改动链条结构。</p>
 *
 * @author 课程大作业
 */
public class AddressValidator extends OrderValidator {

    /** 认为有效的最短地址长度。 */
    private static final int MIN_LENGTH = 6;

    @Override
    public String name() {
        return "收货地址校验";
    }

    @Override
    protected String check(OrderValidationContext context) {
        String address = context.order().deliveryAddress().orElse(context.user().address());
        if (address == null || address.isBlank()) {
            return "收货地址为空，无法配送";
        }
        if (address.trim().length() < MIN_LENGTH) {
            return "收货地址过短，请补充门牌号等信息：" + address;
        }
        return "通过：地址有效（" + address.trim() + "）";
    }
}
