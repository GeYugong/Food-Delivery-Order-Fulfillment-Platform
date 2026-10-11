package com.example.patterns;

/**
 * 设计模式 Demo 的统一契约。
 *
 * <p><b>为什么要统一接口：</b>本模块有 19 个模式、19 个可独立运行的 {@code *Demo} 类。
 * 如果每个 Demo 只是各写各的 {@code main}，演示时就要记 19 条命令；
 * 抽出一个统一契约后，{@link PatternShowcase} 可以像「简单工厂 / 注册表」一样
 * 把全部 Demo 列出来并按键执行，课堂答辩时只需记住一个入口。</p>
 *
 * <p><b>实现约定（每个 Demo 都必须满足）：</b></p>
 * <ol>
 *   <li>必须实现本接口，并同时提供 {@code public static void main(String[] args)}，
 *       两者内部都调用同一个演示方法 {@link #run()}，保证「单独跑」和「菜单跑」结果一致；</li>
 *   <li>必须提供 public 无参构造器（{@link PatternShowcase} 通过反射/注册表实例化）；</li>
 *   <li>{@link #run()} 不允许抛出受检异常，也不允许调用 {@code System.exit}；</li>
 *   <li>输出需分节打印「模式名 → 参与角色 → 调用过程 → 结论」，便于与 PlantUML 图对照讲解。</li>
 * </ol>
 *
 * @author 课程大作业
 */
public interface PatternDemo {

    /**
     * 模式中文名，用于菜单展示。
     *
     * @return 例如 {@code "单例模式"}
     */
    String name();

    /**
     * 菜单短编号，建议直接使用两位数字字符串（如 {@code "01"}），保证菜单顺序稳定。
     *
     * @return 例如 {@code "01"}
     */
    String code();

    /**
     * 分类中文名，用于菜单分组。
     *
     * @return 例如 {@code "创建型"}
     */
    String category();

    /**
     * 一句话说明本 Demo 演示了什么，以及对应到外卖业务中的哪个场景。
     *
     * @return 例如 {@code "平台全局配置与订单号生成器：全局唯一实例 + 懒加载"}
     */
    String description();

    /**
     * 执行演示：把角色、调用顺序、结果打印到控制台。
     */
    void run();
}
