package com.example.delivery.app;

import com.example.delivery.bootstrap.DemoRunner;
import com.example.delivery.bootstrap.PlatformBootstrap;
import com.example.delivery.ui.ConsoleUI;

/**
 * 平台启动入口（整个业务系统的 {@code main} 所在类）。
 *
 * <p><b>设计模式关联：中介者模式（Mediator）的「客户端」+ 组合根</b></p>
 * <p>本类不包含任何业务逻辑：它只做三件事——</p>
 * <ol>
 *   <li>解析启动参数（选交互模式还是自动演示模式）；</li>
 *   <li>通过 {@link PlatformBootstrap} 装配所有组件（组合根）；</li>
 *   <li>把控制权交给控制台或演示脚本。</li>
 * </ol>
 * <p>业务逻辑全部在领域层与应用层，因此本类可以做到「换一个界面（Web/桌面）零改动」。</p>
 *
 * <p><b>启动方式：</b></p>
 * <pre>
 *   # 交互式控制台（默认）
 *   java -cp delivery-platform\target\classes com.example.delivery.app.DeliveryPlatformApplication
 *
 *   # 一键跑完整业务闭环演示（推荐用于答辩/验收）
 *   java -cp delivery-platform\target\classes com.example.delivery.app.DeliveryPlatformApplication --demo
 * </pre>
 *
 * @author 课程大作业
 */
public final class DeliveryPlatformApplication {

    /** 工具类不允许实例化。 */
    private DeliveryPlatformApplication() {
    }

    /**
     * 程序入口。
     *
     * @param args 启动参数；包含 {@code --demo} 时执行自动演示，否则进入交互式控制台
     */
    public static void main(String[] args) {
        boolean demoMode = false;
        for (String arg : args) {
            if ("--demo".equalsIgnoreCase(arg) || "-d".equalsIgnoreCase(arg)) {
                demoMode = true;
            }
        }

        System.out.println("正在装配外卖订单履约平台……");
        PlatformBootstrap.Platform platform = PlatformBootstrap.assemble();

        if (demoMode) {
            DemoRunner.run(platform);
            return;
        }

        ConsoleUI console = new ConsoleUI(platform.facade(), platform.reportService());
        console.printPlatformOverview();
        console.runInteractive();
    }
}
