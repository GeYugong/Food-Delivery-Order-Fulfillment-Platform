package com.example.delivery.domain.order;

import com.example.delivery.domain.merchant.Merchant;
import com.example.delivery.domain.user.User;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 订单仓储接口（端口 / Port）。
 *
 * <p><b>设计模式：模板方法模式（Template Method）的下沉点 + 依赖倒置 + 数据访问对象的抽象</b></p>
 * <p><b>为什么先定义接口：</b>领域模型不应该知道订单被存到哪里。
 * 控制台版先用内存实现，后续换成文件/数据库实现时，领域层与所有设计模式代码零改动；
 * 同时「统计报表」这类只读查询也收敛在这里，避免报表逻辑散落。</p>
 *
 * <p><b>与模板方法的关系：</b>本接口的默认方法 {@link #archiveBefore(Instant)} 给出了
 * 「过期订单自动归档」的固定骨架（筛选 → 判定 → 交子类处理），
 * 具体存档介质（内存/文件/数据库）由实现类决定，这就是模板方法的「钩子」思想。</p>
 *
 * @author 课程大作业
 */
public interface OrderRepository {

    /**
     * 保存订单（新增或覆盖）。
     *
     * @param order 待保存订单
     */
    void save(Order order);

    /**
     * 按订单号查询。
     *
     * @param orderId 订单号
     * @return 命中则返回订单，否则返回 {@code null}
     */
    Order findById(String orderId);

    /**
     * 查询某用户的全部订单。
     *
     * @param user 用户
     * @return 订单列表，按创建时间倒序
     */
    List<Order> findByUser(User user);

    /**
     * 查询某商家的全部订单。
     *
     * @param merchant 商家
     * @return 订单列表，按创建时间倒序
     */
    List<Order> findByMerchant(Merchant merchant);

    /**
     * 查询某状态的订单（派单中介、报表都用得上）。
     *
     * @param status 订单状态
     * @return 订单列表
     */
    List<Order> findByStatus(OrderStatus status);

    /**
     * 查询全部订单。
     *
     * @return 订单列表，按创建时间倒序
     */
    List<Order> findAll();

    /**
     * 统计订单总数。
     *
     * @return 订单总数
     */
    int count();

    /**
     * 把订单写入存档介质（模板方法中的「基本方法」，由实现类给出具体存储方式）。
     *
     * @param order 待存档订单
     */
    void archive(Order order);

    /**
     * 归档在指定时刻之前创建、且已处于终态的订单。
     *
     * <p><b>模板方法模式：</b>本默认方法固定了「归档」这一流程的骨架
     * （先按时间筛选 → 再按终态过滤 → 逐个交给 {@link #archive(Order)}），
     * 而把「存到哪里」留给子类实现；子类不需要也不应该重写流程本身。</p>
     *
     * @param before 时间界限（创建时间早于该时刻的订单才考虑归档）
     * @return 实际归档的订单数量
     */
    default int archiveBefore(Instant before) {
        int archived = 0;
        for (Order order : findAll()) {
            boolean isOld = order.createdAt().isBefore(before);
            boolean isSettled = order.status().isFinalState();
            if (isOld && isSettled) {
                archive(order);
                archived++;
            }
        }
        return archived;
    }

    /**
     * 清理比指定时刻更早的全部订单（演示内存仓储时会用到；真实系统慎用）。
     *
     * @param before 时间界限
     * @return 清理掉的订单数量
     */
    int deleteCreatedBefore(Instant before);

    /**
     * 计算平均履约时长（从下单到送达），用于统计报表。
     *
     * <p>默认实现只统计已完成的订单，避免未送达订单拉低平均值。</p>
     *
     * @return 平均时长；没有任何已完成订单时返回 {@link Duration#ZERO}
     */
    default Duration averageFulfillmentDuration() {
        List<Order> completed = findByStatus(OrderStatus.COMPLETED);
        if (completed.isEmpty()) {
            return Duration.ZERO;
        }
        long totalSeconds = completed.stream()
                .filter(order -> order.completedAt().isPresent())
                .mapToLong(order -> Duration.between(order.createdAt(), order.completedAt().orElseThrow()).getSeconds())
                .sum();
        return Duration.ofSeconds(totalSeconds / completed.size());
    }
}
