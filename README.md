# 基于设计模式实例库与模块化外卖订单履约平台

> 课程大作业 · Java 17 + Maven 多模块 · 纯控制台版
>
> 17 个设计模式独立 Demo ＋ 1 套完整业务闭环的订单履约平台
>
> **设计模式覆盖**：课程要求至少 12 种，本项目实现 **19 种全覆盖**（GoF 全谱）。
> 实例库中有 17 个可独立运行的 `*Demo` 类（编号 01–17），业务系统中另有 6 处模式落地；
> 每个模式在业务系统中的真实应用场景见第五节对照表。

---

## 一、快速开始（3 条命令）

```bat
cd "D:\Food_Delivery Order Fulfillment Platform"

rem 1) 构建（编译 + 打包 + 跑全部测试）
mvnw.cmd clean package

rem 2) 一键跑完整业务闭环演示（推荐用于答辩/验收，无需手工输入）
java -cp delivery-platform\target\classes com.example.delivery.app.DeliveryPlatformApplication --demo

rem 3) 启动交互式控制台（自己下单操作）
java -cp delivery-platform\target\classes com.example.delivery.app.DeliveryPlatformApplication
```

**设计模式实例库入口：**

```bat
rem 统一菜单（17 个 Demo 任选）
java -cp patterns\target\classes com.example.patterns.PatternShowcase

rem 直接跑指定编号的 Demo（01~17，可不补零）
java -cp patterns\target\classes com.example.patterns.PatternShowcase 10

rem 依次跑完全部 17 个 Demo
java -cp patterns\target\classes com.example.patterns.PatternShowcase all

rem 单独运行某个 Demo（每个 Demo 都有自己的 main）
java -cp patterns\target\classes com.example.patterns.creational.singleton.SingletonDemo
```

> **中文乱码处理**：Windows 控制台默认代码页为 GBK，若显示乱码，先执行 `chcp 65001`
> 或在运行前设置 `set JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8`。源码与输出本身都是合法 UTF-8。

### 没有全局 `mvn` 怎么办？

不需要安装。`mvnw.cmd` 会自动使用 `%USERPROFILE%\.m2\wrapper\dists` 里缓存的 Maven 发行版
（本机为 3.9.16，实测可用）。如果你看到 `'rem' 不是内部或外部命令` / `'tlocal'` / `'AVEN_HOME*'`
这类报错，那是 **`mvnw.cmd` 的编码或换行被改坏了**（必须保持纯 ASCII + CRLF），
详见第三节的说明。

万一缓存里确实没有 Maven，也可以完全不用 Maven 构建（只要装了 JDK 17）：

```bat
rem 用 -sourcepath 让 javac 自动找到被依赖的类（只需指定入口类，不必罗列全部文件）
javac -encoding UTF-8 -d delivery-platform\target\classes-raw ^
      -sourcepath delivery-platform\src\main\java ^
      delivery-platform\src\main\java\com\example\delivery\app\DeliveryPlatformApplication.java

rem 然后照常运行（实测：编译出 111 个 class，演示 170 行输出，退出码 0）
java -cp delivery-platform\target\classes-raw com.example.delivery.app.DeliveryPlatformApplication --demo
```

> 注意两点：① `javac` 需要 `JAVA_HOME` 已配置；② 不要用 `dir /s /b > sources.txt`
> 再 `javac @sources.txt` 的写法——本工程路径含空格，`@argfile` 会把反斜杠当转义符而报
> 「无效的标记」。用上面的 `-sourcepath` 方式最省事。

---

## 二、启动方式与入口一览

| 序 | 入口类（全限定名） | 作用 | 命令 |
| --- | --- | --- | --- |
| 1 | `com.example.delivery.app.DeliveryPlatformApplication` | **业务系统主入口**（交互式控制台） | `java -cp delivery-platform\target\classes com.example.delivery.app.DeliveryPlatformApplication` |
| 2 | 同上 + `--demo` | **业务系统主入口**（自动跑完整闭环 + 异常场景） | `... DeliveryPlatformApplication --demo` |
| 3 | `com.example.patterns.PatternShowcase` | **设计模式实例库统一菜单** | `java -cp patterns\target\classes com.example.patterns.PatternShowcase` |
| 4 | `com.example.patterns.*.*Demo` | 17 个设计模式 Demo（每个都有 `main`） | 见第一节示例 |
| 5 | `com.example.delivery.bootstrap.OrderDomainGuide` | 领域模型冒烟演示（阶段一交付物） | `java -cp delivery-platform\target\classes com.example.delivery.bootstrap.OrderDomainGuide` |

> 本项目是**纯 Java 控制台应用**，不启动 Web 服务器，因此没有 HTTP 地址；
> 所有入口都是「类 + 命令行」，如上表所示。构建产物为两个 jar：
> `patterns\target\patterns-1.0.0.jar` 与 `delivery-platform\target\delivery-platform-1.0.0.jar`。

### 交互式控制台菜单

```text
 1. 下单          （用户下单 → 订单构建 → 校验链 → 计价策略 → 优惠券解释）
 2. 查询订单      （按订单号 / 全部 / 按状态）
 3. 支付订单      （支付适配：余额 / 微信 / 支付宝 / 银行卡）
 4. 派单          （派单中介 → 骑手调度策略，可切换策略）
 5. 配送推进      （骑手取餐 / 确认送达）
 6. 取消订单      （状态守卫 + 库存回滚 + 事件通知）
 7. 撤销上一步    （备忘录模式回滚）
 8. 统计报表与存档（访问者模式报表 + 模板方法归档）
 9. 查看商家菜单与库存
10. 查看骑手池
11. 查看优惠券规则（解释器可用的规则）
12. 查看最近一次计价/优惠/事件说明
 0. 退出
```

---

## 三、环境要求

| 项 | 要求 | 本机实测 |
| --- | --- | --- |
| JDK | 17+ | Temurin 17.0.18 ✅ |
| Maven | 3.6+ | 无全局 `mvn`，但 `.m2` 已缓存 3.9.16，`mvnw.cmd` 会自动找到 ✅ |
| 依赖 | 仅 JUnit 5（测试作用域） | 运行期零第三方依赖 ✅ |

### ⚠️ `mvnw.cmd` 必须是「纯 ASCII + CRLF」

如果你刚才执行 `.\mvnw.cmd` 报出 **`'rem' 不是内部或外部命令`、`'tlocal'`、`'AVEN_HOME*'`** 这类错误：
那不是 Maven 的问题，而是**批处理文件的编码/换行被改坏了**。原因有两条：

1. **编码**：Windows 的 `cmd.exe` 按系统 ANSI 代码页（中文 Windows 是 GBK）解码 `.cmd` 文件。
   若文件以 UTF-8 保存且含中文注释，`rem` 行会被误解码并"吞掉"下一行命令，
   于是 `setlocal` 被解析成 `tlocal`、`%MAVEN_HOME%` 被解析成 `AVEN_HOME*`。
2. **换行**：必须是 CRLF。LF-only 时 `rem` 行不结束，同样会吞掉后续命令。

**对策**：`mvnw.cmd` 已改为全英文注释 + 纯 ASCII + CRLF。
请**不要**给它加中文注释，也不要用会改成 LF 的编辑器保存。
若在 Git 下工作，建议加 `.gitattributes` 保证不转换：

```gitattributes
*.cmd text eol=crlf
```

验证脚本是否完好（应输出 0 和 True）：

```powershell
$b = [System.IO.File]::ReadAllBytes('mvnw.cmd')
"非 ASCII 字节: " + ($b | Where-Object { $_ -gt 127 }).Count
"含 CRLF: " + ([System.Text.Encoding]::ASCII.GetString($b).Contains("`r`n"))
```

### 关于「系统里没有 mvn 命令」

不需要全局安装。`mvnw.cmd` 会按顺序查找：

1. `%MAVEN_HOME%\bin\mvn.cmd`
2. `%M2_HOME%\bin\mvn.cmd`
3. **Maven Wrapper 缓存** `%USERPROFILE%\.m2\wrapper\dists\apache-maven-*\<hash>\bin\mvn.cmd`
   （本机命中这一条：`apache-maven-3.9.16`）
4. `PATH` 中的 `mvn`

实测（本机）：

```text
> mvnw.cmd --version
Apache Maven 3.9.16
Maven home: C:\Users\asus\.m2\wrapper\dists\apache-maven-3.9.16\0daed3be...\bin\..
Java version: 17.0.18, vendor: Eclipse Adoptium
```

> 若第 3 条也没有命中，说明 `.m2` 里没有缓存发行版，此时请安装 Maven 3.9+ 并设置 `MAVEN_HOME`，
> 或直接用 IDE 内置 Maven 导入本工程，也可以按第一节最后的办法用 `javac` 直接编译。

---

## 四、工程结构

```text
food-delivery-platform/                 父工程（聚合 + 依赖/插件版本管理）
├── pom.xml
├── mvnw.cmd                            轻量 Maven 启动脚本（无需联网下载 wrapper）
├── README.md
├── docs/uml/                           PlantUML 图（3 张）
│   ├── 01-核心类图.puml
│   ├── 02-下单时序图.puml
│   └── 03-派单时序图.puml
├── patterns/                           设计模式实例库
│   ├── README.md
│   └── src/main/java/com/example/patterns/
│       ├── PatternDemo.java            统一契约（name/code/category/description/run）
│       ├── PatternShowcase.java        统一菜单入口（注册表 + 简单工厂）
│       ├── creational/                 01 单例 / 02 工厂方法 / 03 抽象工厂
│       ├── structural/                 04 适配器 / 05 组合 / 06 装饰器 / 07 外观
│       └── behavioral/                 08 职责链 / 09 命令 / 10 解释器 / 11 中介者 /
│                                       12 备忘录 / 13 观察者 / 14 状态 / 15 策略 /
│                                       16 模板方法 / 17 访问者
└── delivery-platform/                  综合业务系统
    └── src/
        ├── main/java/com/example/delivery/
        │   ├── app/                    DeliveryPlatformApplication（主入口）
        │   ├── bootstrap/              PlatformBootstrap（组合根）、DemoRunner（端到端脚本）、
        │   │                           OrderDomainGuide（领域冒烟演示）
        │   ├── ui/                     ConsoleUI（交互式菜单）
        │   ├── domain/                 领域层（不依赖任何设计模式实现）
        │   │   ├── order/              Order / OrderStatus / OrderItem / OrderState /
        │   │   │                       OrderSnapshot / OrderVisitor / OrderRepository /
        │   │   │                       OrderService / 2 个业务异常
        │   │   ├── user/               User（含会员等级）
        │   │   ├── merchant/           Merchant / Dish
        │   │   ├── rider/              Rider（含骑手状态）
        │   │   ├── coupon/             Coupon（规则表达式）
        │   │   ├── payment/            Payment / PaymentMethod / PaymentStatus
        │   │   ├── pricing/            PricingStrategy / PricingContext（策略模式）
        │   │   └── support/            CouponEvaluator / PaymentGateway / DispatchStrategy /
        │   │                           DispatchStrategies / OrderStore / OrderEvent /
        │   │                           OrderEventListener / DomainEventPublisher
        │   ├── application/            应用层
        │   │   ├── OrderFacade         外观模式：业务闭环编排中心
        │   │   ├── DispatchCenter      中介者模式：派单中心
        │   │   ├── OrderMemoManager    备忘录模式：Caretaker
        │   │   ├── InventoryService    单例：库存（预占/回滚）
        │   │   ├── OrderValidationContext / DispatchOutcome
        │   │   ├── validator/          职责链：6 个校验器 + 链装配工厂
        │   │   └── report/             访问者模式：ReportVisitor / StatisticsReport / ReportService
        │   └── infrastructure/         基础设施层
        │       ├── store/              InMemoryOrderStore / InMemoryActorStore
        │       ├── coupon/             CouponRuleInterpreter / CouponRuleParser / CouponExpression
        │       ├── payment/            4 个支付适配器 + 注册表 + 第三方 SDK 模拟
        │       └── event/              OrderEventListeners（5 个观察者）
        └── test/java/com/example/delivery/   JUnit 5 测试
```

---

## 五、设计模式清单（19 个模式全部落地）

### 5.1 实例库（patterns 模块，每个都有独立 main）

| 编号 | 模式 | Demo 类 | 演示内容 |
| --- | --- | --- | --- |
| 01 | 单例 | `SingletonDemo` | 双检锁 / 静态内部类 / 枚举三种写法；1600 个订单号零重复；8 线程同实例 |
| 02 | 工厂方法 | `FactoryMethodDemo` | 支付网关工厂族；对比静态工厂（简单工厂） |
| 03 | 抽象工厂 | `AbstractFactoryDemo` | 演示数据族 vs 压测数据族；论证跨族混搭会导致数据不自洽 |
| 04 | 适配器 | `AdapterDemo` | 微信（分/报文）/ 支付宝（元/字符串）/ 银行（错误码）统一成 `pay()` |
| 05 | 组合 | `CompositeDemo` | 菜单树 = 分类 → 套餐 → 单品，统一 `price()` 递归求和 |
| 06 | 装饰器 | `DecoratorDemo` | 打包费 / 小费 / 雨天补贴自由叠加，对比继承爆炸 |
| 07 | 外观 | `FacadeDemo` | 不用外观需手调 5 个子系统 12 个方法 vs 一行 `placeOrder()` |
| 08 | 职责链 | `ChainOfResponsibilityDemo` | 非空→起送价→库存→地址→频控，打印逐环通过/否决轨迹 |
| 09 | 命令 | `CommandDemo` | 派单/撤单/改派/取消 + 撤销重做 + 宏命令 + 命令时间线 |
| 10 | 解释器 | `InterpreterDemo` | 手写递归下降解析券规则，打印 AST，非法规则给可读错误 |
| 11 | 中介者 | `MediatorDemo` | 订单/商家/骑手只与派单中心通信，打印转发链路 |
| 12 | 备忘录 | `MementoDemo` | 不可变备忘录 + 负责人 Deque 栈，多级 undo/undoAll |
| 13 | 观察者 | `ObserverDemo` | 手写事件总线，5 个订阅者，动态退订 |
| 14 | 状态 | `StateDemo` | 7 个状态类各自覆盖允许的操作，打印迁移矩阵 |
| 15 | 策略 | `StrategyDemo` | 骑手调度 3 策略 + 配送费 3 策略，运行期切换 |
| 16 | 模板方法 | `TemplateMethodDemo` | `final archive()` 锁死骨架，CSV/JSON/汇总三种实现 |
| 17 | 访问者 | `VisitorDemo` | 3 类订单元素 × 4 个访问者（营业额/销量/CSV/风控），双分派 |

### 5.2 业务系统（delivery-platform 模块，12 个环节闭环）

| # | 履约环节 | 落地位置 | 设计模式 | 为什么在这里用 |
| --- | --- | --- | --- | --- |
| 1 | 用户下单 | `OrderFacade#create` | 外观 + 抽象工厂 | 用户只调一个方法；数据由工厂族成套产出保证自洽 |
| 2 | 订单构建 | `Order.OrderBuilder` | 建造者 | 字段多且可选，链式组装 + `build()` 统一校验不变量 |
| 3 | 校验链 | `application/validator/*` | 职责链 | 6 环一票否决，先廉价后昂贵，可增删环 |
| 4 | 计价策略 | `PricingContext` + `PricingStrategy` | 策略 | 基础/会员免运费/雨天/高峰，运行期自动或手动切换 |
| 5 | 优惠券解释 | `CouponRuleInterpreter` + `CouponExpression` | 解释器 | 规则字符串 → AST → 抵扣额，运营改规则不改代码 |
| 6 | 支付适配 | `infrastructure/payment/*Adapter` | 适配器 | 4 个渠道异构接口统一成 `PaymentGateway#pay` |
| 7 | 状态流转 | `OrderStatus` 矩阵 + `OrderState` 状态类 | 状态 | 非法流转在状态对象处被拒绝，消除散落的 `switch` |
| 8 | 事件通知 | `DomainEventPublisher` + 5 个监听器 | 观察者 | 用户/商家/骑手/报表/审计解耦订阅 |
| 9 | 派单中介 | `DispatchCenter` | 中介者 | 订单、商家、骑手星形拓扑，互不引用 |
| 10 | 骑手调度 | `DispatchStrategies` | 策略 + 命令 | 最近/最少负载/最高评分，支持多策略回退降级 |
| 11 | 订单存档 | `OrderStore#archiveBefore` + `OrderSnapshot` | 模板方法 + 原型 + 备忘录 | 流程骨架固定、介质可换；存档用快照不污染原单 |
| 12 | 统计报表 | `ReportVisitor` + `StatisticsReport` | 访问者 | 营业额/客单价/销量榜/骑手榜外置，订单类零改动 |

**另外落在业务系统中的模式：**单例（仓储、库存、支付注册表）、建造者（门面装配）、
原型（`copyAsDraft` 再来一单）、组合（菜品/套餐统一计价）、装饰器（价格叠加）、
工厂方法（支付网关工厂族）。

---

## 六、业务闭环演示（`--demo` 输出摘要）

`--demo` 会按 13 个步骤跑完整闭环，并**主动演示 5 类异常场景**：

| 步骤 | 内容 | 验证的模式 |
| --- | --- | --- |
| 0 | 平台装配：5 个事件订阅者、4 个支付渠道、7 个菜品库存 | 抽象工厂 / 单例 / 建造者 |
| 1 | 创建草稿订单并加菜 | 建造者 |
| 2 | **只加一份可乐 → 被起送价拦下** → 打印完整校验轨迹 → 补菜后重新提交 | 职责链 |
| 3 | 计价 + 优惠券解释（打印 AST 与求值上下文） | 策略 / 解释器 |
| 4 | **余额不足支付失败**（订单保持待支付）→ 换支付宝成功 → 第一单用微信成功 | 适配器 / 状态 |
| 5 | 派单：打印候选骑手、策略选择理由、负载变化 | 中介者 / 策略 / 观察者 |
| 6 | 取餐 → **撤销回退到已接单** → 重新取餐 → 送达并释放骑手负载 | 状态 / 备忘录 |
| 7 | **已完成订单再取消被拦截** | 状态 |
| 8 | 第二单：支付宝支付 → **全部骑手置为休息导致派单失败** → 恢复运力并切换策略重试成功 | 中介者 / 策略 |
| 9 | 第三单：8 条规则对比求值 → 支付 → **取消并回滚库存** | 解释器 / 备忘录 / 观察者 |
| 10 | 原型模式「再来一单」 | 原型 |
| 11 | 归档 4 张终态订单并打印存档快照 | 模板方法 |
| 12 | 统计报表（营业额/完成率/销量榜/骑手榜）+ 支付对账 + 事件统计 + 短信/审计条数 | 访问者 |
| 13 | 打印状态迁移矩阵总览 | 状态 |

实测要点（一次真实运行）：

```text
  用余额仅 50.00 元的用户 U-1002 下一笔 78.40 元的订单
    [余额支付] 扣款失败：余额 50.00 元 < 应付 78.40 元
  [失败] 支付失败（订单仍停留在待支付，可换渠道重试）：支付失败[余额支付]：余额不足
  该订单状态仍为：待支付
  [成功] 换支付宝支付成功，状态=已支付，流水号=ALI563942252
  ...
  取消前库存：香辣鸡腿堡=48  黄金薯条=99  冰镇可乐=199  藤椒鸡腿饭=28 ...
  取消后库存（已回滚）：香辣鸡腿堡=48  黄金薯条=99  冰镇可乐=199  藤椒鸡腿饭=30 ...
  营业额（已完成）：114.20 元    客单价：57.10 元
  支付流水对账：共 5 笔（成功 4 笔 / 失败 1 笔），成功金额合计 233.20 元
```

---

## 七、PlantUML 图

`docs/uml/` 下三张图（UTF-8 纯文本，可用 IDEA PlantUML 插件、VS Code PlantUML 插件或
<https://www.plantuml.com/plantuml/uml/> 在线渲染）：

| 文件 | 内容 |
| --- | --- |
| `01-核心类图.puml` | 领域模型 + 全部设计模式角色（用 note 标注每个类在模式中的角色） |
| `02-下单时序图.puml` | 下单：构建 → 校验链 → 计价 → 解释器 → 库存预占 → 状态流转 → 事件通知 |
| `03-派单时序图.puml` | 派单与配送：支付 → 中介者 → 策略回退 → 双向更新 → 取餐/送达 → 撤销 → 报表 |

---

## 八、测试

```bat
mvnw.cmd test                          rem 跑全部测试（91 个用例）
mvnw.cmd -pl delivery-platform test    rem 只跑业务模块
```

**实测结果：**

```text
[INFO] Tests run: 91, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

测试清单：

| 测试类 | 用例数 | 覆盖内容 |
| --- | --- | --- |
| `OrderTest`（领域聚合） | 17 | 下单与金额、状态流转、取消、备忘录回滚、原型复制、访问者统计 |
| `OrderFacadeSubmitTest` | 约 20 | 订单构建、6 环职责链（含轨迹与插队顺序）、计价策略、优惠券解释、库存预占、事件通知 |
| `OrderFacadeCancelTest` | 约 17 | 可取消性矩阵、配送中/终态拒绝取消、多菜品库存回滚、取消事件、撤销取消 |
| `OrderStateTransitionTest` | 约 14 | 正向链路每步状态、非法流转被拒、状态对象白名单、迁移矩阵、备忘录 |
| `DispatchAndPaymentTest` | 约 12 | 未支付不可派单、派单成功与骑手负载、无运力失败、策略切换、送达释放负载、四渠道支付、余额不足 |
| `CouponInterpreterTest` | 约 17 | 满减/折扣/且或/括号优先级、抵扣封顶、非法规则报错 |
| `PricingContextTest` | 7 | 基础阶梯价、会员免运费、雨天 1.5 倍、高峰 +2、策略优先级、运行期手动切换 |

另外，`patterns` 模块的每个 Demo 都是可运行的「活文档」，
`java -cp patterns\target\classes com.example.patterns.PatternShowcase all` 相当于一次全量回归
（实测 17 个 Demo 全部执行成功、0 失败）。

**不依赖 Maven 的测试运行器**：`patterns/src/test/java/com/example/delivery/testkit/ConsoleTestRunner.java`
直接调用 JUnit Platform Launcher，扫描包并打印汇总，退出码 0/1 便于脚本与 CI 判断
（已在父 POM 的 surefire 中以 `<exclude>` 排除，不会被当作测试类）。

---

## 九、编码约定

1. 全部源码 UTF-8，中文注释；POM 固定 `project.build.sourceEncoding=UTF-8`。
2. 每个与设计模式相关的类，类注释都包含「**设计模式：X**」+「**为什么用**」+「不用会怎样」。
3. 金额一律 `BigDecimal` + `setScale(2, RoundingMode.HALF_UP)`，禁止 `double`。
4. 时间统一 `java.time.Instant`。
5. 领域层 `domain` 不依赖设计模式实现与基础设施；模式实现放在 `application` / `infrastructure` / `patterns`。
6. 业务异常与程序异常分开：状态非法、校验不过、余额不足等都是**业务异常**，不打印堆栈。

---

## 十、已知取舍（写在报告里可以作为「设计权衡」一节）

1. **解释器 vs 脚本引擎**：本项目手写递归下降解析器以展示解释器模式全部细节；
   生产环境更可能选 Groovy/QLExpress，代价是模式本身被第三方库隐藏。
2. **访问者的代价**：新增一个订单类型需要改动所有 Visitor；本项目的订单类型稳定，故收益大于成本。
3. **快照必须覆盖全部可变状态**：早期实现只快照业务子集，导致撤销后 `cancelReason` 残留，
   测试暴露后改为全量快照——这是「备忘录模式语义完整性」的真实教训。
4. **库存预占时机**：选择「提交订单即预占」而非「支付后扣减」，避免超卖；
   代价是未支付订单会占用库存，真实系统需要超时释放任务。
5. **控制台视图层**：把 `System.out` 与输入解析隔离在 `ui` 包，使业务逻辑可被无界面单元测试覆盖。
6. **策略的「显式设置 vs 自动选择」**：`PricingContext` 早期实现每次计价都重跑自动选择，
   结果运营手工设置的策略在第一次计价后就被悄悄覆盖——「运行期可切换」形同虚设。
   现在用 `strategyOverridden` 标记区分：显式设置优先，`useAutoSelection()` 交还选择权。
   这是「策略模式由谁选择策略」这一常被忽略的设计点。
7. **职责链 `setNext` 的返回语义**：返回 `next` 会让 `a.setNext(b).setNext(c)` 的语义依赖返回值，
   极易误用（曾实测导致链上只剩 2 个处理者）；改为返回链头后语义唯一。
8. **校验轨迹必须可收集**：职责链一票否决会抛异常中断流程，
   若不在 `finally` 里汇总轨迹，控制台就无法展示「前面几环通过了、卡在第几环」。
