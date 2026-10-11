# 设计模式实例库（patterns 模块）

本模块是**设计模式实例库**：每个设计模式一个独立子包、一个独立可运行的 `*Demo` 类，
不依赖 `delivery-platform`，可以单独编译、单独运行、单独阅读。

## 目录规划（阶段二填充）

### 创建型 `com.example.patterns.creational`

| 子包 | 模式 | Demo 入口 | 在业务系统中的落点 |
| --- | --- | --- | --- |
| `singleton` | 单例 | `SingletonDemo` | 平台全局配置 `PlatformConfig`、订单号生成器 `IdGenerator` |
| `factorymethod` | 工厂方法 | `FactoryMethodDemo` | 按支付渠道创建支付网关、按类型创建通知渠道 |
| `abstractfactory` | 抽象工厂 | `AbstractFactoryDemo` | 创建「用户 + 商家 + 菜品」整族演示数据，保证同一套费率体系自洽 |
| `builder` | 建造者 | `BuilderDemo` | `Order.OrderBuilder` 组装订单、菜品建造者 |
| `prototype` | 原型 | `PrototypeDemo` | 「再来一单」`Order#copyAsDraft`、菜单版本克隆 |

### 结构型 `com.example.patterns.structural`

| 子包 | 模式 | Demo 入口 | 在业务系统中的落点 |
| --- | --- | --- | --- |
| `adapter` | 适配器 | `AdapterDemo` | `PaymentGateway` 统一微信/支付宝/银行卡/余额 |
| `composite` | 组合 | `CompositeDemo` | 套餐（组合节点）与单品（叶子）统一计价 |
| `decorator` | 装饰器 | `DecoratorDemo` | 打包费、小费、雨天补贴层层叠加 |
| `facade` | 外观 | `FacadeDemo` | `OrderFacade` 一键下单，隐藏校验/计价/支付/派单子系统 |

### 行为型 `com.example.patterns.behavioral`

| 子包 | 模式 | Demo 入口 | 在业务系统中的落点 |
| --- | --- | --- | --- |
| `chain` | 职责链 | `ChainOfResponsibilityDemo` | 下单校验链：非空 → 起送价 → 库存 → 地址 → 风控 → 限购 |
| `command` | 命令 | `CommandDemo` | 派单/改派/撤单/取消封装为命令，支持撤销与重做 |
| `interpreter` | 解释器 | `InterpreterDemo` | 优惠券规则 `满30减5`、`9折`、`且/或` 解析求值 |
| `mediator` | 中介者 | `MediatorDemo` | `DispatchCenter` 协调商家、骑手、订单三方 |
| `memento` | 备忘录 | `MementoDemo` | 订单快照/回滚、订单存档 |
| `observer` | 观察者 | `ObserverDemo` | 订单事件通知用户、商家、骑手、报表 |
| `state` | 状态 | `StateDemo` | 订单状态机与状态行为 |
| `strategy` | 策略 | `StrategyDemo` | 计价策略、骑手调度策略 |
| `template` | 模板方法 | `TemplateMethodDemo` | 订单归档流程、报表生成流程 |
| `visitor` | 访问者 | `VisitorDemo` | 统计报表、数据导出、风控审计 |

## 运行方式

```bat
rem 先构建
mvnw.cmd -q package

rem 统一菜单（推荐课堂演示）
java -cp patterns\target\classes com.example.patterns.PatternShowcase

rem 单个模式
java -cp patterns\target\classes com.example.patterns.creational.singleton.SingletonDemo
```

## 编写约定

1. 每个模式放在**独立子包**中，避免类名冲突（19 个 Demo 都在同一个大包里会很难看）。
2. 每个 `*Demo` 类都提供 `public static void main(String[] args)`，可以直接运行。
3. 类注释统一写明：**用了哪个模式 → 为什么用 → 不用会怎样**，便于课程报告引用。
4. Demo 中打印的分节标题、参与角色、调用顺序要与 PlantUML 图一致，方便答辩时图文对照。
