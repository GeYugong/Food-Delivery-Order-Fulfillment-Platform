package com.example.patterns;

import com.example.patterns.behavioral.chain.ChainOfResponsibilityDemo;
import com.example.patterns.behavioral.command.CommandDemo;
import com.example.patterns.behavioral.interpreter.InterpreterDemo;
import com.example.patterns.behavioral.mediator.MediatorDemo;
import com.example.patterns.behavioral.memento.MementoDemo;
import com.example.patterns.behavioral.observer.ObserverDemo;
import com.example.patterns.behavioral.state.StateDemo;
import com.example.patterns.behavioral.strategy.StrategyDemo;
import com.example.patterns.behavioral.template.TemplateMethodDemo;
import com.example.patterns.behavioral.visitor.VisitorDemo;
import com.example.patterns.creational.abstractfactory.AbstractFactoryDemo;
import com.example.patterns.creational.factorymethod.FactoryMethodDemo;
import com.example.patterns.creational.singleton.SingletonDemo;
import com.example.patterns.structural.adapter.AdapterDemo;
import com.example.patterns.structural.composite.CompositeDemo;
import com.example.patterns.structural.decorator.DecoratorDemo;
import com.example.patterns.structural.facade.FacadeDemo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;

/**
 * 设计模式实例库统一入口（交互式菜单）。
 *
 * <p><b>设计模式：简单工厂 / 注册表 + 外观</b></p>
 *
 * <p><b>为什么需要统一入口：</b>本模块有 17 个 Demo 类、覆盖 18 个设计模式
 * （解释器模式在 Demo 中同时演示了「解释器」与「访问者」的对比场景）。
 * 每个 Demo 都可以用 {@code java -cp patterns\target\classes 全限定名} 单独运行，
 * 但课堂演示时记住 17 条命令并不现实。</p>
 *
 * <p>于是这里维护一张「编号 → Demo 实例」的注册表：</p>
 * <ul>
 *   <li>调用方（菜单）只依赖 {@link PatternDemo} 抽象，不关心具体是哪个模式；</li>
 *   <li>新增一个模式 Demo，只需在 {@link #registry()} 里加一行；</li>
 *   <li>菜单顺序由 Demo 自己的 {@code code()} 决定，不需要在这里维护排序。</li>
 * </ul>
 *
 * <p><b>运行方式：</b></p>
 * <pre>
 *   java -cp patterns\target\classes com.example.patterns.PatternShowcase
 *   java -cp patterns\target\classes com.example.patterns.PatternShowcase 05   （直接跑 05 号 Demo）
 *   java -cp patterns\target\classes com.example.patterns.PatternShowcase all  （依次跑全部 Demo）
 * </pre>
 *
 * @author 课程大作业
 */
public final class PatternShowcase {

    /** 工具类不允许实例化。 */
    private PatternShowcase() {
    }

    /**
     * 程序入口。
     *
     * @param args 可选的 Demo 编号（如 {@code 05}）或 {@code all}
     */
    public static void main(String[] args) {
        List<PatternDemo> demos = registry();
        if (args.length > 0) {
            String arg = args[0].trim();
            if ("all".equalsIgnoreCase(arg)) {
                runAll(demos);
                return;
            }
            PatternDemo demo = findByCode(demos, arg);
            if (demo == null) {
                System.out.println("未找到编号为「" + arg + "」的 Demo，可用编号见下方菜单。");
            } else {
                runOne(demo);
                return;
            }
        }
        runInteractive(demos);
    }

    /**
     * Demo 注册表（简单工厂：集中列出全部 Demo）。
     *
     * @return 按编号排序的 Demo 列表
     */
    public static List<PatternDemo> registry() {
        List<PatternDemo> demos = new ArrayList<>(List.of(
                // ---------- 创建型 ----------
                new SingletonDemo(),            // 01 单例
                new FactoryMethodDemo(),        // 02 工厂方法
                new AbstractFactoryDemo(),      // 03 抽象工厂
                // ---------- 结构型 ----------
                new AdapterDemo(),              // 04 适配器
                new CompositeDemo(),            // 05 组合
                new DecoratorDemo(),            // 06 装饰器
                new FacadeDemo(),               // 07 外观
                // ---------- 行为型 ----------
                new ChainOfResponsibilityDemo(),// 08 职责链
                new CommandDemo(),              // 09 命令
                new InterpreterDemo(),          // 10 解释器
                new MediatorDemo(),             // 11 中介者
                new MementoDemo(),              // 12 备忘录
                new ObserverDemo(),             // 13 观察者
                new StateDemo(),                // 14 状态
                new StrategyDemo(),             // 15 策略
                new TemplateMethodDemo(),       // 16 模板方法
                new VisitorDemo()               // 17 访问者
        ));
        demos.sort(Comparator.comparing(PatternDemo::code));
        return demos;
    }

    /**
     * 交互式菜单。
     *
     * @param demos 全部 Demo
     */
    private static void runInteractive(List<PatternDemo> demos) {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            printBanner();
            printMenu(demos);
            System.out.print("请输入编号（回车 = 依次运行全部，q = 退出）: ");
            System.out.flush();
            if (!scanner.hasNextLine()) {
                return;
            }
            String input = scanner.nextLine().trim();
            if ("q".equalsIgnoreCase(input) || "quit".equalsIgnoreCase(input)) {
                System.out.println("已退出设计模式实例库。");
                return;
            }
            if (input.isEmpty() || "all".equalsIgnoreCase(input)) {
                runAll(demos);
                continue;
            }
            if ("demo".equalsIgnoreCase(input)) {
                // 依次运行全部 Demo 但不停顿，适合一次性产出完整演示日志
                demos.forEach(PatternShowcase::runOne);
                continue;
            }
            PatternDemo demo = findByCode(demos, input);
            if (demo == null) {
                System.out.println("无效编号：" + input + "，请重新输入。");
                continue;
            }
            runOne(demo);
        }
    }

    /**
     * 打印横幅。
     */
    private static void printBanner() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════╗");
        System.out.println("║            设计模式实例库 · Design Pattern Showcase               ║");
        System.out.println("║   17 个可独立运行的 Demo，覆盖 18 个设计模式（GoF 全谱）          ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════╝");
    }

    /**
     * 打印菜单（按分类分组）。
     *
     * @param demos 全部 Demo
     */
    private static void printMenu(List<PatternDemo> demos) {
        String currentCategory = "";
        for (PatternDemo demo : demos) {
            if (!demo.category().equals(currentCategory)) {
                currentCategory = demo.category();
                System.out.println();
                System.out.println("  ── " + currentCategory + "模式 ──────────────────────────────────");
            }
            System.out.printf("   %s. %-8s %s%n", demo.code(), demo.name(), demo.description());
        }
        System.out.println();
    }

    /**
     * 运行单个 Demo。
     *
     * @param demo Demo
     */
    public static void runOne(PatternDemo demo) {
        System.out.println();
        System.out.println("################################################################");
        System.out.printf("#  [%s] %s（%s）%n", demo.code(), demo.name(), demo.category());
        System.out.printf("#  %s%n", demo.description());
        System.out.println("################################################################");
        try {
            demo.run();
        } catch (RuntimeException e) {
            System.out.println("Demo 执行失败：" + e.getMessage());
        }
    }

    /**
     * 依次运行全部 Demo。
     *
     * @param demos 全部 Demo
     */
    private static void runAll(List<PatternDemo> demos) {
        for (PatternDemo demo : demos) {
            runOne(demo);
        }
        System.out.println();
        System.out.println("全部 " + demos.size() + " 个 Demo 执行完毕。");
    }

    /**
     * 按编号查找 Demo。
     *
     * @param demos 全部 Demo
     * @param code  编号
     * @return 命中的 Demo；未命中返回 null
     */
    private static PatternDemo findByCode(List<PatternDemo> demos, String code) {
        String normalized = code.trim();
        if (normalized.length() == 1) {
            normalized = "0" + normalized;
        }
        String finalCode = normalized;
        return demos.stream().filter(demo -> demo.code().equals(finalCode)).findFirst().orElse(null);
    }
}
