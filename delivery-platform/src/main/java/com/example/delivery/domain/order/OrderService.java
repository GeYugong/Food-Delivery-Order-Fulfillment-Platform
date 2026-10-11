package com.example.delivery.domain.order;

import com.example.delivery.domain.rider.Rider;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单应用服务接口（订单履约为核心用例的契约）。
 *
 * <p><b>设计模式关联总览：</b>本接口的每个方法都对应履约闭环中的一环，
 * 方法体内由具体实现类（{@code OrderServiceImpl}，后续阶段给出）编排各种设计模式：</p>
 *
 * <table border="1">
 *   <caption>方法 → 设计模式对应表</caption>
 *   <tr><th>方法</th><th>履约环节</th><th>使用/体现的设计模式</th></tr>
 *   <tr><td>{@link #createOrder}</td><td>订单构建</td><td>建造者、抽象工厂（创建用户/商家/菜品族）</td></tr>
 *   <tr><td>{@link #submitOrder}</td><td>校验链 + 计价 + 优惠</td><td>职责链、策略、解释器</td></tr>
 *   <tr><td>{@link #payOrder}</td><td>支付适配</td><td>适配器、外观</td></tr>
 *   <tr><td>{@link #advanceStatus}</td><td>状态流转</td><td>状态、观察者</td></tr>
 *   <tr><td>{@link #assignRider}</td><td>派单中介 + 骑手调度</td><td>中介者、命令、观察者</td></tr>
 *   <tr><td>{@link #cancelOrder}</td><td>取消与回滚</td><td>状态、备忘录、命令（撤销）</td></tr>
 *   <tr><td>{@link #archiveOrders}</td><td>订单存档</td><td>模板方法、原型（副本存档）</td></tr>
 * </table>
 *
 * <p><b>为什么用接口而不是直接写实现：</b>课程演示需要「同一契约、多种实现」的对比
 * （内存实现 vs 文件实现；真实支付 vs 模拟支付），接口让替换成本为零，
 * 也是依赖倒置原则的直接体现。</p>
 *
 * @author 课程大作业
 */
public interface OrderService {

    /**
     * 创建草稿订单。
     *
     * @param userId     下单用户编号
     * @param merchantId 商家编号
     * @return 处于草稿态的订单
     */
    Order createOrder(String userId, String merchantId);

    /**
     * 向订单追加菜品。
     *
     * @param orderId  订单号
     * @param dishId   菜品编号
     * @param quantity 数量
     * @return 追加后的订单
     */
    Order addDish(String orderId, String dishId, int quantity);

    /**
     * 提交订单：执行「校验链 → 计价策略 → 优惠券解释 → 计算实付」完整流程。
     *
     * @param orderId   订单号
     * @param couponId  优惠券编号，可为 {@code null}
     * @return 提交后的订单（待支付状态，金额已算好）
     */
    Order submitOrder(String orderId, String couponId);

    /**
     * 支付订单（通过支付适配器调用具体支付渠道）。
     *
     * @param orderId       订单号
     * @param paymentMethod 支付方式标识，例如 {@code "WALLET"}、{@code "WECHAT_PAY"}
     * @return 支付后的订单（已支付状态）
     */
    Order payOrder(String orderId, String paymentMethod);

    /**
     * 派单：把已支付订单交给派单中介去匹配骑手。
     *
     * @param orderId 订单号
     * @return 派单结果说明（成功时已绑定骑手，失败时说明原因）
     */
    DispatchResult dispatch(String orderId);

    /**
     * 显式指定骑手接单（演示用；真实系统由中介者自动匹配）。
     *
     * @param orderId 订单号
     * @param riderId 骑手编号
     * @return 已接单的订单
     */
    Order assignRider(String orderId, String riderId);

    /**
     * 推进订单到下一个业务状态（取餐/送达等）。
     *
     * @param orderId 订单号
     * @return 推进后的订单
     */
    Order advanceStatus(String orderId);

    /**
     * 取消订单。
     *
     * @param orderId 订单号
     * @param reason  取消原因
     * @return 取消后的订单
     */
    Order cancelOrder(String orderId, String reason);

    /**
     * 查询订单。
     *
     * @param orderId 订单号
     * @return 订单
     * @throws IllegalArgumentException 订单不存在时抛出
     */
    Order getOrder(String orderId);

    /**
     * 查询某用户的订单。
     *
     * @param userId 用户编号
     * @return 订单列表
     */
    List<Order> listUserOrders(String userId);

    /**
     * 归档历史订单（模板方法：筛选 + 终态判定 + 存档）。
     *
     * @return 归档数量
     */
    int archiveOrders();

    /**
     * 优惠试算：只计算不下单，用于「提交前预览实付金额」。
     *
     * @param orderId  订单号
     * @param couponId 优惠券编号，可为 {@code null}
     * @return 优惠后实付金额
     */
    BigDecimal previewTotal(String orderId, String couponId);

    /**
     * 派单结果值对象。
     *
     * <p><b>设计模式关联：命令模式（Command）的返回值契约</b>——
     * 派单是一次可撤销/可重试的命令执行，结果需要明确「成功/失败 + 原因 + 承接骑手」。</p>
     *
     * @param success 是否派单成功
     * @param orderId 订单号
     * @param riderId 承接骑手编号（失败为 {@code null}）
     * @param message 人类可读的结果说明
     * @author 课程大作业
     */
    record DispatchResult(boolean success, String orderId, String riderId, String message) {

        /** @return 是否派单失败 */
        public boolean failed() {
            return !success;
        }
    }

    /**
     * 骑手调度端口（供中介者模式调用，避免订单服务直接依赖调度算法）。
     *
     * <p><b>设计模式：策略模式（Strategy）+ 中介者模式（Mediator）协作</b>——
     * 中介者只负责「找谁」，调度端口负责「怎么排序挑选」，
     * 排序算法可换成「最近优先 / 最少单量优先 / 顺路度优先」而互不影响。</p>
     */
    interface RiderDispatcher {

        /**
         * 为指定订单挑选一名可用骑手。
         *
         * @param order 待派订单
         * @return 选中的骑手；无可用骑手时返回 {@code null}
         */
        Rider dispatch(Order order);
    }
}
