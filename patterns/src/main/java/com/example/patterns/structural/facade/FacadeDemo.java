package com.example.patterns.structural.facade;

import com.example.patterns.PatternDemo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 【结构型】外观模式（Facade）—— 一键下单。
 *
 * <p>用了什么模式：外观模式。OrderFacade 把「校验 → 计价 → 扣库存 → 支付 → 派单」这一串子系统调用
 * 收敛成一个 placeOrder(...) 方法，顺序、回滚、结果汇总都在外观里只写一次。</p>
 * <p>为什么用：下单顺序有业务含义（库存必须在支付前扣，配送费要先知道距离才能算实付）。
 * 若小程序、H5、客服后台各自拼这串流程，一旦有人写反就会出现「已支付但无库存」这类事故。</p>
 * <p>不用会怎样：客户端要认识 5 个子系统、记住 12 个方法的调用顺序，漏调一步没有编译错误，
 * 只会在运行时炸；子系统改签名时所有调用点都要跟着改。</p>
 * <p>重要澄清：外观是「提供便捷入口」，不是「封装墙」。它不禁止客户端直接访问子系统 ——
 * 本 Demo 第五节演示「绕过外观直接查库存」「只重试支付」仍然可行。</p>
 */
public class FacadeDemo implements PatternDemo {
    private static final String USER_ID = "U10086";
    private static final String MERCHANT_ID = "M2001";
    private static final List<String> DISH_IDS =
        new ArrayList<>(Arrays.asList("D-BURGER-01", "D-FRIES-02", "D-COLA-03"));
    private static final String COUPON_ID = "CP-5YUAN";
    private static final String PAYMENT_METHOD = "WECHAT";
    private int manualCalls;
    public static void main(String[] args) {
        new FacadeDemo().run();
    }
    public FacadeDemo() {
        // 无需初始化状态
    }
    @Override public String name() { return "外观模式"; }
    @Override public String code() { return "07"; }
    @Override public String category() { return "结构型"; }
    @Override public String description() { return "一键下单：把校验/计价/扣库存/支付/派单 5 个子系统的固定调用顺序收敛成一个方法"; }
    @Override public void run() {
        var validator = new OrderValidatorSubsystem();
        var calculator = new PriceCalculatorSubsystem();
        var inventory = new InventorySubsystem();
        var payment = new PaymentSubsystem();
        var dispatch = new DispatchSubsystem();
        BigDecimal distance = new BigDecimal("2.8");
        String orderId = "WM20240517007";
        int stockBefore = inventory.stockOf("D-COLA-03");
        System.out.println("==========================================================");
        System.out.println("【" + code() + "】" + name() + "（" + category() + "）");
        System.out.println("场景：" + description());
        System.out.println("==========================================================");
        System.out.println();
        System.out.println("── 一、参与角色 ──");
        System.out.println("  Facade（外观）：OrderFacade —— 只暴露 placeOrder(userId, merchantId, dishIds, couponId, payMethod)");
        System.out.println("  子系统1 OrderValidatorSubsystem：validateNotNull/validateMinAmount/validateStock（校验链有序）");
        System.out.println("  子系统2 PriceCalculatorSubsystem：calcSubtotal/calcDiscount/calcDeliveryFee/calcPayable");
        System.out.println("  子系统3 PaymentSubsystem：pay(orderId, payable, method)；子系统4 DispatchSubsystem：matchRider/estimateArrival");
        System.out.println("  子系统5 InventorySubsystem：check/deduct/restore；Client：小程序/H5/客服后台只依赖 OrderFacade");
        System.out.println();
        System.out.println("  演示订单：" + USER_ID + " 在商家 " + MERCHANT_ID + " 点 " + DISH_IDS.size() + " 个菜，券 " + COUPON_ID);
        // 1. 不用外观：客户端自己按顺序调 12 个方法
        System.out.println();
        System.out.println("── 二、不用外观：客户端要自己按正确顺序调用 5 个子系统的 12 个方法 ──");
        System.out.println("   （每一步都是一个对象 + 一个方法名 + 一组参数，全都要客户端自己记住）");
        System.out.println();
        System.out.println("  [1] validator.validateNotNull(...)   → " + count(validator.validateNotNull(USER_ID, MERCHANT_ID, DISH_IDS)));
        System.out.println("  [2] validator.validateMinAmount(...) → " + count(validator.validateMinAmount(DISH_IDS)));
        System.out.println("  [3] validator.validateStock(...)     → " + count(validator.validateStock(DISH_IDS)));
        System.out.println("  [4] calculator.calcSubtotal(...)     → 小计 " + count(calculator.calcSubtotal(DISH_IDS)) + " 元");
        System.out.println("  [5] calculator.calcDiscount(...)     → 优惠 " + count(calculator.calcDiscount(DISH_IDS, COUPON_ID)) + " 元");
        System.out.println("  [6] calculator.calcDeliveryFee(2.8)  → 配送费 " + count(calculator.calcDeliveryFee(distance)) + " 元");
        System.out.println("  [7] calculator.calcPayable(...)      → 实付 " + count(calculator.calcPayable(DISH_IDS, COUPON_ID, distance)) + " 元");
        System.out.println("  [8] inventory.check(...)             → " + count(inventory.check(DISH_IDS)));
        System.out.println("  [9] inventory.deduct(...)            → " + count(inventory.deduct(DISH_IDS)) + "（顺序错了就会「付了钱没菜」）");
        System.out.println("  [10] payment.pay(订单, 实付, 渠道)   → 渠道 " + PAYMENT_METHOD + "，" + count(payment.pay(orderId, calculator.calcPayable(DISH_IDS, COUPON_ID, distance), PAYMENT_METHOD)));
        System.out.println("  [11] dispatch.estimateArrival(2.8)   → " + count(dispatch.estimateArrival(distance)) + " 分钟");
        System.out.println("  [12] dispatch.matchRider(orderId)    → " + count(dispatch.matchRider(orderId)));
        System.out.println();
        System.out.println("  共 " + manualCalls + " 次手工调用、8 个参数拼装点；");
        System.out.println("  任意一步顺序写反（如先 pay 后 deduct）都不会编译报错，只会在运行时变成事故。");
        // 2. 用外观：一行调用完成同样的 12 次调用
        System.out.println();
        System.out.println("── 三、用外观：一行调用完成 ──");
        System.out.println();
        System.out.println("  客户端代码：");
        System.out.println("      String summary = orderFacade.placeOrder(\"" + USER_ID + "\", \"" + MERCHANT_ID
            + "\", dishIds, \"" + COUPON_ID + "\", \"" + PAYMENT_METHOD + "\");");
        System.out.println();
        OrderFacade facade = new OrderFacade();
        System.out.println("  返回结果摘要：");
        System.out.println("      " + facade.placeOrder(USER_ID, MERCHANT_ID, DISH_IDS, COUPON_ID, PAYMENT_METHOD));
        System.out.println();
        System.out.println("  外观内部按固定顺序调用了 5 个子系统（子系统自己不打印，由外观记录日志）：");
        int step = 1;
        for (String line : facade.callLog()) {
            System.out.println("      " + step + ". " + line);
            step++;
        }
        // 3. 对照
        System.out.println();
        System.out.println("── 四、对照 ──");
        System.out.println("  维度          不用外观                                用外观");
        System.out.println("  客户端代码量  12 次调用 + 5 次 new + 参数拼装 ≈ 20 行  1 行 placeOrder(...)");
        System.out.println("  需要知道的类  5 个子系统类 + 12 个方法名              1 个 OrderFacade");
        System.out.println("  顺序正确性    每个调用点自己保证，易错                顺序写在唯一入口，改一处全局生效");
        System.out.println("  失败回滚      客户端自己写（退库存、退款）            外观里统一处理并给出明确失败原因");
        System.out.println("  新增入口      小程序/H5/客服后台各写一遍              都复用同一个外观");
        // 4. 外观不是封装墙
        System.out.println();
        System.out.println("── 五、外观不禁止直接访问子系统（不是封装墙） ──");
        System.out.println();
        System.out.println("  场景一：运营后台只想看库存（不需要下单）→ 绕过外观直接问子系统：");
        System.out.println("      new InventorySubsystem().stockOf(\"D-COLA-03\") → 还剩 " + stockBefore + " 份（下单前）");
        System.out.println();
        System.out.println("  场景二：对失败订单做「只重试支付」的定制流程 → 只依赖 PaymentSubsystem：");
        System.out.println("      payment.pay(\"WM-RETRY-001\", new BigDecimal(\"40.70\"), \"ALIPAY\") → "
            + payment.pay("WM-RETRY-001", new BigDecimal("40.70"), "ALIPAY"));
        System.out.println();
        System.out.println("  结论：外观把「常用路径」变简单，但没有堵死「特殊路径」。");
        // 5. 结论
        System.out.println();
        System.out.println("── 六、结论 ──");
        System.out.println("  1. 外观 = 为复杂子系统提供「高层统一入口」，客户端与子系统解耦；");
        System.out.println("  2. 它不新增功能，只做「编排 + 顺序保证 + 结果汇总」，子系统可独立演进；");
        System.out.println("  3. vs 适配器：适配器改接口形状以兼容；外观只是包装出更方便的入口，子系统接口不动；");
        System.out.println("  4. vs 中介者：中介者让子系统互不认识、转发双向通信；外观是客户端侧的单向门面；");
        System.out.println("  5. 注意：外观不该变成「上帝类」，业务规则留在子系统，外观只负责流程编排。");
        System.out.println();
        System.out.println("==========================================================");
    }
    private <T> T count(T value) {
        manualCalls++;
        return value;
    }
}


class OrderFacade {
    private final OrderValidatorSubsystem validator = new OrderValidatorSubsystem();
    private final PriceCalculatorSubsystem calculator = new PriceCalculatorSubsystem();
    private final InventorySubsystem inventory = new InventorySubsystem();
    private final PaymentSubsystem payment = new PaymentSubsystem();
    private final DispatchSubsystem dispatch = new DispatchSubsystem();
    private final List<String> log = new ArrayList<>();


    /** 一键下单，固定顺序 = 校验 → 计价 → 扣库存 → 支付 → 派单；成功返回摘要，失败返回原因。 */
    String placeOrder(String userId, String merchantId, List<String> dishIds, String couponId, String paymentMethod) {
        log.clear();
        BigDecimal distance = new BigDecimal("2.8");
        // 1 校验链：任一失败立即返回，绝不进入扣库存与支付
        if (!validator.validateNotNull(userId, merchantId, dishIds)) {
            return "下单失败 | 原因=参数校验失败：用户/商家/菜品不能为空";
        }
        log.add("[子系统1 校验] validateNotNull → 通过");
        if (!validator.validateMinAmount(dishIds)) {
            return "下单失败 | 原因=未达起送价";
        }
        if (!validator.validateStock(dishIds)) {
            return "下单失败 | 原因=库存不足";
        }
        log.add("[子系统1 校验] validateMinAmount + validateStock → 通过");
        // 2 计价链：距离先定，才能定配送费，才能算实付
        BigDecimal payable = calculator.calcPayable(dishIds, couponId, distance);
        log.add("[子系统2 计价] 小计 " + calculator.calcSubtotal(dishIds) + "，优惠 "
            + calculator.calcDiscount(dishIds, couponId) + "，配送费 "
            + calculator.calcDeliveryFee(distance) + " → 实付 " + payable);
        // 3 先扣库存再收钱（失败要回滚）
        if (!inventory.check(dishIds)) {
            return "下单失败 | 原因=库存不足";
        }
        inventory.deduct(dishIds);
        log.add("[子系统5 库存] check + deduct → 已锁定库存");
        // 4 支付；失败则把刚扣的库存退回去
        String orderId = "WM" + System.currentTimeMillis() % 100000;
        if (!payment.pay(orderId, payable, paymentMethod)) {
            inventory.restore(dishIds);
            return "下单失败 | 原因=支付失败，已回滚库存";
        }
        log.add("[子系统3 支付] pay(" + orderId + ", " + payable + ", " + paymentMethod + ") → 成功");
        // 5 派单
        int eta = dispatch.estimateArrival(distance);
        String rider = dispatch.matchRider(orderId);
        log.add("[子系统4 派单] 匹配骑手 " + rider + "，预计 " + eta + " 分钟送达");
        return "下单成功 | 订单号=" + orderId + " | 实付=" + payable + " 元 | 支付=" + paymentMethod
            + " | 骑手=" + rider + " | 预计 " + eta + " 分钟";
    }
    List<String> callLog() { return new ArrayList<>(log); }
}


// ==== 子系统：各有自己的复杂接口（本 Demo 用确定性的模拟实现） ====


class OrderValidatorSubsystem {
    private static final BigDecimal MIN_AMOUNT = new BigDecimal("20.00");
    static final Map<String, BigDecimal> DISH_PRICES = new LinkedHashMap<>();
    static {
        DISH_PRICES.put("D-BURGER-01", new BigDecimal("19.80"));
        DISH_PRICES.put("D-FRIES-02", new BigDecimal("9.90"));
        DISH_PRICES.put("D-COLA-03", new BigDecimal("6.50"));
    }
    boolean validateNotNull(String userId, String merchantId, List<String> dishIds) { return userId != null && !userId.trim().isEmpty()
            && merchantId != null && !merchantId.trim().isEmpty()
            && dishIds != null && !dishIds.isEmpty(); }
    boolean validateMinAmount(List<String> dishIds) {
        BigDecimal subtotal = BigDecimal.ZERO;
        for (String dishId : dishIds) {
            subtotal = subtotal.add(DISH_PRICES.getOrDefault(dishId, BigDecimal.ZERO));
        }
        return subtotal.compareTo(MIN_AMOUNT) >= 0;
    }
    boolean validateStock(List<String> dishIds) { return new InventorySubsystem().check(dishIds); }
}


class PriceCalculatorSubsystem {
    private static final BigDecimal COUPON_THRESHOLD = new BigDecimal("35.00");
    BigDecimal calcSubtotal(List<String> dishIds) {
        BigDecimal subtotal = BigDecimal.ZERO;
        for (String dishId : dishIds) {
            subtotal = subtotal.add(OrderValidatorSubsystem.DISH_PRICES.getOrDefault(dishId, BigDecimal.ZERO));
        }
        return subtotal.setScale(2, RoundingMode.HALF_UP);
    }
    BigDecimal calcDiscount(List<String> dishIds, String couponId) {
        if ("CP-5YUAN".equals(couponId) && calcSubtotal(dishIds).compareTo(COUPON_THRESHOLD) >= 0) {
            return new BigDecimal("5.00");
        }
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
    BigDecimal calcDeliveryFee(BigDecimal distanceKm) {
        BigDecimal fee = new BigDecimal("4.00");
        if (distanceKm.compareTo(new BigDecimal("3.0")) > 0) {
            fee = fee.add(distanceKm.subtract(new BigDecimal("3.0")));
        }
        return fee.setScale(2, RoundingMode.HALF_UP);
    }
    BigDecimal calcPayable(List<String> dishIds, String couponId, BigDecimal distanceKm) { return calcSubtotal(dishIds).subtract(calcDiscount(dishIds, couponId))
            .add(calcDeliveryFee(distanceKm)).setScale(2, RoundingMode.HALF_UP); }
}


class PaymentSubsystem {
    boolean pay(String orderId, BigDecimal payable, String paymentMethod) { return orderId != null && payable != null && payable.compareTo(BigDecimal.ZERO) > 0
            && ("WECHAT".equals(paymentMethod) || "ALIPAY".equals(paymentMethod) || "BANK".equals(paymentMethod)); }
}


class DispatchSubsystem {
    private static final String[] RIDERS = {"骑手-阿强", "骑手-小林", "骑手-老王"};
    int estimateArrival(BigDecimal distanceKm) { return distanceKm.multiply(new BigDecimal("8")).add(new BigDecimal("10")).intValue(); }
    String matchRider(String orderId) { return RIDERS[orderId == null ? 0 : Math.abs(orderId.hashCode()) % RIDERS.length]; }
}


class InventorySubsystem {
    private final Map<String, Integer> stock = new LinkedHashMap<>();
    InventorySubsystem() { stock.put("D-BURGER-01", 50); stock.put("D-FRIES-02", 80); stock.put("D-COLA-03", 100); }
    int stockOf(String dishId) { return stock.getOrDefault(dishId, 0); }
    boolean check(List<String> dishIds) {
        if (dishIds == null) {
            return false;
        }
        for (String dishId : dishIds) {
            if (stockOf(dishId) <= 0) {
                return false;
            }
        }
        return true;
    }
    boolean deduct(List<String> dishIds) {
        if (!check(dishIds)) {
            return false;
        }
        for (String dishId : dishIds) {
            stock.put(dishId, stockOf(dishId) - 1);
        }
        return true;
    }
    void restore(List<String> dishIds) {
        if (dishIds == null) {
            return;
        }
        for (String dishId : dishIds) {
            stock.put(dishId, stockOf(dishId) + 1);
        }
    }
}
