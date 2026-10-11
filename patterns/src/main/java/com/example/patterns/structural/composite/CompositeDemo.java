package com.example.patterns.structural.composite;

import com.example.patterns.PatternDemo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 【结构型】组合模式（Composite）—— 菜单树与套餐计价。
 *
 * <p>用了什么模式：组合模式。把「单品（叶子）」与「套餐/分类（容器）」统一到抽象 MenuComponent 下形成树，
 * 容器把 print() / price() 递归转发给子节点，客户端对单个对象和组合对象一视同仁。</p>
 * <p>为什么用：外卖菜单天然是树（菜单 → 分类 → 套餐 → 单品），运营要求「整棵树打印」「整棵树求和」，
 * 组合模式让递归代码只写一份，深度任意增加都不用改客户端。</p>
 * <p>不用会怎样：必须区分 Dish 与 Combo 两套类型，计价要写「单品取 price，套餐则遍历 children 递归」，
 * 每多一层嵌套（分类里放套餐、套餐里再放套餐）就多一层 instanceof 判断，极易漏分支。</p>
 * <p>透明式 vs 安全式：本 Demo 用折中写法 —— 抽象类给 add/remove/children 的默认实现（抛异常），
 * 容器覆写。客户端可以统一调用（透明式的便利），越界调用立刻报错（安全式的效果）；
 * 若要求绝对安全，可把这三个方法只放组合类，代价是客户端必须 instanceof 判断、丧失统一性。</p>
 */
public class CompositeDemo implements PatternDemo {
    private static final BigDecimal COMBO_DISCOUNT = new BigDecimal("0.90");
    public static void main(String[] args) {
        new CompositeDemo().run();
    }
    public CompositeDemo() {
        // 无需初始化状态
    }
    @Override public String name() { return "组合模式"; }
    @Override public String code() { return "05"; }
    @Override public String category() { return "结构型"; }
    @Override public String description() { return "菜单树与套餐计价：单品/套餐/分类一视同仁，递归打印与递归求和"; }
    @Override public void run() {
        MenuComponent all = buildMenuTree();
        System.out.println("==========================================================");
        System.out.println("【" + code() + "】" + name() + "（" + category() + "）");
        System.out.println("场景：" + description());
        System.out.println("==========================================================");
        System.out.println();
        System.out.println("── 一、参与角色 ──");
        System.out.println("  Component（抽象节点）：MenuComponent —— name() / price() / print(indent) 统一接口");
        System.out.println("  Leaf（叶子节点）：DishLeaf 单品；Composite（组合节点）：ComboMeal 套餐（子项和 × "
            + COMBO_DISCOUNT + "）、MenuCategory 分类（子项和，无折扣）");
        System.out.println("  Client（客户端）：只持有根节点 MenuComponent，一次调用遍历整棵树");
        // 1. 客户端只写一行 print，整棵树自己递归
        System.out.println();
        System.out.println("── 二、调用过程：客户端只写一行 root.print(\"\")，树自己递归 ──");
        System.out.println();
        System.out.println();
        all.print("");
        List<MenuComponent> leaves = collectLeaves(all);
        System.out.println("  （缩进层级 = 树的深度；节点数 = " + countNodes(all)
            + "，其中叶子单品 " + leaves.size() + " 个）");
        // 2. 递归求和，并与「叶子之和」对照
        System.out.println();
        System.out.println("── 三、递归计价 ──");
        System.out.println("  整棵菜单 price() 合计 = " + all.price() + " 元   （同样是同一行调用）");
        for (MenuComponent child : all.children()) {
            System.out.println("    分类「" + child.name() + "」price() = " + child.price() + " 元");
        }
        BigDecimal leafSum = BigDecimal.ZERO;
        StringBuilder detail = new StringBuilder();
        for (MenuComponent leaf : leaves) {
            leafSum = leafSum.add(leaf.price());
            detail.append(leaf.name()).append("(").append(leaf.price()).append(") ");
        }
        System.out.println("  一致性校验：所有叶子单价之和 = " + leafSum + " 元  ← " + detail.toString().trim());
        System.out.println("    套餐折扣逐层生效，所以整棵树合计必然小于叶子之和，差额就是套餐让利。");
        // 3. 透明式 / 安全式取舍：实测叶子节点上调用 add 会怎样
        System.out.println();
        System.out.println("── 四、透明式 vs 安全式（add/remove 放在哪一层） ──");
        System.out.println("  [透明式] add 声明在抽象类上，客户端无脑调用：MenuComponent c = 任意节点; c.add(...)");
        System.out.println("  [安全式] add 只在组合类上，客户端必须强转：if (node instanceof ComboMeal combo) {...}");
        System.out.println("  本 Demo 折中做法：抽象类给默认实现（抛异常），容器覆写。实测——");
        MenuComponent leaf = new DishLeaf("测试单品", new BigDecimal("1.00"));
        try {
            leaf.add(new DishLeaf("非法子节点", BigDecimal.ONE));
            System.out.println("      [意外] 叶子 add 竟然成功，说明抽象类里不该放默认空实现");
        } catch (UnsupportedOperationException ex) {
            System.out.println("      叶子.add(...) → UnsupportedOperationException：" + ex.getMessage());
            System.out.println("      报错明确、不会静默丢数据，同时客户端仍能用同一类型写递归。");
        }
        MenuCategory probe = new MenuCategory("测试分类");
        probe.add(new DishLeaf("新品尝鲜堡", new BigDecimal("21.00")));
        System.out.println("      组合.add(...) → 成功，当前「" + probe.name() + "」price() = " + probe.price() + " 元");
        probe.remove(probe.children().get(0));
        System.out.println("      再 remove 第一个子项后 price() = " + probe.price() + " 元");
        System.out.println();
        System.out.println("  根节点仍是同一类型：" + all.name() + "，客户端递归代码一行未改。");
        // 4. 结论
        System.out.println();
        System.out.println("── 五、结论 ──");
        System.out.println("  1. 组合模式 = 树形结构 + 统一接口：叶子与容器同一抽象，客户端无需 if (是套餐吗)；");
        System.out.println("  2. 递归天然适配：容器把 print()/price() 转发给子节点，深度任意、代码不变；");
        System.out.println("  3. 新增「加料包」「季节限定分类」只需新增节点，遍历逻辑零改动；");
        System.out.println("  4. 透明式客户端简单但有「叶子也能 add」的语义陷阱；安全式类型安全但要 instanceof；");
        System.out.println("  5. 代价：为统一接口，叶子也被迫实现与自己无关的方法（本 Demo 用默认抛异常缓解）。");
        System.out.println();
        System.out.println("==========================================================");
    }

    /** 组装菜单树：根分类 → 主食 / 小食饮品 / 超值套餐 → 单品与套餐；add 返回自身，可链式搭建。 */
    private static MenuComponent buildMenuTree() {
        MenuComponent burger = new DishLeaf("香辣鸡腿堡", new BigDecimal("19.80"));
        MenuComponent fries = new DishLeaf("黄金薯条", new BigDecimal("9.90"));
        MenuComponent cola = new DishLeaf("冰镇可乐", new BigDecimal("6.50"));
        DishLeaf burger2 = new DishLeaf("香辣鸡腿堡", new BigDecimal("19.80"));
        DishLeaf fries2 = new DishLeaf("黄金薯条", new BigDecimal("9.90"));
        DishLeaf cola2 = new DishLeaf("冰镇可乐", new BigDecimal("6.50"));
        // 套餐是容器，可以装单品；双人餐里再放一份重复单品（add 返回 MenuComponent，可直接链式调用）
        MenuComponent singleCombo = new ComboMeal("单人超值餐", COMBO_DISCOUNT).add(burger).add(cola);
        MenuComponent doubleCombo = new ComboMeal("双人分享餐", COMBO_DISCOUNT)
            .add(burger2).add(burger2).add(fries2).add(cola2);
        // 分类也是容器，说明容器可以嵌套不同语义的容器
        return new MenuCategory("全部菜单")
            .add(new MenuCategory("主食").add(burger))
            .add(new MenuCategory("小食饮品").add(fries).add(cola))
            .add(new MenuCategory("超值套餐").add(singleCombo).add(doubleCombo));
    }
    private static BigDecimal scale2(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
    private static int countNodes(MenuComponent node) {
        int total = 1;
        for (MenuComponent child : node.children()) {
            total += countNodes(child);
        }
        return total;
    }
    private static List<MenuComponent> collectLeaves(MenuComponent node) {
        List<MenuComponent> children = node.children();
        List<MenuComponent> out = new ArrayList<>();
        if (children.isEmpty()) {
            out.add(node);
            return out;
        }
        for (MenuComponent child : children) {
            out.addAll(collectLeaves(child));
        }
        return out;
    }
}

// Component（抽象节点）：单品与套餐/分类的共同父类型，统一 name()/price()/print(indent)。
// price() 与 print() 是真正的统一接口；add/remove/children 给默认实现（抛异常）由组合类覆写，
// 兼顾客户端统一调用与叶子的语义安全 —— 覆写时不能降低可见性，否则编译报错。

abstract class MenuComponent {
    abstract String name();
    abstract BigDecimal price();
    abstract void print(String indent);
    public MenuComponent add(MenuComponent child) {
        throw new UnsupportedOperationException("叶子节点「" + name() + "」不能添加子节点，只有套餐/分类可以");
    }
    public void remove(MenuComponent child) {
        throw new UnsupportedOperationException("叶子节点「" + name() + "」没有子节点可移除");
    }
    public List<MenuComponent> children() { return Collections.emptyList(); }
    static BigDecimal scale2(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
}

// Leaf（叶子节点）：单品菜品，price() 就是自己的单价

class DishLeaf extends MenuComponent {
    private final String dishName;
    private final BigDecimal unitPrice;
    DishLeaf(String dishName, BigDecimal unitPrice) {
        this.dishName = dishName;
        this.unitPrice = unitPrice;
    }
    @Override
    String name() { return dishName; }
    @Override
    BigDecimal price() { return scale2(unitPrice); }
    @Override
    void print(String indent) {
        System.out.println(indent + "· 单品：" + dishName + "  " + price() + " 元");
    }
}

// Composite（组合节点）：套餐，价格 = 子项之和 × 折扣率；套餐里还可以再放套餐

class ComboMeal extends MenuComponent {
    private final String comboName;
    private final BigDecimal discount;
    private final List<MenuComponent> items = new ArrayList<>();
    ComboMeal(String comboName, BigDecimal discount) {
        this.comboName = comboName;
        this.discount = discount;
    }
    @Override
    String name() { return comboName; }
    @Override
    BigDecimal price() {
        BigDecimal sum = BigDecimal.ZERO;
        for (MenuComponent item : items) {
            sum = sum.add(item.price());
        }
        return scale2(sum.multiply(discount));
    }
    @Override
    void print(String indent) {
        System.out.println(indent + "◆ 套餐：" + comboName + "  " + price() + " 元（折扣 " + discount + "）");
        for (MenuComponent item : items) {
            item.print(indent + "    ");
        }
    }

    /**
     * 添加子节点，并返回<b>本组合节点自身</b>（协变返回类型）。
     *
     * <p><b>为什么返回 ComboMeal 而不是 MenuComponent：</b>
     * 父类签名返回 MenuComponent 时，链式写法
     * {@code new ComboMeal(...).add(a).add(b)} 在第二次 add 时会退化成
     * MenuComponent 类型（编译期类型），从而无法继续调用组合节点特有的方法。
     * 用协变返回类型既满足父类契约，又让链式 API 保持精确类型——这是组合模式常见的实现技巧。</p>
     *
     * @param child 子节点
     * @return 本套餐对象
     */
    @Override
    public ComboMeal add(MenuComponent child) {
        items.add(child);
        return this;
    }
    @Override public void remove(MenuComponent child) {
        items.remove(child);
    }
    @Override public List<MenuComponent> children() { return Collections.unmodifiableList(items); }
}

// Composite（组合节点）：菜单分类，价格 = 子项之和（不参与折扣）

class MenuCategory extends MenuComponent {
    private final String categoryName;
    private final List<MenuComponent> nodes = new ArrayList<>();
    MenuCategory(String categoryName) {
        this.categoryName = categoryName;
    }
    @Override
    String name() { return categoryName; }
    @Override
    BigDecimal price() {
        BigDecimal sum = BigDecimal.ZERO;
        for (MenuComponent node : nodes) {
            sum = sum.add(node.price());
        }
        return scale2(sum);
    }
    @Override
    void print(String indent) {
        System.out.println(indent + "【分类】" + categoryName + "  小计 " + price() + " 元");
        for (MenuComponent node : nodes) {
            node.print(indent + "    ");
        }
    }
    @Override public MenuComponent add(MenuComponent child) {
        nodes.add(child);
        return this;
    }
    @Override public void remove(MenuComponent child) {
        nodes.remove(child);
    }
    @Override public List<MenuComponent> children() { return Collections.unmodifiableList(nodes); }
}
