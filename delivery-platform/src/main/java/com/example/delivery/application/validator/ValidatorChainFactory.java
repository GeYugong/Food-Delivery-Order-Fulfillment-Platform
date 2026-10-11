package com.example.delivery.application.validator;

import com.example.delivery.application.InventoryService;
import com.example.delivery.application.OrderValidationContext;

/**
 * 下单校验链的「装配工厂」。
 *
 * <p><b>设计模式：职责链模式（Chain of Responsibility）+ 简单工厂</b></p>
 *
 * <p><b>为什么把组装逻辑单独拿出来：</b>链条的**顺序**是业务决策
 * （先廉价后昂贵、先强约束后弱约束），把它集中在一个地方，
 * 产品经理调整顺序时只改这一处；测试也可以直接调用
 * {@link #chainOf(OrderValidator...)} 拼一条只含目标环节的短链。</p>
 *
 * <p>默认完整链条与排序理由：</p>
 * <ol>
 *   <li>{@link EmptyOrderValidator} —— 零成本，且能挡掉最离谱的请求；</li>
 *   <li>{@link MinimumAmountValidator} —— 一次金额比较，零 IO；</li>
 *   <li>{@link StockValidator} —— 需要查库存（可能有远程调用）；</li>
 *   <li>{@link AddressValidator} —— 需要读地址与（未来的）地图服务；</li>
 *   <li>{@link FrequencyLimitValidator} —— 需要查频控计数（Redis）；</li>
 *   <li>{@link RiskControlValidator} —— 最重，聚合前面所有结论后才执行。</li>
 * </ol>
 *
 * @author 课程大作业
 */
public final class ValidatorChainFactory {

    /** 工具类不允许实例化。 */
    private ValidatorChainFactory() {
    }

    /**
     * 构建默认的完整下单校验链。
     *
     * @param inventory 库存服务
     * @return 链首处理者
     */
    public static OrderValidator defaultChain(InventoryService inventory) {
        OrderValidator head = new EmptyOrderValidator();
        head.setNext(new MinimumAmountValidator());
        head.next.setNext(new StockValidator(inventory));
        head.next.next.setNext(new AddressValidator());
        head.next.next.next.setNext(new FrequencyLimitValidator());
        head.next.next.next.next.setNext(new RiskControlValidator());
        return head;
    }

    /**
     * 按传入顺序拼一条自定义链。
     *
     * @param validators 依序排列的校验器
     * @return 链首；无参数时返回一个永远通过的哨兵校验器
     */
    public static OrderValidator chainOf(OrderValidator... validators) {
        if (validators.length == 0) {
            return new OrderValidator() {
                @Override
                protected String check(OrderValidationContext context) {
                    return "通过：空链";
                }

                @Override
                public String name() {
                    return "空校验链";
                }
            };
        }
        for (int i = 0; i < validators.length - 1; i++) {
            validators[i].setNext(validators[i + 1]);
        }
        return validators[0];
    }
}
