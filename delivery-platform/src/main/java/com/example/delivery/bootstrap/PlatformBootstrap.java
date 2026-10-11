package com.example.delivery.bootstrap;

import com.example.delivery.application.DispatchCenter;
import com.example.delivery.application.InventoryService;
import com.example.delivery.application.OrderFacade;
import com.example.delivery.application.OrderMemoManager;
import com.example.delivery.application.report.ReportService;
import com.example.delivery.domain.coupon.Coupon;
import com.example.delivery.domain.merchant.Dish;
import com.example.delivery.domain.merchant.Merchant;
import com.example.delivery.domain.pricing.PricingContext;
import com.example.delivery.domain.rider.Rider;
import com.example.delivery.domain.support.DomainEventPublisher;
import com.example.delivery.domain.user.User;
import com.example.delivery.infrastructure.coupon.CouponRuleInterpreter;
import com.example.delivery.infrastructure.event.OrderEventListeners;
import com.example.delivery.infrastructure.payment.AlipayPaymentAdapter;
import com.example.delivery.infrastructure.payment.BankCardPaymentAdapter;
import com.example.delivery.infrastructure.payment.PaymentGatewayRegistry;
import com.example.delivery.infrastructure.payment.ThirdPartyPayApis;
import com.example.delivery.infrastructure.payment.WalletPaymentAdapter;
import com.example.delivery.infrastructure.payment.WechatPaymentAdapter;
import com.example.delivery.infrastructure.store.InMemoryActorStore;
import com.example.delivery.infrastructure.store.InMemoryOrderStore;

import java.math.BigDecimal;
import java.util.List;

/**
 * 平台装配器（组合根 / Composition Root）。
 *
 * <p><b>设计模式：抽象工厂 + 单例 + 建造者 + 依赖注入</b></p>
 *
 * <p><b>为什么需要一个统一的装配入口：</b>整个平台有十几个协作者
 * （仓储、库存、解释器、计价上下文、事件总线、派单中介、四个支付适配器、五个事件监听器…）。
 * 如果每个入口（交互控制台、阶段演示、单元测试）都自己 new 一遍，
 * 会出现三套互不相同的装配逻辑，极易漏注册监听器或适配器。</p>
 *
 * <p>因此这里把「演示数据 + 组件装配」收敛成一个可重复调用的工厂：
 * 调用 {@link #assemble()} 就得到一台<b>数据齐全、渠道齐全、监听器齐全</b>的平台。</p>
 *
 * <p><b>抽象工厂思想的体现：</b>{@link #platformDataFactory()} 一次性产出
 * 相互匹配的一整族业务对象（用户/商家/菜品/骑手/优惠券），
 * 换一个工厂实现就能切换成「演示数据族」或「压测数据族」，
 * 而装配代码完全不变。</p>
 *
 * @author 课程大作业
 */
public final class PlatformBootstrap {

    /** 工具类不允许实例化。 */
    private PlatformBootstrap() {
    }

    /**
     * 装配结果：一次组装出的全部组件（不可变容器，便于测试按需取用）。
     *
     * @param facade      业务门面
     * @param orderStore  订单仓储
     * @param actorStore  用户/商家/骑手仓储
     * @param inventory   库存服务
     * @param publisher   事件发布器
     * @param reportService 报表服务
     * @param interpreter 优惠券解释器
     * @param smsListener 用户短信监听器（断言用）
     * @param reportListener 报表事件监听器
     * @param auditListener 审计日志监听器
     * @param merchantListener 商家端监听器
     * @param riderListener 骑手端监听器
     * @author 课程大作业
     */
    public record Platform(
            OrderFacade facade,
            InMemoryOrderStore orderStore,
            InMemoryActorStore actorStore,
            InventoryService inventory,
            DomainEventPublisher publisher,
            ReportService reportService,
            CouponRuleInterpreter interpreter,
            OrderEventListeners.UserSmsListener smsListener,
            OrderEventListeners.ReportListener reportListener,
            OrderEventListeners.AuditLogListener auditListener,
            OrderEventListeners.MerchantPortalListener merchantListener,
            OrderEventListeners.RiderAppListener riderListener) {
    }

    /**
     * 装配一台完整的平台（含演示数据、四个支付渠道、五个事件监听器）。
     *
     * @return 装配结果
     */
    public static Platform assemble() {
        // ---------- 1. 仓储（单例：保证整个进程一份数据） ----------
        InMemoryOrderStore orderStore = InMemoryOrderStore.getInstance();
        InMemoryActorStore actorStore = InMemoryActorStore.getInstance();
        orderStore.clear();
        actorStore.clear();

        // ---------- 2. 演示数据（抽象工厂产出的一整族自洽数据） ----------
        DataSet data = platformDataFactory();
        data.users.forEach(actorStore.users()::save);
        data.merchants.forEach(actorStore.merchants()::save);
        data.riders.forEach(actorStore.riders()::save);

        // ---------- 3. 库存服务：依据菜单派生库存 ----------
        InventoryService inventory = InventoryService.getInstance();
        inventory.clear();
        data.merchants.forEach(inventory::register);

        // ---------- 4. 事件总线 + 五个监听器（观察者模式） ----------
        DomainEventPublisher publisher = new DomainEventPublisher();
        var smsListener = new OrderEventListeners.UserSmsListener();
        var merchantListener = new OrderEventListeners.MerchantPortalListener();
        var riderListener = new OrderEventListeners.RiderAppListener();
        var reportListener = new OrderEventListeners.ReportListener();
        var auditListener = new OrderEventListeners.AuditLogListener();
        publisher.subscribe(smsListener);
        publisher.subscribe(merchantListener);
        publisher.subscribe(riderListener);
        publisher.subscribe(reportListener);
        publisher.subscribe(auditListener);

        // ---------- 5. 优惠券解释器（解释器模式） ----------
        CouponRuleInterpreter interpreter = new CouponRuleInterpreter();
        data.coupons.forEach(interpreter::register);

        // ---------- 6. 支付适配器（适配器模式），注册进注册表 ----------
        PaymentGatewayRegistry registry = PaymentGatewayRegistry.getInstance();
        registry.register(new WalletPaymentAdapter(actorStore.users()));
        registry.register(new WechatPaymentAdapter(new ThirdPartyPayApis.WechatSdk()));
        registry.register(new AlipayPaymentAdapter(new ThirdPartyPayApis.AliPayClient()));
        registry.register(new BankCardPaymentAdapter(new ThirdPartyPayApis.BankGateway()));

        // ---------- 7. 计价上下文（策略模式）：起步价 5 元 + 1 元/公里 ----------
        PricingContext pricing = new PricingContext().withRate("5.00", "1.00");

        // ---------- 8. 派单中心（中介者模式，内部走策略模式） ----------
        DispatchCenter dispatchCenter = new DispatchCenter(orderStore, actorStore.riders(), publisher);

        // ---------- 9. 门面（外观模式 + 建造者装配） ----------
        OrderFacade.Builder builder = new OrderFacade.Builder()
                .orderStore(orderStore)
                .paymentLedger(paymentLedgerOf(orderStore))
                .userStore(actorStore.users())
                .merchantStore(actorStore.merchants())
                .riderStore(actorStore.riders())
                .inventory(inventory)
                .couponEvaluator(interpreter)
                .couponInterpreter(interpreter)
                .pricing(pricing)
                .publisher(publisher)
                .dispatchCenter(dispatchCenter)
                .memoManager(new OrderMemoManager());
        data.coupons.forEach(builder::withCoupon);
        OrderFacade facade = builder.build();

        // ---------- 10. 报表服务（访问者模式） ----------
        ReportService reportService = new ReportService(facade::listOrders, facade::allPayments);

        return new Platform(facade, orderStore, actorStore, inventory, publisher, reportService,
                interpreter, smsListener, reportListener, auditListener, merchantListener, riderListener);
    }

    /**
     * 把订单仓储适配成支付流水仓储。
     *
     * <p><b>适配器思想的复用：</b>订单仓储本身不承担支付流水职责，
     * 这里用一个匿名适配器把「内存列表」适配成 {@code PaymentLedger} 契约，
     * 从而不需要为演示再引入一个独立的存储实现。</p>
     *
     * @param orderStore 订单仓储（仅用于示意生命周期绑定，实际不读取其数据）
     * @return 支付流水仓储
     */
    private static com.example.delivery.domain.support.OrderStore.PaymentLedger paymentLedgerOf(
            InMemoryOrderStore orderStore) {
        return new com.example.delivery.domain.support.OrderStore.PaymentLedger() {
            private final List<com.example.delivery.domain.payment.Payment> payments =
                    new java.util.ArrayList<>();

            @Override
            public void record(com.example.delivery.domain.payment.Payment payment) {
                payments.add(payment);
            }

            @Override
            public List<com.example.delivery.domain.payment.Payment> findByOrder(String orderId) {
                return payments.stream().filter(p -> p.orderId().equals(orderId)).toList();
            }

            @Override
            public List<com.example.delivery.domain.payment.Payment> findAll() {
                return List.copyOf(payments);
            }
        };
    }

    /**
     * 演示数据族（抽象工厂的「具体工厂」：演示族）。
     *
     * <p>产出的用户、商家、菜品、骑手、优惠券在价格体系与会员等级上是<b>相互匹配</b>的，
     * 因此可以放心混用——这正是抽象工厂模式要保证的「族内一致性」。</p>
     *
     * @return 一整族演示数据
     */
    private static DataSet platformDataFactory() {
        DataSet data = new DataSet();

        data.users.add(new User("U-1001", "张三", "13812348888",
                "杭州市西湖区文一西路 969 号", new BigDecimal("200.00"), User.MemberLevel.GOLD));
        data.users.add(new User("U-1002", "李四", "13700002222",
                "杭州市余杭区文一西路 1500 号", new BigDecimal("50.00"), User.MemberLevel.NORMAL));
        data.users.add(new User("U-1003", "王五", "13600003333",
                "杭州市滨江区江南大道 588 号", new BigDecimal("800.00"), User.MemberLevel.DIAMOND));

        data.merchants.add(new Merchant("M-2001", "川味小厨", "杭州市西湖区文三路 100 号",
                List.of(
                        Dish.available("DISH-001", "香辣鸡腿堡", "19.80", 50, "主食"),
                        Dish.available("DISH-002", "黄金薯条", "9.90", 100, "小食"),
                        Dish.available("DISH-003", "冰镇可乐", "6.50", 200, "饮品"),
                        Dish.available("DISH-004", "藤椒鸡排饭", "25.00", 30, "主食")),
                new BigDecimal("20.00"), new BigDecimal("5.00")));

        data.merchants.add(new Merchant("M-2002", "轻食沙拉铺", "杭州市西湖区教工路 8 号",
                List.of(
                        Dish.available("DISH-101", "鸡胸肉沙拉", "28.00", 40, "轻食"),
                        Dish.available("DISH-102", "牛油果奶昔", "18.00", 25, "饮品"),
                        Dish.available("DISH-103", "全麦三明治", "16.50", 60, "轻食")),
                new BigDecimal("30.00"), new BigDecimal("6.00")));

        data.riders.add(Rider.idle("R-3001", "李骑手", "13900001111", "1.20", "4.90"));
        data.riders.add(Rider.idle("R-3002", "赵骑手", "13900002222", "0.80", "4.70"));
        data.riders.add(new Rider("R-3003", "孙骑手", "13900003333",
                Rider.RiderStatus.DELIVERING, 2, 3, new BigDecimal("2.50"), new BigDecimal("4.95")));

        data.coupons.add(Coupon.fullReduction("C-9001", "30", "5"));
        data.coupons.add(new Coupon("C-9002", "满50减10", "满50减10",
                new BigDecimal("10.00"), new BigDecimal("50.00"),
                java.time.LocalDate.now().minusDays(1), java.time.LocalDate.now().plusDays(30)));
        data.coupons.add(new Coupon("C-9003", "9折券", "9折",
                new BigDecimal("50.00"), BigDecimal.ZERO,
                java.time.LocalDate.now().minusDays(1), java.time.LocalDate.now().plusDays(30)));
        data.coupons.add(new Coupon("C-9004", "钻石会员满30减5且等级>=2",
                "满30减5 且 会员等级>=2", new BigDecimal("5.00"), new BigDecimal("30.00"),
                java.time.LocalDate.now().minusDays(1), java.time.LocalDate.now().plusDays(30)));
        data.coupons.add(new Coupon("C-9005", "满50减10或钻石会员",
                "满50减10 或 会员等级==2", new BigDecimal("10.00"), BigDecimal.ZERO,
                java.time.LocalDate.now().minusDays(1), java.time.LocalDate.now().plusDays(30)));

        return data;
    }

    /**
     * 演示数据集容器（抽象工厂的「产品族」容器）。
     */
    private static final class DataSet {

        /** 用户族。 */
        private final List<User> users = new java.util.ArrayList<>();

        /** 商家族。 */
        private final List<Merchant> merchants = new java.util.ArrayList<>();

        /** 骑手族。 */
        private final List<Rider> riders = new java.util.ArrayList<>();

        /** 优惠券族。 */
        private final List<Coupon> coupons = new java.util.ArrayList<>();
    }
}
