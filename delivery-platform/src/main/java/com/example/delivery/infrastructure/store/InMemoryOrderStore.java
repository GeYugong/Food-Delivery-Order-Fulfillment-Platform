package com.example.delivery.infrastructure.store;

import com.example.delivery.domain.order.Order;
import com.example.delivery.domain.order.OrderSnapshot;
import com.example.delivery.domain.order.OrderStatus;
import com.example.delivery.domain.support.OrderStore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 内存版订单仓储（控制台演示用）。
 *
 * <p><b>设计模式关联：</b></p>
 * <ul>
 *   <li><b>单例模式</b>：{@link #getInstance()} 保证整个控制台程序只有一份订单数据，
 *       避免「不同界面看到不同数据集」；</li>
 *   <li><b>模板方法</b>：继承 {@link OrderStore#archiveBefore(long)} 的流程骨架，
 *       只实现 {@link #archive(Order)} 这一「基本方法」；</li>
 *   <li><b>原型模式</b>：归档时保存订单副本（{@link Order#copyAsDraft(String)} 之外，
 *       这里用只读快照 {@code snapshot()} 记录存档内容），保证后续操作不影响存档；</li>
 *   <li><b>备忘录模式</b>：{@code archivedSnapshots} 保存的是订单状态快照，
 *       可以进行「存档回放」与「存档校验」。</li>
 * </ul>
 *
 * <p><b>为什么用 LinkedHashMap：</b>需要按订单号 O(1) 查找，同时保持插入顺序便于稳定输出；
 * 展示时再按创建时间排序。</p>
 *
 * @author 课程大作业
 */
public class InMemoryOrderStore implements OrderStore {

    /** 单例实例（饿汉式：类加载即创建，语义简单且线程安全）。 */
    private static final InMemoryOrderStore INSTANCE = new InMemoryOrderStore();

    /** 订单号 → 订单。 */
    private final Map<String, Order> orders = new LinkedHashMap<>();

    /** 归档区：订单号 → 存档快照（列表，支持同一订单多次存档）。 */
    private final Map<String, List<OrderSnapshot>> archivedSnapshots = new LinkedHashMap<>();

    /** 归档日志，便于控制台展示「订单存档」环节做了什么。 */
    private final List<String> archiveLog = new ArrayList<>();

    /** 私有构造器，禁止外部 new。 */
    private InMemoryOrderStore() {
    }

    /**
     * 获取全局唯一实例。
     *
     * @return 单例
     */
    public static InMemoryOrderStore getInstance() {
        return INSTANCE;
    }

    @Override
    public void save(Order order) {
        orders.put(order.orderId(), order);
    }

    @Override
    public Optional<Order> findById(String orderId) {
        return Optional.ofNullable(orders.get(orderId));
    }

    @Override
    public List<Order> findByUser(String userId) {
        return orders.values().stream()
                .filter(order -> order.customer().userId().equals(userId))
                .sorted(OrderStore.byCreatedAtDesc())
                .toList();
    }

    @Override
    public List<Order> findByMerchant(String merchantId) {
        return orders.values().stream()
                .filter(order -> order.merchant().merchantId().equals(merchantId))
                .sorted(OrderStore.byCreatedAtDesc())
                .toList();
    }

    @Override
    public List<Order> findByStatus(OrderStatus status) {
        return orders.values().stream()
                .filter(order -> order.status() == status)
                .sorted(OrderStore.byCreatedAtDesc())
                .toList();
    }

    @Override
    public List<Order> findAll() {
        return orders.values().stream()
                .sorted(Comparator.comparing(Order::createdAt).reversed())
                .toList();
    }

    @Override
    public void archive(Order order) {
        // 存档用「快照」而不是订单引用：后续订单继续流转也不会污染存档内容
        archivedSnapshots.computeIfAbsent(order.orderId(), key -> new ArrayList<>()).add(order.snapshot());
        archiveLog.add(String.format("已存档：%s（状态=%s，实付=%s 元）",
                order.orderId(), order.status().label(), order.totalAmount().toPlainString()));
    }

    @Override
    public boolean delete(String orderId) {
        return orders.remove(orderId) != null;
    }

    /** @return 归档日志（只读） */
    public List<String> archiveLog() {
        return List.copyOf(archiveLog);
    }

    /**
     * 查询某订单的存档快照数量（用于演示「订单存档」确实落盘）。
     *
     * @param orderId 订单号
     * @return 快照数量
     */
    public int archivedSnapshotCount(String orderId) {
        return archivedSnapshots.getOrDefault(orderId, List.of()).size();
    }

    /** @return 已存档的订单号集合大小 */
    public int archivedOrderCount() {
        return archivedSnapshots.size();
    }

    /** 清空全部数据（测试与演示重置用）。 */
    public void clear() {
        orders.clear();
        archivedSnapshots.clear();
        archiveLog.clear();
    }
}
