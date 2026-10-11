package com.example.patterns.creational.singleton;

import com.example.patterns.PatternDemo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 单例模式（Singleton）演示：外卖平台的「全局唯一」三件套。
 *
 * <p><b>用了什么模式：</b>单例模式——保证一个类在同一个 JVM 中只有一个实例，并给出全局访问点。
 * 本 Demo 用三种产量级写法覆盖同一模式：① {@link PlatformConfig} 双重检查锁（DCL）懒汉式；
 * ② {@link IdGenerator} 静态内部类（Holder）；③ {@link NotificationChannelRegistry} 枚举。</p>
 *
 * <p><b>为什么用：</b>平台名、默认配送费率、高峰时段这类配置若每个模块各 new 一份，
 * 运营改一次费率就要重启所有模块；订单号生成器更危险——两个实例会让「全局唯一订单号」失效，
 * 出现两笔订单同号，对账与退款全部错乱。</p>
 *
 * <p><b>不用会怎样：</b>配置多份副本 → 配置漂移（A 模块读旧费率、B 模块读新费率，同一单结算金额不一致）；
 * 订单号生成器多份副本 → 主键冲突 / 覆盖写。</p>
 *
 * @author 课程大作业
 */
public class SingletonDemo implements PatternDemo {

    /** 订单号日期段格式：ORD-20261010-0001。 */
    private static final DateTimeFormatter ORDER_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 并发线程数；每线程生成量，总数 = 8 × 200 = 1600。 */
    private static final int THREAD_COUNT = 8;
    private static final int PER_THREAD = 200;

    /**
     * 单独运行入口：与 {@link #run()} 共用同一段逻辑，保证「单独跑」和「菜单跑」输出一致。
     *
     * @param args 未使用
     */
    public static void main(String[] args) { new SingletonDemo().run(); }

    @Override
    public String name() { return "单例模式"; }

    @Override
    public String code() { return "01"; }

    @Override
    public String category() { return "创建型"; }

    @Override
    public String description() {
        return "平台全局配置 + 订单号生成器 + 通知渠道注册表：全 JVM 唯一实例，对应外卖平台的统一配置中心与唯一订单号发放。";
    }

    /** 执行演示：模式名 → 参与角色 → 调用过程 → 结论（不抛受检异常、不调用 System.exit）。 */
    @Override
    public void run() {
        System.out.println("============================================================");
        System.out.println("【" + name() + "】" + category() + "模式 · 编号 " + code());
        System.out.println("场景：" + description());
        System.out.println("============================================================");
        System.out.println();
        System.out.println("—— 参与角色 ——");
        System.out.println("  Singleton（单例角色）：PlatformConfig  —— 平台全局配置，双重检查锁懒加载");
        System.out.println("  Singleton（单例角色）：IdGenerator     —— 订单号生成器，静态内部类写法");
        System.out.println("  Singleton（单例角色）：NotificationChannelRegistry —— 通知渠道注册表，枚举写法");
        System.out.println("  全局访问点            ：getInstance() / INSTANCE");
        System.out.println("  客户端                ：SingletonDemo.run() 以及下面 8 个并发线程");

        demoDoubleCheckedLocking();
        demoIdGeneratorUnderConcurrency();
        demoEnumSingleton();
        printFiveFlavours();
        printConclusion();
    }

    /** ① 双重检查锁单例：证明多次 getInstance() 拿到同一个对象。 */
    private void demoDoubleCheckedLocking() {
        System.out.println();
        System.out.println("—— 调用过程 ——");
        System.out.println("1) 双重检查锁单例 PlatformConfig：连续 5 次调用 getInstance()，比较引用是否相同");
        PlatformConfig a = PlatformConfig.getInstance(), b = PlatformConfig.getInstance();
        PlatformConfig c = PlatformConfig.getInstance(), d = PlatformConfig.getInstance();
        PlatformConfig e = PlatformConfig.getInstance();
        System.out.println("   a==b : " + (a == b) + "，b==c : " + (b == c)
                + "，c==d : " + (c == d) + "，d==e : " + (d == e));
        System.out.println("   identityHashCode = " + System.identityHashCode(a) + " / " + System.identityHashCode(b)
                + " / " + System.identityHashCode(c) + " / " + System.identityHashCode(d)
                + " / " + System.identityHashCode(e));
        System.out.println("   私有构造器实际执行次数 = " + PlatformConfig.getConstructCount()
                + "（只应为 1，说明 5 次调用共享同一实例）");
        System.out.println("   平台名 = " + a.getPlatformName() + "，默认配送费率 = ￥" + a.getDefaultDeliveryFee()
                + "，高峰时段 = " + a.getPeakHours());
    }

    /** ② 静态内部类单例 + 8 线程并发：用 Set 去重证明订单号无重复。 */
    private void demoIdGeneratorUnderConcurrency() {
        System.out.println();
        System.out.println("2) 静态内部类单例 IdGenerator：启动 " + THREAD_COUNT + " 个线程，每线程生成 "
                + PER_THREAD + " 个订单号，收集进 Set 检查重复");
        IdGenerator generator = IdGenerator.getInstance();
        System.out.println("   getInstance() == getInstance() : " + (generator == IdGenerator.getInstance())
                + "，identityHashCode=" + System.identityHashCode(generator));

        Set<String> orderNos = ConcurrentHashMap.newKeySet();
        Set<Integer> instances = ConcurrentHashMap.newKeySet();
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(THREAD_COUNT);
        ExecutorService pool = Executors.newFixedThreadPool(THREAD_COUNT, r -> {
            Thread t = new Thread(r, "order-worker");
            t.setDaemon(true);
            return t;
        });
        try {
            for (int i = 0; i < THREAD_COUNT; i++) {
                pool.execute(() -> {
                    try {
                        startGate.await();
                        IdGenerator local = IdGenerator.getInstance();
                        instances.add(System.identityHashCode(local));
                        for (int j = 0; j < PER_THREAD; j++) {
                            orderNos.add(local.nextOrderNo());
                        }
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneGate.countDown();
                    }
                });
            }
            startGate.countDown();
            doneGate.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            System.out.println("   [警告] 并发演示被中断：" + ex.getMessage());
        } finally {
            pool.shutdownNow();
        }

        int expected = THREAD_COUNT * PER_THREAD;
        System.out.println("   期望数量 = " + expected + "，Set 实际数量 = " + orderNos.size()
                + "，是否无重复 = " + (orderNos.size() == expected));
        System.out.println("   8 个线程看到的 IdGenerator 实例个数 = " + instances.size() + "（应为 1）");
        System.out.println("   订单号样例（排序后前 3 个） = " + orderNos.stream().sorted().limit(3).toList());
        System.out.println("   下一个订单号 = " + generator.nextOrderNo() + "（格式 ORD-yyyyMMdd-序号，AtomicLong 保证递增）");
    }

    /** ③ 枚举单例：注册表天然线程安全，且防反射、防反序列化破坏。 */
    private void demoEnumSingleton() {
        System.out.println();
        System.out.println("3) 枚举单例 NotificationChannelRegistry：注册表作为唯一实例被复用");
        NotificationChannelRegistry registry = NotificationChannelRegistry.INSTANCE;
        System.out.println("   INSTANCE == valueOf(\"INSTANCE\") : "
                + (registry == NotificationChannelRegistry.valueOf("INSTANCE"))
                + "，identityHashCode=" + System.identityHashCode(registry));
        System.out.println("   已注册渠道数量 = " + registry.size());
        registry.describeAll().forEach(line -> System.out.println("     - " + line));
        System.out.println("   注册一个只在压测环境使用的渠道（同一实例就地修改，所有调用方立即可见）：");
        registry.register("PUSH", "APP 推送", true);
        System.out.println("   现在渠道数量 = " + registry.size() + "，且 INSTANCE.size() = "
                + NotificationChannelRegistry.INSTANCE.size() + "（同一份数据）");
    }

    /** 五种单例写法的取舍对比，直接可用于课程报告。 */
    private void printFiveFlavours() {
        System.out.println();
        System.out.println("—— 五种写法取舍（本 Demo 使用第 3、4、5 种）——");
        System.out.println("   1. 饿汉式：类加载即创建，简单且线程安全；缺点是启动即占资源，配置类会拖慢启动。");
        System.out.println("   2. 懒汉式 + 方法级 synchronized：绝对安全，但每次读配置都要抢锁，高并发下成为热点。");
        System.out.println("   3. 双重检查锁 DCL：只在首次创建时加锁，之后走无锁快路径；实例必须 volatile，"
                + "否则可能拿到构造未完成的半成品。本 Demo 的 PlatformConfig 即此写法。");
        System.out.println("   4. 静态内部类 Holder：借 JVM 类加载机制同时获得线程安全与懒加载，代码最简洁。"
                + "本 Demo 的 IdGenerator 即此写法。");
        System.out.println("   5. 枚举：最推荐，天然线程安全、防反射、防反序列化破坏；缺点是不能继承其他类。"
                + "本 Demo 的 NotificationChannelRegistry 即此写法。");
        System.out.println("   补充：前四种都能被反射（setAccessible）或反序列化破坏，只有枚举真正免疫。");
    }

    /** 结论小节。 */
    private void printConclusion() {
        System.out.println();
        System.out.println("—— 结论 / 为什么这么设计 ——");
        System.out.println("   1. 全局配置必须单例：费率、高峰时段是全站共识，多份副本会让同一单结算金额不一致；");
        System.out.println("   2. 订单号生成器必须单例：「唯一订单号」只能由「唯一序号发生器」保证，"
                + "每单 new 一个则 AtomicLong 各自从 0 起算，必然撞号；");
        System.out.println("   3. 渠道注册表用枚举单例：多条通知链路共享同一份渠道表，枚举写法最不容易被写出第二个实例；");
        System.out.println("   4. 单例的代价是全局可变状态，因此配置类字段全 final、只暴露只读方法，"
                + "可写单例（注册表）用 LinkedHashMap + 单一实例保证一致；");
        System.out.println("   5. 单例也带来测试耦合，生产代码通常把单例作为「默认实现」注入，便于替换测试桩。");
        System.out.println("============================================================");
    }

    // ==================== 辅助类型（同文件包级类，不额外占用文件） ====================

    /** 平台全局配置：双重检查锁（DCL）懒汉单例；字段全 final，创建后不可变。 */
    static final class PlatformConfig {

        /** 构造计数器，仅用于向演示证明「构造器只执行了一次」。 */
        private static final AtomicInteger CONSTRUCT_COUNT = new AtomicInteger(0);

        /** 单例引用：必须 volatile，DCL 才成立（保证可见性并禁止重排序）。 */
        private static volatile PlatformConfig instance;

        private final String platformName;
        private final BigDecimal defaultDeliveryFee;
        private final String peakHours;

        private PlatformConfig() {
            CONSTRUCT_COUNT.incrementAndGet();
            this.platformName = "饱了么外卖平台";
            this.defaultDeliveryFee = new BigDecimal("4.50");
            this.peakHours = "11:00-13:00, 17:30-19:30";
        }

        /** 全局访问点：双重检查锁，只有首次创建时加锁。 */
        static PlatformConfig getInstance() {
            PlatformConfig local = instance;
            if (local == null) {                     // 第一次检查：无锁快路径
                synchronized (PlatformConfig.class) {
                    local = instance;
                    if (local == null) {             // 第二次检查：并发下只创建一次
                        local = new PlatformConfig();
                        instance = local;
                    }
                }
            }
            return local;
        }

        static int getConstructCount() { return CONSTRUCT_COUNT.get(); }
        String getPlatformName() { return platformName; }
        BigDecimal getDefaultDeliveryFee() { return defaultDeliveryFee; }
        String getPeakHours() { return peakHours; }
    }

    /** 订单号生成器：静态内部类（Holder）单例，序号用 AtomicLong 的 CAS 保证并发不重号。 */
    static final class IdGenerator {

        private static final AtomicLong SEQUENCE = new AtomicLong(0L);

        /** 私有构造器：禁止外部 new 出第二个生成器。 */
        private IdGenerator() { }

        static IdGenerator getInstance() { return Holder.INSTANCE; }

        /** 生成形如 ORD-20261010-0001 的全局唯一订单号。 */
        String nextOrderNo() {
            long seq = SEQUENCE.incrementAndGet();
            return String.format("ORD-%s-%04d", LocalDate.now().format(ORDER_DATE), seq);
        }

        /** 静态内部类：真正持有单例，触发时机由 JVM 决定（首次访问才加载）。 */
        private static final class Holder {
            private static final IdGenerator INSTANCE = new IdGenerator();
        }
    }

    /** 通知渠道注册表：枚举单例，抗反射、抗反序列化破坏。 */
    enum NotificationChannelRegistry {

        /** 唯一实例。 */
        INSTANCE;

        private final Map<String, String> channels = new LinkedHashMap<>();
        private final Map<String, Boolean> enabled = new LinkedHashMap<>();

        NotificationChannelRegistry() {
            register("SMS", "短信通知", true);
            register("WECHAT", "微信服务通知", true);
            register("IN_APP", "站内信", false);
        }

        /** 注册（或覆盖）一个通知渠道。 */
        void register(String code, String label, boolean open) {
            channels.put(code, label);
            enabled.put(code, open);
        }

        int size() { return channels.size(); }

        /** 输出渠道清单，供演示打印。 */
        List<String> describeAll() {
            return channels.entrySet().stream()
                    .map(e -> e.getKey() + " -> " + e.getValue() + "（"
                            + (Boolean.TRUE.equals(enabled.get(e.getKey())) ? "启用" : "停用") + "）")
                    .toList();
        }
    }
}
