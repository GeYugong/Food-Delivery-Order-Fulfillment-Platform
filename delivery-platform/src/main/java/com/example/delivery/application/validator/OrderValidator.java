package com.example.delivery.application.validator;

import com.example.delivery.application.OrderValidationContext;

import java.util.ArrayList;
import java.util.List;
/**
 * 下单校验器（职责链模式 Chain of Responsibility 的「抽象处理者 Handler」角色）。
 *
 * <p><b>设计模式：职责链模式（Chain of Responsibility）</b></p>
 *
 * <p><b>为什么用：</b>下单前要过一串校验——非空、起送价、库存、地址、频控、风控。
 * 如果写成一个大方法里的连续 {@code if}，会带来三个问题：</p>
 * <ol>
 *   <li>想调整顺序或临时下线某条规则，必须改代码；</li>
 *   <li>每条规则无法单独测试与复用（例如「再来一单」也要跑同一批规则）；</li>
 *   <li>失败时无法优雅地说明「前面几环通过了、卡在第几环」。</li>
 * </ol>
 *
 * <p>用职责链后，每环都是独立类，通过 {@link #setNext(OrderValidator)} 串起来，
 * 既可以整链复用，也可以只跑其中几环（例如支付前只跑风控）。</p>
 *
 * <p><b>终止语义：</b>本实现采用「一票否决」策略——某环判定不通过即抛出
 * {@code OrderValidationException} 终止整条链（外卖下单必须全部满足，
 * 不像审批流那样需要多级会签）。链条的「是否继续传递」由 {@link #passToNext} 统一控制。</p>
 *
 * @author 课程大作业
 */
public abstract class OrderValidator {

    /** 下一个处理者（链式结构，null 表示链尾）。 */
    protected OrderValidator next;

    /** 本环节的执行轨迹，供控制台演示「链条是怎么走的」。 */
    protected final List<String> trace = new ArrayList<>();

    /**
     * 设置下一个校验器。
     *
     * <p><b>返回 {@code this}（链头）而不是 {@code next}：</b>这是一个很容易踩的坑——
     * 若返回 {@code next}，写成 {@code a.setNext(b).setNext(c)} 时第二次调用会作用在
     * <b>b</b> 上，看起来「链起来了」，实际上链条结构取决于返回值的语义，极易误用；
     * 返回链头则语义唯一：{@code a.setNext(b).setNext(c)} 明确表示 a→b→c。</p>
     *
     * @param next 下一个校验器
     * @return 链头（即本对象），便于链式声明 {@code a.setNext(b).setNext(c)}
     */
    public OrderValidator setNext(OrderValidator next) {
        this.next = next;
        return this;
    }

    /**
     * 校验入口（模板方法：先记录轨迹，再交给子类判断，最后决定是否向后传递）。
     *
     * @param context 校验上下文
     */
    public final void validate(OrderValidationContext context) {
        String result = check(context);
        trace.add("[" + name() + "] " + result);
        if (!result.startsWith("通过")) {
            throw new com.example.delivery.domain.order.OrderValidationException(name(), result);
        }
        passToNext(context);
    }

    /**
     * 具体校验逻辑（模板方法中的「基本方法」，由子类实现）。
     *
     * @param context 校验上下文
     * @return 以「通过」开头表示放行，否则为否决原因
     */
    protected abstract String check(OrderValidationContext context);

    /**
     * 校验器名称。
     *
     * @return 例如 {@code "起送价校验"}
     */
    public abstract String name();

    /**
     * 把请求传递给下一个校验器；链尾则视为整链通过。
     *
     * @param context 校验上下文
     */
    protected void passToNext(OrderValidationContext context) {
        if (next != null) {
            next.validate(context);
        }
    }

    /**
     * 收集整条链的执行轨迹（递归向下）。
     *
     * <p><b>为什么轨迹要单独收集：</b>校验链「一票否决」会抛异常中断流程，
     * 如果轨迹只写日志，控制台就无法展示「前面几环通过了、卡在第几环」。
     * 通过把轨迹挂在校验器上并递归汇总，即使链条被打断也能打印出完整过程——
     * 这对职责链模式的课堂演示非常关键。</p>
     *
     * @return 轨迹列表（按链条顺序）
     */
    public List<String> collectTrace() {
        List<String> all = new ArrayList<>(trace);
        if (next != null) {
            all.addAll(next.collectTrace());
        }
        return all;
    }

    /**
     * 清空整条链的轨迹（同一链条被复用多次时，避免历史轨迹混入）。
     */
    public void resetTrace() {
        trace.clear();
        if (next != null) {
            next.resetTrace();
        }
    }
}
