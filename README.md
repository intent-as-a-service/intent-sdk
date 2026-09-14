**English** · [中文](#chinese)

<a name="english"></a>
# intent-sdk — Intent as a Service

> **AI integration without the chat box.** A business page shows a row of *intent buttons*; one
> click runs the intent and renders a structured result card in place. The AI runs **in-process, as
> a native SDK, inside your application** — permissions, transactions and data scope stay exactly as
> the host defines them. No separate account system, no cross-domain calls, no data leaving your
> boundary.

[![build](https://github.com/intent-as-a-service/intent-sdk/actions/workflows/ci.yml/badge.svg)](https://github.com/intent-as-a-service/intent-sdk/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](./LICENSE)

---

## The problem it solves

Traditional "AI integration" drops an LLM into a chat box: users have to work out what to ask, and
then carry the answer back into the business flow by hand. The result is **AI and the business
running as two separate worlds** — you ask in the chat box, then click in the business system.

Intent as a Service flips the direction: **AI is no longer an entry point, it is a capability.**

| Chat box | Intent as a Service |
|---|---|
| Entry point: one global chat box | Entry point: intent buttons on the page, loaded per page |
| The user phrases the request | The system declares the intent (id / params / tool allow-list / output contract) |
| Output is a paragraph of prose | Output is a **standard result envelope** (`title` / `summary` / `blocks` / `followups` / `nextIntents`) the UI renders directly |
| The answer has to be carried back by hand | "Next step" chips in the card are clickable (re-run with extra input / AI-recommended intent / host todo) |
| Permissions, transactions and data scope have to be rebuilt | Tools call host services **in-process**, so permissions and transactions follow the call stack |
| Every AI capability needs a release | Intent specs and fact rules are YAML — edited in the admin UI, **effective on save** |

In one sentence: **move "what can be asked, how it should be asked, and what the answer looks like"
out of the user's head and into the system.**

## Catalog enrichment: from a list of capabilities to a list of todos

By default the intent menu looks the same to everyone ("Account risk scan", "Login anomaly
analysis"). Catalog enrichment attaches **business facts** to those entries:

```
One AI icon, but it opens showing:
  📌 Todos · Account risk scan          expand
     · "Zhang San" inactive for 91 days    zhangsan · R&D       · click to scan
     · "Li Si" inactive for 63 days        lisi     · Marketing · click to scan
  Badge: Login anomaly analysis  [17 failed logins in the last 24 hours]
```

Facts come from the business module's `IntentFactProvider` (**data only**); conditions and wording
live in YAML. Change a threshold, a sentence, or the pages an intent appears on by editing one YAML
file — no Java change, no release. A malformed rule **fails at startup** rather than silently
producing no todos.

---

## Architecture

![Architecture](./docs/diagrams/01-总体架构图.png)

**Three boundaries decide whether this survives in the long run:**

1. **SDK decoupled from the host framework** — `intent-sdk-core` / `intent-sdk-host` carry **no
   Spring dependency**; identity, permissions and the context bridge are SPI implementations the
   host provides. Changing hosts means rewriting three classes.
2. **SDK decoupled from the AI engine** — the tool contract is the SDK's own `IntentTool`;
   pi-ai / pi-agent appear in **exactly one module (`intent-sdk-pi`)**. Swap inference engines by
   swapping that module. **Pure-process scenarios (skill / flow executors) need no pi at all — and
   therefore no LLM.**
3. **Platform decoupled from business** — business modules only declare things (tool beans + YAML)
   and touch no platform code. Delete a business module and the framework still runs, just without
   those intents.

## Modules

| Module | Depends on | Purpose |
|---|---|---|
| `intent-protocol` | Jackson only | Protocol DTOs: `IntentSpec` / `IntentRequest` / `IntentResult` / `CatalogResponse`… local and remote results share one shape |
| `intent-sdk-core` | Jackson only — **no Spring, no pi** | Execution engine core: spec loading and validation, orchestration, catalog assembly, tool contract |
| `intent-sdk-host` | core | Host SPI (identity / permissions / context / repositories) + a **declarative fact-rule engine** |
| `intent-sdk-pi` | core + **pi-ai / pi-agent** | Executor implementations: the `builtin-agent` reasoning loop, deterministic `skill` steps, `flow` orchestration |
| `intent-sdk-gateway` | core | Cross-system intent gateway client (**optional**: leave it out and everything stays local) |
| `intent-spring-boot-starter` | host + Spring Boot | Auto-configuration: default SPI implementations plus rule-engine wiring |

## Quick start

> **Status: 0.1.0-SNAPSHOT, not published.** Artifacts are not on Maven Central yet — install from
> source into your local repository first:
>
> ```bash
> # 1) Install the inference engine first (only needed for agent-type executors;
> #    skip it entirely for pure skill/flow scenarios)
> git clone <the pi-java repository> && cd pi-java && mvn install -DskipTests
>
> # 2) Then install the intent SDK
> cd intent-sdk && mvn install -DskipTests
> ```
>
> This section becomes a plain dependency once publishing lands.

### 1. Add the dependency

```xml
<dependency>
    <groupId>io.github.intent-as-a-service</groupId>
    <artifactId>intent-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

> **Pure-process use (no LLM needed)**: exclude `intent-sdk-pi` and the `skill` / `flow` executors
> keep working. You then do not need pi-ai / pi-agent at all — the core modules depend on Jackson and
> nothing else.

### 2. Wrap host capabilities as tools (Java, 1–3 per intent)

```java
@Component
public class OrderQueryTool implements IntentTool {

    private final OrderService orderService;   // inject the host service directly

    @Override public String name() { return "order_query"; }

    @Override public String description() {
        // This text goes straight into the model prompt and decides whether the model
        // uses the tool correctly — be explicit about which questions it can answer.
        return "Query orders by customer and status; returns order no., amount, status and creation time.";
    }

    @Override public Map<String, Object> parameters() {
        return Schemas.object(Map.of(
                "customerId", Schemas.string("Customer id"),
                "limit",      Schemas.number("Max rows to return")
        ), List.of());
    }

    @Override public IntentToolResult execute(String id, Map<String, Object> args, IntentToolContext ctx) {
        // In-process call: permissions and transactions ride the host call stack.
        return IntentToolResult.data(orderService.query(args));
    }
}
```

### 3. Declare an intent (YAML, one file per intent)

```yaml
# src/main/resources/intent/order.overdue.scan.yaml
id: order.overdue.scan            # system.domain.action
name: Overdue order diagnosis
description: Review overdue orders, grade them by customer and amount, rank collection priority
scope: local
pages: [order/list, ""]           # only on these pages; "" = the intent centre
promptTemplate: |
  You are an order risk analyst. Call order_query first to get the overdue-order facts;
  never invent data. Lead with the conclusion; use a table for the top overdue orders and
  badges for risk levels; every number must come from the tool, otherwise write "insufficient data".
paramsSchema:
  type: object
  properties:
    customerId: { type: string, title: Customer id }
  required: []
context:
  - { key: customerId, title: Current customer, required: false }   # auto-filled from the page
tools: [order_query]              # tool allow-list (omit = all tools; not recommended)
policy:
  roles: ["*"]
  timeoutSeconds: 180
```

**All five elements must be present before an intent can be published, and they are validated hard
at startup** — an unknown intent, a parameter outside the params schema, or a template referencing an
unknown field stops the service from booting.

### 4. Mount the front end (framework-agnostic vanilla JS)

```ts
IntentUI.configure({ apiPrefix: '/api/intent', authHeaders: () => ({ Authorization: 'Bearer ' + token }) });
IntentUI.mountFloating({ getPage: () => route.path.slice(1), getContext: () => pageContext() });
```

Batteries included: floating entry point / intent menu / slot form / todo groups / result cards /
execution trace / history / feedback. Works with Vue 2, Vue 3, React, jQuery and server-rendered
templates alike ([intent-ui-sdk](https://github.com/intent-as-a-service/intent-ui-sdk)).

## Hosts in production

| Host | Integration | Intents shipped |
|---|---|---|
| [RuoYi-Vue-Plus](https://github.com/intent-as-a-service/RuoYi-Vue-Plus) | SDK embedded | 14 (account hygiene / permission review / login anomalies / cache diagnosis …) |
| [ruoyi-office](https://github.com/intent-as-a-service/ruoyi-office) (yudao) | SDK embedded | 40 (full CRM chain) |

## Measured numbers

| Scenario | Duration | Tokens |
|---|---|---|
| agent-type intent (reasoning loop + 11 tool calls) | 19–23 s | 7.7k–10.8k |
| **skill-type intent (pure tool steps, zero LLM)** | **13–51 ms** | **0** |

> For data-fetching intents that need no live judgement, freeze them into a skill-type executor
> profile — roughly 400× faster, zero cost, and auditable.

## Documentation

| Document | Contents |
|---|---|
| [Architecture design](./docs/架构设计方案.md) | Full design: the five elements, execution path, catalog enrichment, gateway |
| [Integration & delivery guide](./docs/集成交付指南.md) | Three steps to integrate a legacy system, effort involved, delivery SOP at scale |
| [Migration guide](./docs/迁移方案-新系统接入.md) | What changes when swapping hosts, current gaps |
| [Feature list](./docs/功能清单.md) | Implemented vs planned, item by item |
| [Deployment guide](./docs/部署运行指南.md) | Infrastructure, build, start-up, demo |
| [Field notes](./docs/意图即服务-真实案例.md) | What it took to land in a real business system |

*(The documents above are currently written in Chinese.)*

## Design trade-offs

- **Why a "context bridge" is mandatory**: the reasoning loop runs tools on a separate thread, while
  the host's login state and data scope live on the original one. Not carrying the context over does
  not raise an error — it **silently returns wrong results** (data belonging to someone else, or
  nothing at all).
- **Why hiding from the catalog is not authorization**: anyone who knows an intent id can POST to the
  execution endpoint, so permissions are re-checked independently before every execution.
- **Why specs live in a database rather than a read-only classpath**: operators need to publish,
  unpublish and re-scope intents without a release. Seeding only writes when a spec is physically
  absent, so an intent deleted in the admin UI does not come back after a restart.
- **Known limitations**: whole-card responses (no streaming), single-turn execution (no multi-turn
  conversation), gateway server side not implemented. See the
  [feature list](./docs/功能清单.md).

## License

[Apache License 2.0](./LICENSE)

---

<a name="chinese"></a>
# intent-sdk · 意图即服务（Intent as a Service）

[English](#english) · **中文**

> **去聊天框的 AI 接入方式**：业务页面放一排意图按钮，点击即执行，结果卡片内嵌返回。
> AI 能力以**原生 SDK 进程内嵌入**业务系统 —— 权限、事务、数据范围完全沿用宿主，不建独立账号体系、不跨域、数据不出域。

---

## 它解决什么问题

传统"AI 接入"是把大模型塞进一个聊天框，用户要自己想清楚该怎么问、问完还要把结论搬回业务操作里。
结果是 **AI 与业务是两张皮** —— 人在聊天框里问，手在业务系统里点。

意图即服务换了个方向：**AI 不再是入口，而是能力。**

| 传统聊天框 | 意图即服务 |
|-----------|-----------|
| 入口：一个全局聊天框 | 入口：业务页面上的意图按钮（按页面装载） |
| 用户组织语言 | 系统已声明意图（编号 / 入参 / 工具白名单 / 输出契约） |
| 输出是一段话 | 输出是**标准结果信封**（title / summary / blocks / followups / nextIntents），UI 直接渲染 |
| 结果要人工搬运 | 结果卡里的「下一步」可点击执行（补充要求重跑 / AI 推荐意图 / 宿主待办） |
| 权限事务数据范围要重做 | 工具**进程内直调**宿主 Service，权限与事务天然随调用栈 |
| 每个 AI 能力都要发版 | 意图规范与事实规则都是 YAML，后台改完**保存即生效** |

一句话：**把"能问什么、该怎么问、结果长什么样"从用户脑子里挪进系统里。**

## 目录增强：从"能力列表"到"待办列表"

默认形态下意图入口对每个人都一样（「账号风险体检」「登录异常分析」）。目录增强让入口带上**业务事实**：

```
同一颗 AI 图标，打开就是：
  📌 待办 · 账号风险体检      展开
     · 账号「张三」已 91 天未登录      zhangsan · 研发部 · 点一下做账号体检
     · 账号「李四」已 63 天未登录      lisi · 市场部 · 点一下做账号体检
  徽标：登录异常分析  [近 24 小时 17 次登录失败]
```

事实由业务模块的 `IntentFactProvider` 供给（**只取数**），条件与文案写在 YAML 里 ——
改阈值、改文案、改挂载页面只动一份 YAML，不用改 Java、不用发版。规则写错会在**启动时直接失败**，不会静默不出待办。

---

## 架构

![总体架构](./docs/diagrams/01-总体架构图.png)

**三条边界，决定了这套东西能不能长期活下去：**

1. **SDK 与宿主框架解耦** —— `intent-sdk-core` / `intent-sdk-host` **无 Spring 依赖**；
   身份、权限、上下文桥全部由宿主实现 SPI 提供。换宿主只需重写三个类。
2. **SDK 与 AI 引擎解耦** —— 工具契约是 SDK 自有的 `IntentTool`；
   pi-ai / pi-agent **只出现在 `intent-sdk-pi` 一个模块里**，换推理引擎只换这个模块。
   **纯流程型场景（skill / flow 执行器）可以完全不引 pi，也就不需要任何大模型。**
3. **平台与业务解耦** —— 业务模块只写声明（工具 Bean + YAML），平台代码零改动；
   删掉业务模块，框架照常运行（只是没有那些意图）。

## 模块

| 模块 | 依赖 | 作用 |
|------|------|------|
| `intent-protocol` | 仅 Jackson | 协议 DTO：`IntentSpec` / `IntentRequest` / `IntentResult` / `CatalogResponse`…… 本地与远程返回同构 |
| `intent-sdk-core` | 仅 Jackson，**无 Spring、无 pi** | 执行引擎内核：规范加载校验、编排、目录装配、工具契约 |
| `intent-sdk-host` | core | 宿主 SPI（身份/权限/上下文/仓储）+ **声明式事实规则引擎** |
| `intent-sdk-pi` | core + **pi-ai / pi-agent** | 执行器实现：`builtin-agent` 推理循环、`skill` 确定性步骤、`flow` 流程编排 |
| `intent-sdk-gateway` | core | 跨系统意图网关客户端（**可组装**：不装即本地闭环） |
| `intent-spring-boot-starter` | host + Spring Boot | 自动装配：默认 SPI 实现 + 规则引擎接线，引入即用 |

## 快速开始

> **当前状态（0.1.0-SNAPSHOT，未发布）**：制品尚未上 Maven 中央仓，请先从源码安装到本地仓库：
>
> ```bash
> # 1) 先装推理引擎（只有 agent 型执行器需要；纯 skill/flow 场景可跳过）
> git clone https://github.com/<pi-java 仓库> && cd pi-java && mvn install -DskipTests
>
> # 2) 再装意图 SDK
> cd intent-sdk && mvn install -DskipTests
> ```
>
> 中央仓发布后本节会替换为直接引依赖。

### 1. 引依赖

```xml
<dependency>
    <groupId>io.github.intent-as-a-service</groupId>
    <artifactId>intent-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

> **纯流程型（不需要大模型）**：排除 `intent-sdk-pi` 即可，`skill` / `flow` 执行器照常工作。
> 这样连 pi-ai / pi-agent 都不用装 —— 核心模块的依赖清单只有 Jackson。

### 2. 把宿主能力包成工具（Java，1~3 个/意图）

```java
@Component
public class OrderQueryTool implements IntentTool {

    private final OrderService orderService;   // 直接注入宿主 Service

    @Override public String name() { return "order_query"; }

    @Override public String description() {
        // 这段文字直接进模型提示词，决定模型会不会正确使用它 —— 写清楚"能回答什么问题"
        return "按客户、状态查询订单，返回订单号、金额、状态与创建时间。";
    }

    @Override public Map<String, Object> parameters() {
        return Schemas.object(Map.of(
                "customerId", Schemas.string("客户编号"),
                "limit",      Schemas.number("返回条数上限")
        ), List.of());
    }

    @Override public IntentToolResult execute(String id, Map<String, Object> args, IntentToolContext ctx) {
        // 进程内直调：权限与事务天然沿用宿主调用栈
        return IntentToolResult.data(orderService.query(args));
    }
}
```

### 3. 声明意图（YAML，1 个/意图）

```yaml
# src/main/resources/intent/order.overdue.scan.yaml
id: order.overdue.scan            # 系统.域.动作
name: 逾期订单诊断
description: 盘点逾期订单，按客户与金额分级，给出催收优先级
scope: local
pages: [order/list, ""]           # 只在这些页面出现；"" = 意图中心
promptTemplate: |
  你是订单风控分析员。先调用 order_query 取逾期订单事实，禁止编造数据。
  结论先行；用 table 列 TOP 逾期订单；用 badges 标风险等级；
  数字必须来自工具返回，缺失就写"数据不足"。
paramsSchema:
  type: object
  properties:
    customerId: { type: string, title: 客户编号 }
  required: []
context:
  - { key: customerId, title: 当前客户, required: false }   # 页面自动补参
tools: [order_query]              # 工具白名单（不写 = 全部工具，不推荐）
policy:
  roles: ["*"]
  timeoutSeconds: 180
```

**五要素齐备才可发布，启动时强校验** —— 意图不存在、参数不在入参 Schema 内、模板引用未知字段，都会让服务直接起不来。

### 4. 前端挂载（框架无关的原生 JS）

```ts
IntentUI.configure({ apiPrefix: '/api/intent', authHeaders: () => ({ Authorization: 'Bearer ' + token }) });
IntentUI.mountFloating({ getPage: () => route.path.slice(1), getContext: () => pageContext() });
```

自带：悬浮入口 / 意图菜单 / 槽位表单 / 待办分组 / 结果卡片 / 执行轨迹 / 历史 / 反馈。
Vue2 / Vue3 / React / jQuery / 服务端模板都能用（[intent-ui-sdk](https://github.com/intent-as-a-service/intent-ui-sdk)）。

## 已落地的宿主框架

| 宿主 | 集成方式 | 现成意图 |
|------|---------|---------|
| [RuoYi-Vue-Plus](https://github.com/intent-as-a-service/RuoYi-Vue-Plus) | SDK 嵌入 | 14 个（账号治理 / 权限体检 / 登录异常 / 缓存诊断……） |
| [ruoyi-office](https://github.com/intent-as-a-service/ruoyi-office)（yudao） | SDK 嵌入 | 40 个（CRM 全链路） |

## 实测数据

| 场景 | 耗时 | token |
|------|------|-------|
| agent 型意图（推理循环 + 11 次工具调用） | 19~23s | 7.7k~10.8k |
| **skill 型意图（纯工具步骤，零大模型）** | **13~51ms** | **0** |

> 不需要临场判断的取数类意图，把它固化成 skill 型执行器档案 —— 快 400 倍、成本为零、结果可审计。

## 文档

| 文档 | 内容 |
|------|------|
| [架构设计方案](./docs/架构设计方案.md) | 完整设计：五要素、执行链路、目录增强、网关 |
| [集成交付指南](./docs/集成交付指南.md) | 接入旧系统的三步动作、改造量、规模化交付 SOP |
| [迁移方案-新系统接入](./docs/迁移方案-新系统接入.md) | 换宿主要改什么、SDK 现有缺口 |
| [功能清单](./docs/功能清单.md) | 已实现 / 规划中，逐项对照 |
| [部署运行指南](./docs/部署运行指南.md) | 基础设施、构建、启动、演示 |
| [意图即服务-真实案例](./docs/意图即服务-真实案例.md) | 在真实业务系统里的落地复盘 |

## 设计取舍

- **为什么必须"上下文桥"**：推理循环在独立线程执行工具，宿主的登录态与数据权限挂在原线程上。
  不搬运不会报错，而是**静默返回错误结果**（越权查到别人的数据，或查不到任何数据）。
- **为什么"目录隐藏 ≠ 免鉴权"**：知道意图编号的人可以直接 POST 执行接口，所以执行前独立再校验一次。
- **为什么规范落库而不是只读 classpath**：运营要能上架/下架/改角色而不发版；
  播种只在物理不存在时写入，后台删掉的意图不会因重启复活。
- **已知局限**：整卡返回（无流式）、单轮执行（无多轮对话）、网关服务端未实现。
  详见 [功能清单](./docs/功能清单.md)。

## 许可证

[Apache License 2.0](./LICENSE)

---

**关键词**：意图即服务 · Intent as a Service · AI 嵌入 · Agent · 存量系统 AI 化 · 去聊天框 · 工具调用 · 若依 · yudao
