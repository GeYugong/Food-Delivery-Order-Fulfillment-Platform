package com.example.delivery.infrastructure.store;

import com.example.delivery.domain.merchant.Merchant;
import com.example.delivery.domain.rider.Rider;
import com.example.delivery.domain.support.OrderStore;
import com.example.delivery.domain.user.User;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 内存版「用户 / 商家 / 骑手」仓储（三合一，控制台演示用）。
 *
 * <p><b>设计模式关联：</b></p>
 * <ul>
 *   <li><b>单例模式</b>：{@link #getInstance()} 提供唯一数据源；</li>
 *   <li><b>三个内部类分别实现 {@link OrderStore.UserStore}、
 *       {@link OrderStore.MerchantStore}、{@link OrderStore.RiderStore}</b>：
 *       接口隔离原则（ISP）——订单服务只需要用户仓储时，不会被迫依赖骑手仓储；</li>
 *   <li><b>骑手状态用 record 覆盖式更新</b>：骑手接单/送达返回新对象，
 *       仓储负责替换，天然避免「同一个骑手对象被多处并发修改」的问题。</li>
 * </ul>
 *
 * @author 课程大作业
 */
public final class InMemoryActorStore {

    /** 单例。 */
    private static final InMemoryActorStore INSTANCE = new InMemoryActorStore();

    /** 用户表。 */
    private final Map<String, User> users = new LinkedHashMap<>();

    /** 商家表。 */
    private final Map<String, Merchant> merchants = new LinkedHashMap<>();

    /** 骑手表。 */
    private final Map<String, Rider> riders = new LinkedHashMap<>();

    /** 用户仓储视图。 */
    private final OrderStore.UserStore userStore = new OrderStore.UserStore() {
        @Override
        public void save(User user) {
            users.put(user.userId(), user);
        }

        @Override
        public Optional<User> findById(String userId) {
            return Optional.ofNullable(users.get(userId));
        }

        @Override
        public List<User> findAll() {
            return List.copyOf(users.values());
        }
    };

    /** 商家仓储视图。 */
    private final OrderStore.MerchantStore merchantStore = new OrderStore.MerchantStore() {
        @Override
        public void save(Merchant merchant) {
            merchants.put(merchant.merchantId(), merchant);
        }

        @Override
        public Optional<Merchant> findById(String merchantId) {
            return Optional.ofNullable(merchants.get(merchantId));
        }

        @Override
        public List<Merchant> findAll() {
            return List.copyOf(merchants.values());
        }
    };

    /** 骑手仓储视图。 */
    private final OrderStore.RiderStore riderStore = new OrderStore.RiderStore() {
        @Override
        public void save(Rider rider) {
            riders.put(rider.riderId(), rider);
        }

        @Override
        public Optional<Rider> findById(String riderId) {
            return Optional.ofNullable(riders.get(riderId));
        }

        @Override
        public List<Rider> findAll() {
            return List.copyOf(riders.values());
        }
    };

    /** 私有构造器。 */
    private InMemoryActorStore() {
    }

    /**
     * @return 单例
     */
    public static InMemoryActorStore getInstance() {
        return INSTANCE;
    }

    /** @return 用户仓储 */
    public OrderStore.UserStore users() {
        return userStore;
    }

    /** @return 商家仓储 */
    public OrderStore.MerchantStore merchants() {
        return merchantStore;
    }

    /** @return 骑手仓储 */
    public OrderStore.RiderStore riders() {
        return riderStore;
    }

    /** 清空全部数据（测试用）。 */
    public void clear() {
        users.clear();
        merchants.clear();
        riders.clear();
    }
}
