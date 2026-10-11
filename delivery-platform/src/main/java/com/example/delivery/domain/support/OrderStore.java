package com.example.delivery.domain.support;

import com.example.delivery.domain.merchant.Merchant;
import com.example.delivery.domain.order.Order;
import com.example.delivery.domain.order.OrderStatus;
import com.example.delivery.domain.user.User;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 订单存储端口（Repository 模式 / 端口-适配器风格）。
 *
 * <p><b>为什么领域层定义接口、基础设施层实现：</b>业务编排（职责链、状态机、事件通知）
 * 不应该关心订单是存在内存、文件还是数据库里。控制台版用内存实现，
 * 换成文件/MySQL 实现时，应用层与服务层代码零改动——这就是依赖倒置带来的可替换性。</p>
 *
 * <p><b>与模板方法模式的关系：</b>默认方法 {@link #archiveBefore} 固定了「归档」的流程骨架
 * （筛选 → 终态判定 → 交给 {@link #archive(Order)} 落盘），把「怎么落盘」留给实现类。</p>
 *
 * @author 课程大作业
 */
public interface OrderStore {

    /**
     * 保存订单（新增或覆盖）。
     *
     * @param order 订单
     */
    void save(Order order);

    /**
     * 按订单号查询。
     *
     * @param orderId 订单号
     * @return 可能为空的订单
     */
    Optional<Order> findById(String orderId);

    /**
     * 按用户查询订单（创建时间倒序）。
     *
     * @param userId 用户编号
     * @return 订单列表
     */
    List<Order> findByUser(String userId);

    /**
     * 按商家查询订单。
     *
     * @param merchantId 商家编号
     * @return 订单列表
     */
    List<Order> findByMerchant(String merchantId);

    /**
     * 按状态查询订单。
     *
     * @param status 订单状态
     * @return 订单列表
     */
    List<Order> findByStatus(OrderStatus status);

    /**
     * 全部订单（创建时间倒序）。
     *
     * @return 订单列表
     */
    List<Order> findAll();

    /**
     * 归档某张订单（模板方法中的基本方法，由实现类决定「存到哪里」）。
     *
     * @param order 订单
     */
    void archive(Order order);

    /**
     * 归档在指定时刻之前创建、且已处于终态的订单。
     *
     * <p><b>设计模式：模板方法（Template Method）</b>——流程骨架固定，
     * 子类只提供 {@link #archive(Order)} 的实现即可，不需要重写流程本身。</p>
     *
     * @param beforeMillis 时间界限（毫秒时间戳）
     * @return 归档数量
     */
    default int archiveBefore(long beforeMillis) {
        int count = 0;
        for (Order order : findAll()) {
            boolean old = order.createdAt().toEpochMilli() < beforeMillis;
            if (old && order.status().isFinalState()) {
                archive(order);
                count++;
            }
        }
        return count;
    }

    /**
     * 删除某张订单（内存实现用于「订单迁出在线库」的演示）。
     *
     * @param orderId 订单号
     * @return 是否存在并删除成功
     */
    boolean delete(String orderId);

    /**
     * 订单总数。
     *
     * @return 数量
     */
    default int count() {
        return findAll().size();
    }

    /**
     * 按创建时间倒序排序的辅助比较器（供实现类复用）。
     *
     * @return 比较器
     */
    static Comparator<Order> byCreatedAtDesc() {
        return Comparator.comparing(Order::createdAt).reversed();
    }

    /**
     * 支付流水仓储（支付适配器产出的 {@code Payment} 记录最终要落在这里，用于对账）。
     */
    interface PaymentLedger {

        /**
         * 记录一笔支付流水。
         *
         * @param payment 支付记录
         */
        void record(com.example.delivery.domain.payment.Payment payment);

        /**
         * 查询某订单的全部支付流水（含失败记录，便于排查重复支付）。
         *
         * @param orderId 订单号
         * @return 支付记录列表
         */
        List<com.example.delivery.domain.payment.Payment> findByOrder(String orderId);

        /**
         * 全部支付流水。
         *
         * @return 支付记录列表
         */
        List<com.example.delivery.domain.payment.Payment> findAll();
    }

    /**
     * 用户仓储（用户是订单的参与方，放在同一端口族里便于控制台演示时统一装配）。
     */
    interface UserStore {

        /**
         * 保存用户。
         *
         * @param user 用户
         */
        void save(User user);

        /**
         * 按编号查询用户。
         *
         * @param userId 用户编号
         * @return 可能为空的用户
         */
        Optional<User> findById(String userId);

        /**
         * 全部用户。
         *
         * @return 用户列表
         */
        List<User> findAll();
    }

    /**
     * 商家仓储。
     */
    interface MerchantStore {

        /**
         * 保存商家。
         *
         * @param merchant 商家
         */
        void save(Merchant merchant);

        /**
         * 按编号查询商家。
         *
         * @param merchantId 商家编号
         * @return 可能为空的商家
         */
        Optional<Merchant> findById(String merchantId);

        /**
         * 全部商家。
         *
         * @return 商家列表
         */
        List<Merchant> findAll();
    }

    /**
     * 骑手仓储。
     */
    interface RiderStore {

        /**
         * 保存骑手。
         *
         * @param rider 骑手（record，更新=覆盖）
         */
        void save(com.example.delivery.domain.rider.Rider rider);

        /**
         * 按编号查询骑手。
         *
         * @param riderId 骑手编号
         * @return 可能为空的骑手
         */
        Optional<com.example.delivery.domain.rider.Rider> findById(String riderId);

        /**
         * 全部骑手。
         *
         * @return 骑手列表
         */
        List<com.example.delivery.domain.rider.Rider> findAll();

        /**
         * 当前可接单的骑手。
         *
         * @return 可接单骑手列表
         */
        default List<com.example.delivery.domain.rider.Rider> findAvailable() {
            return findAll().stream().filter(com.example.delivery.domain.rider.Rider::canAccept).toList();
        }
    }
}
