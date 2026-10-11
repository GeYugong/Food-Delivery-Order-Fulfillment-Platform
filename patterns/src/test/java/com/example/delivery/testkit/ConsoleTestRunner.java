package com.example.delivery.testkit;

import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import java.io.PrintWriter;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectPackage;
import static org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request;

/**
 * 命令行测试运行器（不依赖 Maven 的 surefire，直接调用 JUnit Platform Launcher）。
 *
 * <p><b>为什么项目里需要它：</b></p>
 * <ul>
 *   <li>在没有 Maven 的环境里也能一键跑完全部单元测试；</li>
 *   <li>适合在 CI 或课程验收脚本里调用，退出码直接反映成功/失败（便于脚本判断）；</li>
 *   <li>比 {@code mvn test} 快得多（不启动 Maven 生命周期）。</li>
 * </ul>
 *
 * <p><b>它属于测试基础设施</b>：位于 {@code patterns} 模块的 test 源码树（该模块的测试
 * classpath 能解析到 {@code junit-platform-launcher}），并在父 POM 的 surefire 配置里被
 * {@code <exclude>} 排除，因此不会被 Maven 当作测试类加载。</p>
 *
 * <p><b>运行方式（把 junit 相关 jar 加入 classpath）：</b></p>
 * <pre>
 *   java -cp "..\delivery-platform\target\classes;target\test-classes;%JUNIT_JARS%" ^
 *        com.example.delivery.testkit.ConsoleTestRunner
 * </pre>
 * <p>不传参数时扫描 {@code com.example.delivery} 包；也可传入自定义包名。
 * 退出码 0 表示全部通过，1 表示存在失败用例。</p>
 *
 * @author 课程大作业
 */
public final class ConsoleTestRunner {

    /** 工具类不允许实例化。 */
    private ConsoleTestRunner() {
    }

    /**
     * 主方法：扫描 {@code com.example.delivery} 包下全部测试并输出汇总。
     *
     * @param args 可选：自定义要扫描的包名（默认 {@code com.example.delivery}）
     */
    public static void main(String[] args) {
        String packageName = args.length > 0 ? args[0] : "com.example.delivery";

        System.out.println("========================================================");
        System.out.println("  正在扫描测试包：" + packageName);
        System.out.println("========================================================");

        LauncherDiscoveryRequest discoveryRequest = request()
                .selectors(selectPackage(packageName))
                .build();

        Launcher launcher = LauncherFactory.create();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        launcher.execute(discoveryRequest, listener);

        TestExecutionSummary summary = listener.getSummary();
        PrintWriter writer = new PrintWriter(System.out);
        summary.printTo(writer);
        summary.printFailuresTo(writer, 30);
        writer.flush();

        System.out.println("========================================================");
        System.out.printf("  用例总数=%d  成功=%d  失败=%d  跳过=%d%n",
                summary.getTestsFoundCount(),
                summary.getTestsSucceededCount(),
                summary.getTestsFailedCount(),
                summary.getTestsSkippedCount());
        System.out.println(summary.getTestsFailedCount() == 0 ? "  结果：全部通过" : "  结果：存在失败用例");
        System.out.println("========================================================");

        // 用退出码表达结果，便于脚本/CI 判断
        if (summary.getTestsFailedCount() > 0) {
            System.exit(1);
        }
    }
}
