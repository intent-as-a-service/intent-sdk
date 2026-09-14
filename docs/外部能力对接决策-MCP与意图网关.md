# 外部能力对接决策：知识库/本体工具化，以及 MCP vs 意图网关

> 用户澄清的架构取向：
> 1. **知识库与本体都外置**（后续可自研本地版）；
> 2. 客户已建成时，**通过工具调用对接**；
> 3. **意图执行时用"工具调用"完成本体与知识库的植入**；
> 4. 其余工具由**业务系统内置 SDK** 提供。
>
> 本文回答两件事：(A) "一切皆工具"这个设想的可行性与陷阱；
> (B) 外部系统工具走 **MCP** 还是 **意图网关**。

---

## 0. 结论

**A. "一切皆工具"成立，且让 `KnowledgeRetriever` SPI 有条件降级为"可选"。**
但有一个必须处理的陷阱：**工具是模型驱动调用（概率性），而"必须检索""不许编造实体"是边界条件（确定性）**。
你们自己的数据已经证明了概率性不可接受（`followups` 从 12 次里 6 次 → 8 次里 7 次，即**改造后仍有 ~12% 该输出而没输出**）。
→ **建议"双通道"**：确定性通道负责"必须发生"，工具通道负责"探索性获取"。

**B. MCP 与意图网关不是二选一，它们在不同层，应该分层共存。**
但**先做 MCP**，理由是**成熟度差了一个数量级**：

| | MCP | 意图网关 |
|---|---|---|
| 代码现状 | `McpClient`(314) + `McpTool`(48) + `McpServerRegistry`(115) **已实现** | `HttpGatewayClient.java` **64 行，且全仓 0 调用方** |
| 服务端 | 第三方现成（各家 MCP Server） | **不存在**（`intent-gateway-server` 规划在 M2，不在本仓库） |
| 可跑通时间 | **1~2 人日**（写一个适配器） | **数周**（要造服务端 + 凭证 + 路由 + 审计） |

**C. 但有一条硬边界：MCP 不适合"以用户身份做写操作"。**
MCP Server 是**另一个进程**，无 ThreadLocal、无 `SecurityContextHolder`
→ **工具调用拿不到"当前登录用户"**，除非把身份当参数显式传（就退化成服务间调用）或按租户起连接（有成本）。
这条边界正好把两者的分工划清楚（见 §4）。

---

## 1. "一切皆工具"：可行性

### 1.1 成立的部分

`HostToolRegistry` + `IntentTool` 已经是**统一的能力收口**（`intent-sdk-core/.../tool/`）。
把本体与知识库做成 `IntentTool`，意味着：

- 对外部客户：包一层 HTTP/DB 调用即可，客户端已建成什么就用什么；
- 对后续自研：直接实现 `IntentTool`，替换实现不动意图契约；
- 与业务 SDK 工具**同构**：同一个白名单、同一条执行链、同一份留痕（`StepTrace.tool`）。

这确实比"为知识库单开一个 SPI"更灵活——**你的判断是对的**。

### 1.2 陷阱：概率性 vs 确定性（必须处理）

`AgentExecutor` 是**模型自主规划**：模型决定要不要调工具。这带来三个具体问题：

| 风险 | 具体表现 | 后果 |
|---|---|---|
| **该检索而不检索** | 模型自认为知道答案，直接 `submit_result` | 回答无依据、可能编造；且**不可复现**（同样的问题两次结果不同） |
| **该验证而不验证** | 本体校验做成工具 → 模型跳过 | 编造的 `customerId` 一路查到宿主 |
| **循环依赖** | 模型先调 `knowledge_search` 还是先调业务工具？无约束 | token 浪费、时序随机 |

**证据**：`架构设计方案.md:196` 自己记录了 `followups` 的命中率
——"改造前 12 次里只有 6 次带 followups"，改造提示词后"8 次执行有 7 次输出 4~5 条"。
即**经过提示词优化，仍约 12% 不遵守**。这还是"输出一个字段"这种简单要求；
"必须先检索再回答"这种**带前提**的要求，靠提示词约束的可靠性只会更低。

### 1.3 建议：双通道，各司其职

| 通道 | 机制 | 负责 | 何时用 |
|---|---|---|---|
| **确定性通道** | `ExecutorProfile.flow` 的 `knowledge` 节点（已实现，`FlowExecutor.java:229-265`）；系统提示词前置注入 | **必须发生**的检索（合规、引用、事实核查） | 结果要引用来源、监管要求有据 |
| **工具通道** | `knowledge_search` 作为 `IntentTool` | **探索性**获取（模型按需追问、多轮收敛） | 开放式分析、模型自己决定查什么 |
| **确定性通道** | 参数 Schema 的**动态枚举注入**（见 §2.2） | **必须成立**的约束（实体合法性） | 所有涉及实体 ID 的意图 |
| **工具通道** | `resolve_entity` / `describe_entity` 作为 `IntentTool` | 候选太多时的**两步消解** | 实体量大、需模糊匹配 |

**结论**：`KnowledgeRetriever` SPI **不要删**，降级为"确定性通道的可选实现"；
同时新增 `knowledge_search` 工具作为"探索通道"。两者共用同一份 kb 语义（都用知识库 ID）。

---

## 2. 本体怎么"植入"才对（这里我要纠正你的设想）

你说"通过工具调用完成本体的植入"。**本体里有一部分不适合做成工具**，需要分开：

### 2.1 本体的四类内容，植入方式不同

| 本体内容 | 适合做成工具？ | 正确的植入方式 |
|---|---|---|
| 实体有哪些字段、含义、枚举值 | ✅ 适合 | **`describe_entity` 工具** + 提示词里放"当前对象"的字段摘要 |
| 实体关系（`Customer --1:N--> Contract`） | ✅ 适合 | **`query_relations` 工具**（模型探索用） |
| **"哪些工具吃哪些实体"** | ❌ 不适合 | **编排层的静态声明**——用于工具链推导、白名单校验，模型不需要知道 |
| **"参数值必须是合法实体"** | ❌ **不适合** | **参数 Schema 层**（见 §2.2）——这是**边界条件**，不能交给模型自觉 |

**为什么"参数合法性"不能做成工具**：它是**进入执行前的准入检查**，属于
`IntentRuntime` 的职责（与现有的 `MISSING_PARAMS` / `VALIDATION_ERROR` 同一层）。
做成工具 = 让模型"选择是否自我约束"，逻辑上就不成立。

### 2.2 正确做法：动态枚举注入（治本，且改动小）

在 `AgentExecutor.buildSystemPrompt` 附近，构建工具 Schema 时：
若本体声明 `customerId` 引用 `crm.Customer`，且**上下文已定位到候选集**
（页面上下文里有 `customerId`，或用户是销售、其名下客户 ≤ N 条），
就把候选集**注入成 JSON Schema 的 `enum`**：

```json
{ "customerId": { "type": "string", "enum": ["C-1001","C-1002","..."] } }
```

模型**只能从枚举里选**，从根上消灭编造 ID。
候选集过大时（> 20 条），退回两步法：先调 `resolve_entity` 工具拿到候选，再填参。

这与你们已有的"实体槽位自动带出 18 个客户候选"（`集成交付指南.md:232`）
**是同一个机制、同一个数据源**，只是把它从"前端表单"复用到"模型参数约束"。

### 2.3 于是本体的最小落地形态（不引入图数据库）

```yaml
# ontology/crm.yaml —— 与 IntentSpec / ExecutorProfile 同风格，启动强校验
entities:
  Customer:
    fields: { id: {type: string}, name: {type: string}, level: {type: string, enum: [A,B,C]} }
  Contract:
    fields: { id: {type: string}, customerId: {type: string, ref: Customer}, endTime: {type: date} }
relations:
  - { from: Customer, to: Contract, name: contracts, cardinality: "1:N" }
  - { from: Contract, to: Payment,  name: payments,  cardinality: "1:N" }
```

三个消费者（**都不需要模型参与**）：
1. **参数 Schema 校验/枚举注入**（`IntentRuntime` + `AgentExecutor`）；
2. **知识库检索的实体过滤**（见 §3）；
3. **工具链推导/白名单校验**（编排层）。

模型侧只通过 `describe_entity` / `resolve_entity` / `query_relations` 三个工具**只读**访问。

---

## 3. 知识库对接：三层，客户建成什么接什么

按你的"外置"取向，`KnowledgeRetriever` 的实现可以有三种，**同一条 SPI**：

| 客户现状 | 实现方式 | 工作量 |
|---|---|---|
| 用 ruoyi/yudao 自带 RAG | 调 `AiKnowledgeSegmentService.searchKnowledgeSegment`（已实测存在） | 0.5 人日 |
| 已有外部知识库（Dify / RAGFlow / 向量库直连 / ES） | 包一层 HTTP/SDK 调用 | 0.5~1 人日/家 |
| 暂无 | 先挂 `NoopKnowledgeRetriever`（明确报错，不静默跳过） | 0.1 人日 |

**关键**：`supports(kb)` 让**多家知识库并存** —— 按 `kb` 标识分派。
意图档案里写 `kb: dify-prod` 或 `kb: 1001`（yudao 知识库 ID），路由到对应实现。
这就是你要的"外部客户建成什么就接什么"。

同理，**本体外置**：`OntologyProvider` SPI，宿主可对接客户既有元数据系统；
SDK 内置一个 YAML 实现作为默认。与 `KnowledgeRetriever` 对称设计。

---

## 4. MCP vs 意图网关：分层，不是二选一

### 4.1 本质区别只有一条

| | MCP | 意图网关 |
|---|---|---|
| **谁决定调用** | **模型**（看到工具描述后自主选） | **契约**（`IntentSpec` 声明，前端点击触发） |
| 粒度 | **细**：单个工具/函数 | **粗**：一个业务意图（可含多步编排） |
| 发现方式 | **动态**：`tools/list` 运行时发现 | **静态**：意图目录注册 |
| 参数 | JSON Schema，模型生成 | JSON Schema **+ 执行前强校验 + 槽位补全** |
| 幂等/重试 | 协议不管 | `policy.maxRetries`、执行留痕可回放 |
| 身份传递 | 无（协议无此概念） | **短时凭证 + 身份映射**（设计目标） |
| 审计 | 无 | **全链路留痕**（设计目标） |
| 现状 | **已实现可用**（pi-java） | **客户端 64 行、服务端不存在** |

### 4.2 硬边界：MCP 拿不到"当前登录用户"

这是最关键的技术事实，直接决定分工：

```
MCP Server = 另一个进程
   → 无 ThreadLocal、无 SecurityContextHolder
   → McpClient 由 McpServerRegistry.fromSettings 一次性 spawn（McpServerRegistry.java:24）
   → 连接是"系统级"的，不是"用户级"的
```

后果：**MCP 工具无法"以当前登录用户的身份"访问受权限保护的数据。**
两条出路都有代价：
- **身份当参数显式传** → 退化成服务间调用，宿主侧仍要重建权限（回到你要避开的坑）；
- **按用户/租户起连接** → 进程数与连接数随用户数增长，不可接受。

**这条边界恰好把分工划清楚**：

| 场景 | 通道 | 理由 |
|---|---|---|
| 只读查询、能力发现、知识检索、文档获取 | **MCP** | 无副作用；数据可脱敏；生态广；动态发现价值大 |
| 需要审批的写操作、跨系统编排、敏感/高危动作 | **意图网关** | 需要身份、凭证、幂等、审计、编排 |
| 本系统内的业务工具 | **不用两者** | 进程内直调宿主 SDK（`IntentTool`），这才是你的初衷 |

**一句话**：**没有外部副作用的走 MCP，有副作用和治理要求的走网关。**

### 4.3 网关的真正独特价值：它不只是"另一个 RPC"

如果只把网关理解成"远程工具调用"，那和 MCP + 一个 HTTP 客户端没区别。
它不可替代的地方是：

1. **身份换发**：网关校验来源后签发**短时凭证**，目标系统本地校验后**映射为用户身份执行**
   —— 这样目标系统的权限体系**不用改造**（正是你初衷的跨系统版本）；
2. **调用链贯通**：跨系统审计（谁、从哪个系统、调了什么、结果如何）；
3. **契约前置校验**：意图在执行前有 Schema 校验与槽位补全，MCP 没有这层。

→ 所以网关的定位应该是"**跨系统的意图路由与身份治理**"，
而不是"**工具总线**"。工具总线交给 MCP。

### 4.4 建议的落地顺序

| 步骤 | 内容 | 工作量 | 依赖 |
|---|---|---|---|
| **G1** | **MCP 适配器**：把 MCP 工具包成 `IntentTool`，注册进 `HostToolRegistry` | **1~2 人日** | 无 |
| **G2** | 工具命名空间与白名单对齐（MCP 工具名形如 `server__tool`，需与 `IntentSpec.tools` 白名单正则匹配） | 0.5 人日 | G1 |
| **G3** | **只读约束**：MCP 工具默认只允许只读；写类 MCP 工具需显式声明并走审批 | 0.5 人日 | G1 |
| **G4** | 知识库三层适配（yudao / 外部 / Noop）+ `OntologyProvider` | 2~3 人日 | 无 |
| **G5** | 确定性通道接线（`ExecutorProfile.knowledge` 解禁 + 枚举注入） | 2~3 人日 | G4 |
| **G6** | **网关只在真有跨系统写需求时再启动**（目前 M2 规划，且服务端不存在） | 数周 | 业务驱动 |

### 4.5 G1 的一个实现要点（真会踩）

`McpTool` 实现的是 `dev.pi.agent.AgentTool`（`McpTool.java:12,46`），
而意图执行要的是 `dev.intent.sdk.tool.IntentTool`。**两者是不同契约**（这是有意的解耦）。

所以适配器方向是：`McpTool(AgentTool)` → `IntentTool`，落到 `intent-sdk-pi`（唯一知道 pi 形态的模块）。
但更好的做法是**直接在 MCP 层适配**：把 `McpClient.callTool(name, args)`（`McpClient.java:135`）
包成 `IntentTool`，绕过 `AgentTool` 这一层，避免"两侧都是 AgentTool 却分属不同模块"的尴尬。

**另一处会踩**：`McpTool` 的工具名带命名空间（`McpTool.java:9-12` 注释"Tool names are namespaced as..."），
而 `ExecutorProfileValidator.java:18` 的工具名正则是 `^[a-z][a-z0-9_]*$`
—— **双下划线命名空间里的分隔符要确认能通过这个正则**，否则 MCP 工具在档案里声明就被拒。这是 G2 要解决的。

---

## 5. 更新后的问题清单（供你拍板）

| # | 决策点 | 我的建议 |
|---|---|---|
| 1 | `KnowledgeRetriever` 是否保留 | **保留**，降级为"确定性通道的可选实现"；同时新增 `knowledge_search` 工具做探索通道 |
| 2 | 本体是否只做工具 | **不是**。参数合法性走 **Schema 层**（动态枚举），其余三块做工具 + 静态声明 |
| 3 | 外部工具首选协议 | **先 MCP**（1~2 人日可跑通）；网关等真有跨系统写需求再启动 |
| 4 | 是否现在就建网关 | **否**。服务端不存在，且当前无业务驱动 |
| 5 | 本体是否引入图数据库 | **否**。YAML + 三个消费者（参数校验/检索过滤/工具链推导）足够 |
| 6 | 知识库多家并存 | **靠 `supports(kb)` 分派**，意图档案里用 kb 标识选实现 |

---

## 附录：证据

| 结论 | 证据 |
|---|---|
| pi-java MCP 已实现 | `pi-java/pi-coding-agent/.../core/mcp/McpClient.java`（314 行：`connect`/`listTools`/`callTool`）、`McpTool.java`（48）、`McpServerRegistry.java`（115） |
| MCP 工具名带命名空间 | `McpTool.java:9-12` |
| 工具名正则（可能与命名空间冲突） | `next-agent/intent-sdk-core/.../executor/ExecutorProfileValidator.java:18` |
| 网关客户端是空壳 | `next-agent/intent-sdk-gateway/` 仅 `HttpGatewayClient.java`（64 行） |
| 网关调用点不存在 | `IntentRuntime` 只用 `gateway.isAvailable()`（`IntentRuntime.java:231-233`），`GatewayClient.invoke` 全仓 0 调用 |
| 确定性检索已实现 | `FlowExecutor.java:229-265`（`knowledge` 节点，无检索器时明确报错） |
| yudao 已有 RAG 可复用 | `AiKnowledgeSegmentService.java:131`、`AiKnowledgeSegmentServiceImpl.java:268-295` |
| 提示词约束的可靠性上限 | `架构设计方案.md:196`（12 次里 6 次 → 8 次里 7 次） |
| 实体候选机制已存在（可复用到模型参数） | `集成交付指南.md:232`（实体槽位自动带出 18 个客户候选） |
| MCP 连接是系统级而非用户级 | `McpServerRegistry.java:24`（`fromSettings` 一次性 spawn） |
