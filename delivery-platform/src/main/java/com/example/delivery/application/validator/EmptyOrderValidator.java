package com.example.delivery.application.validator;

import com.example.delivery.application.OrderValidationContext;

/**
 * 订单非空校验：订单必须至少有一道菜，且每道菜数量合法。
 *
 * <p><b>职责链中的位置：第一环。</b>这是最便宜、最容易失败的校验，
 * 放在链首可以避免为明显非法的请求去做库存查询、风控等昂贵操作
 * （这正是职责链「先便宜、后昂贵」的排序原则）。</p>
 *
 * @author 课程大作业
 */
public class EmptyOrderValidator extends OrderValidator {

    @Override
    public String name() {
        return "订单非空校验";
    }

    @Override
    protected String check(OrderValidationContext context) {
        var order = context.order();
        if (order.items().isEmpty()) {
            return "订单没有任何菜品，请先选择菜品";
        }
        boolean anyInvalid = order.items().stream().anyMatch(item -> item.quantity() <= 0);
        if (anyInvalid) {
            return "存在数量非法的菜品明细";
        }
        return "通过：共 " + order.items().size() + " 行明细、" + order.totalQuantity() + " 份菜品";
    }
}
