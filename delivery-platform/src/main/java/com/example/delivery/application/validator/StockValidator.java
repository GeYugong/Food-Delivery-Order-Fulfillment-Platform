package com.example.delivery.application.validator;

import com.example.delivery.application.InventoryService;
import com.example.delivery.application.OrderValidationContext;

/**
 * 库存校验：订单中每道菜的数量不能超过当前可售库存。
 *
 * <p><b>职责链中的位置：第三环。</b>依赖 {@link InventoryService}（库存服务），
 * 因此把它放在纯计算的校验之后：只有前两环都通过，才值得去查库存。</p>
 *
 * <p><b>注意职责边界：</b>本环节只负责「能不能下单」的判断，
 * <b>不负责扣减库存</b>——扣减发生在下单成功之后（由应用服务编排），
 * 因为校验阶段失败回滚的代价必须为零。</p>
 *
 * @author 课程大作业
 */
public class StockValidator extends OrderValidator {

    /** 库存服务。 */
    private final InventoryService inventory;

    /**
     * @param inventory 库存服务
     */
    public StockValidator(InventoryService inventory) {
        this.inventory = inventory;
    }

    @Override
    public String name() {
        return "库存校验";
    }

    @Override
    protected String check(OrderValidationContext context) {
        for (var item : context.order().items()) {
            int remaining = inventory.remaining(item.dishId());
            if (remaining < item.quantity()) {
                return String.format("库存不足：%s 需要 %d 份，仅剩 %d 份",
                        item.dishName(), item.quantity(), remaining);
            }
        }
        return "通过：全部菜品库存充足";
    }
}
