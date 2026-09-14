# pi / pi-java 能力对照与 agent-next 意图执行补强路线

> 目的：把 agent-next 的意图执行做完善。围绕**本体、知识库、工具调用、技能、智能体**五项能力，
> 逐项盘清「pi 原版有什么 / pi-java 已经转了什么 / next-agent 现在用了什么 / 还缺什么」，
> 并给出「能从原版转的先转过来、原版也没有的再自建」的推进顺序。
>
> 代码基线（本文件所有结论均可按路径复核）：
> - pi 原版（TS）：`pi-agent/pi/packages/`（coding-agent 70.2K 行 / ai 25.3K / tui 15.8K / agent 11.8K / 其余 ~9K）
> - pi-java 移植版：`pi-agent/pi-java/`（10 模块，Java 21，仅依赖 Jackson）
> - 意图层：`next-agent/`（intent-protocol / intent-sdk-core / intent-sdk-host / intent-sdk-pi）

---

## 0. 一句话结论

1. **五项能力里，四项（工具调用、技能、智能体、扩展机制）pi 原版已有完整实现且 pi-java 已经移植了大部分内核**；
   真正的缺口不在「有没有代码」，而在**内核能力没有接到两个上层**：
   - pi-java 自己的 `pi-coding-agent` 没接（Skills/PromptTemplates 是死代码，扩展事件不能拦截工具）；
   - agent-next 的 `AgentExecutor` 没接（自己手搓系统提示词，`beforeToolCall`/`afterToolCall` 全部空着）。
2. **知识库只有「半个骨架」**：`KnowledgeRetriever` SPI + FlowExecutor 的 `knowledge` 节点已经通了，
   但 `ExecutorProfile.knowledge` 声明式附件被构建期显式拒绝，且**没有任何检索实现**（向量库/关键词都还没有）。
3. **本体在 pi 和 pi-java 里都不存在，在 next-agent 里也不存在**——不是「转过来就行」，必须自建。
   好消息是：本体最该落在**工具调用的 Schema 契约层 + 知识库的检索过滤层**，而这两层的接口位置已经就绪。
4. **另外：审计顺带查出 12 个既有缺陷**（见 §3.3），其中 3 个会直接干扰后续能力的验收——
   `maxTurns` 声明了但从不生效（"防失控"是假的）、装配网关后 REMOTE/COMPOSITE 会被当本地意图执行、
   skill/flow 路径不校验意图工具白名单。**建议先修这三条再补能力。**


---

## 1. pi 原版（TS）能力地图

### 1.1 分层总览

| 包 | 行数 | 职责 |
|---|---|---|
| `packages/ai` | 25322 | 模型接入（9 种协议）、消息类型、事件流、SSE、成本/token、OAuth、deferred tools |
| `packages/agent` | 11768 | **AgentLoop / Agent / harness 会话层**（Skills、PromptTemplates、compaction、session tree） |
| `packages/coding-agent` | 70249 | 工具集、会话管理、扩展系统、系统提示词、权限信任、MCP、交互模式、TUI 模式 |
| `packages/tui` | 15780 | 终端 UI 组件 |
| `protocol/client/server/telemetry/session-backends/evals` | ~6900 | RPC、遥测、会话后端、评测 |

### 1.2 工具调用（工具调用）

pi 的工具契约在 `packages/agent/src/types.ts:386-410`：

```ts
export interface AgentTool<TParameters extends TSchema, TDetails> extends Tool<TParameters> {
  label: string;
  prepareArguments?: (args: unknown) => Static<TParameters>;   // 兼容垫片（旧参数形态）
  execute: (
    toolCallId: string,
    params: Static<TParameters>,
    signal?: AbortSignal,
    onUpdate?: AgentToolUpdateCallback<TDetails>,              // 流式部分结果
  ) => Promise<AgentToolResult<TDetails>>;
  executionMode?: ToolExecutionMode;                            // 单工具覆盖并行/串行
}

export interface AgentToolResult<T> {
  content: (TextContent | ImageContent)[];
  details: T;
  usage?: Usage;
  addedToolNames?: string[];   // 动态引入新工具（deferred tools 的基础）
  terminate?: boolean;         // 整批都 true 才提前结束循环
}
```

工具执行循环在 `packages/agent/src/agent-loop.ts`，能力清单：

| 能力 | 位置 | 说明 |
|---|---|---|
| 参数校验 | `prepareToolCall` L598-666 | 校验失败折叠成 error tool result，**不中断循环**（模型可自纠） |
| 执行前钩子 | `config.beforeToolCall` L617-645 | 返回 `{block:true, reason}` 可**否决**执行；返回 `terminate` 参与提前结束 |
| 执行后钩子 | `config.afterToolCall` L722-749 | 可替换 `content/details/isError/usage/terminate` |
| 流式部分结果 | `executePreparedToolCall` L681-694 | `onUpdate` → `tool_execution_update` 事件 |
| 并行/串行 | `executeToolCalls` L409-424 | 默认并行；任一工具 `executionMode="sequential"` 则整批串行 |
| 截断保护 | `failToolCallsFromTruncatedMessage` L379-404 | `stopReason==="length"` 时**拒绝执行全部工具调用**（参数可能被截断） |
| 批次提前结束 | `shouldTerminateToolBatch` L580-582 | 全部 `terminate` 才结束 |
| 工具集可变 | `addedToolNames` | 工具结果可引入新工具，配合 `DeferredTools` 实现"按需暴露工具" |

内置工具（`packages/coding-agent/src/core/tools/index.ts:95-105`）：`read / bash / powershell / edit / write / grep / find / ls`。
agent 包另有更精简的一套：`read / write / edit / bash / image`。

> **关键事实：pi 核心工具集里没有 `skill` 工具、没有 `knowledge` 工具、更没有 `ontology` 工具。**
> 技能靠「提示词声明 + read 加载」，知识库靠「扩展自己注册工具」。

### 1.3 技能（技能）

pi 实现了 [Agent Skills 标准](https://agentskills.io/specification)，文档 `packages/coding-agent/docs/skills.md`。三层实现：

| 层 | 文件 | 职责 |
|---|---|---|
| harness 层（无 IO 依赖） | `packages/agent/src/harness/skills.ts` (386 行) | `loadSkills` / `loadSourcedSkills` / `formatSkillInvocation` |
| 应用层 | `packages/coding-agent/src/core/skills.ts` (507 行) | `loadSkills` / `loadSkillsFromDir` / `formatSkillsForPrompt` |
| 装配层 | `core/resource-loader.ts` (979 行) | 统一加载 skills / prompts / themes / extensions / AGENTS.md |

**渐进式披露（progressive disclosure）是核心设计**：
1. 启动时扫描技能目录，只提取 `name` + `description`；
2. 系统提示词注入 `<available_skills>` XML 块（**只含描述，不含正文**）——`formatSkillsForPrompt`；
3. 模型判断任务匹配时，用 **`read` 工具**读完整 `SKILL.md`；
4. 用户可以 `/skill:name args` 强制展开，走 `formatSkillInvocation` 直接内联正文。

发现规则（`skills.ts:163-275`）：含 `SKILL.md` 的目录即技能根（不再递归）；根目录 `.md` 需有 frontmatter `description` 才算技能；遵循 `.gitignore/.ignore/.fdignore`；跳过 `.` 开头与 `node_modules`。
校验：name ≤64 字符且 `^[a-z0-9-]+$`、description ≤1024 字符；违规**只告警不拒绝加载**（除 description 缺失）。

装载路径：`~/.pi/agent/skills/`、`~/.agents/skills/`、`.pi/skills/`、`.agents/skills/`、packages、settings `skills` 数组、CLI `--skill`。

### 1.4 智能体（智能体）

| 层 | 文件 | 职责 |
|---|---|---|
| 循环 | `packages/agent/src/agent-loop.ts` (794 行) | 纯函数式 `agentLoop` / `agentLoopContinue`，事件流驱动 |
| 门面 | `packages/agent/src/agent.ts` (528 行) | 有状态 Agent：transcript、steer/followUp 队列、CAS 忙拒绝、abort |
| 会话层 | `packages/agent/src/harness/agent-harness.ts` (471 行) | prompt/steer/followUp/compact/abort/resume + 配置 getter/setter |
| 应用会话 | `packages/coding-agent/src/core/agent-session.ts` (3145 行) | 扩展分发、技能展开、压缩、会话树 |
| 编排钩子 | `agent-loop.ts` L168-270 | `getSteeringMessages` / `getFollowUpMessages` / `prepareNextTurn` / `shouldStopAfterTurn` |

**多智能体在 pi 里是扩展而非核心**：`packages/coding-agent/examples/extensions/subagent/index.ts`
（注册一个 `subagent` 工具，**spawn 独立 pi 进程**，用 JSON 模式取结构化输出）。

### 1.5 扩展机制（能力装载的总闸门）

`packages/coding-agent/src/core/extensions/types.ts`（1791 行）定义 `ExtensionAPI`（L1252+）：

- **注册**：`registerTool` / `registerCommand` / `registerShortcut` / `registerFlag` / `registerProvider`
  （含 `streamSimple` 自定义流与 `oauth`）/ `registerMarkdownTransformer` / 消息与条目渲染器；
- **事件（35 个）**：`project_trust`、`resources_discover`、`session_*`（8 个）、`context`、
  `before/after_provider_*`、`before_agent_start`、`agent_start/end/settled`、
  `turn_start/end`、`message_start/update/end`、`tool_execution_start/update/end`、
  **`tool_call`（可否决）**、**`tool_result`（可改写）**、`input`（可改写/拦截）、`user_bash`；
- **动作**：`sendMessage` / `sendUserMessage` / `exec` / `setActiveTools` / `setModel` / `setThinkingLevel` / `setSessionName` / `setLabel`。

`resources_discover` 事件是技能/提示词/主题的**外部注入点**（`core/extensions/runner.ts:1201-1242`）。

### 1.6 本体 / 知识库（本体 / 知识库）

对 `packages/**/*.ts` 全量检索 `ontology|knowledge|graph.?rag|embedding|retriev|semantic|vector`：

> **pi 原版没有任何本体、知识图谱、向量检索、embedding 的实现。**
> 命中项全部是无关词（`undo stack`、`knownVectors`（CBOR 测试向量）、OSC 133 "semantic prompt"、
> `semantic` 作形容词、recraft 的 "vector" 图片模型名）。

**这是本次分析最重要的负面结论**：这两项能力**没有原版可抄**。

---

## 2. pi-java 移植版现状

### 2.1 模块与依赖

```
pi-protocol → (无)              pi-ai → (Jackson, JDK http)
pi-client / pi-server / pi-session-backends / pi-evals / pi-telemetry
pi-agent → pi-ai
pi-coding-agent → pi-agent, pi-ai, pi-protocol
pi-plugins/* → pi-coding-agent
```
Java 21（`maven.compiler.release=21`），全仓仅 Jackson + JUnit + JLine。

移植规模（main/test java 文件数）：`pi-ai` 100/38、`pi-coding-agent` 107/61、`pi-agent` 50/13、
`pi-plugins` 36/30、`pi-server` 16/2、`pi-evals` 9/8、`pi-session-backends` 9/2、`pi-telemetry` 4/4、`pi-protocol` 3/2、`pi-client` 7/1。

### 2.2 五项能力逐项核对

#### ① 工具调用 —— 内核齐全，接线缺失

| 项 | pi (TS) | pi-java | 状态 |
|---|---|---|---|
| 工具契约 | `AgentTool.execute(id, params, signal, onUpdate)` | `AgentTool.execute(id, args, signal)` — `pi-agent/.../AgentTool.java:46` | 🔴 **缺 `onUpdate`** |
| 结果契约 | `content/details/usage/addedToolNames/terminate` | 同名 record — `AgentTool.java:50-55` | ✅ |
| 参数兼容垫片 | `prepareArguments` | 同名 default 方法 — `AgentTool.java:35` | ✅ |
| 单工具执行模式 | `executionMode` | ❌ 无 | 🔴 |
| 工具参数校验 | `validateToolArguments` | `pi-ai/.../util/ToolValidator.java` (118 行) | 🟡 仅 required/原始类型/enum，**无 additionalProperties、无 oneOf/anyOf、遇错即抛（不返回错误列表）** |
| 执行循环 | `agent-loop.ts` | `pi-agent/.../AgentLoop.java` (460 行) | ✅ 并行/串行、批次提前结束、截断保护均有 |
| 执行前否决 | `beforeToolCall` → block | `AgentLoopConfig.beforeToolCall` — `AgentLoopConfig.java:74`，`AgentLoop.java:418-430` 已调用 | ✅ **内核已通** |
| 执行后改写 | `afterToolCall` | `AgentLoopConfig.afterToolCall` — `AgentLoopConfig.java:88`，`AgentLoop.java:462-473` 已调用 | ✅ |
| 流式部分结果 | `onUpdate` → `tool_execution_update` | 事件类型**已定义**（`AgentEvent.java:38`）但 `AgentLoop` **从不 emit** | 🔴 **死事件** |
| 动态增删工具 | `addedToolNames` + `DeferredTools` | 字段保留（`AgentTool.java:54`），`pi-ai/.../util/DeferredTools.java` 已移植 | 🟡 未接线 |
| 内置工具 | 8 个（read/bash/powershell/edit/write/grep/find/ls） | 9 个（+ `GlobTool`）— `pi-coding-agent/.../tools/` | ✅ |
| MCP 客户端 | `core/extensions` 内 | `pi-coding-agent/.../core/mcp/`（McpClient/McpServerRegistry/McpTool） | ✅ |

**关键接线缺口**：`Agent.java:209` 自建 `AgentLoopConfig` 时**只设了 model/apiKey/steering/followUp**，
`beforeToolCall` / `afterToolCall` / `toolExecution` / `transformContext` **全部留空且无 setter**。
`pi-coding-agent/Main.java:236-245` 只在 `AgentEvent.ToolExecutionStart/End` 上"事后观察"，
无法否决——所以扩展系统里的 `tool_call` / `tool_result` 事件（`ExtensionEventNames.java:36-37`）
在 pi-java 里**没有落到 `AgentLoop` 的钩子**。

#### ② 技能 —— 代码已移植，完全未接线（死代码）

| 项 | pi (TS) | pi-java | 状态 |
|---|---|---|---|
| 技能加载 | `harness/skills.ts` (386) | `harness/Skills.java` (233) + `IgnoreMatcher.java` + `Frontmatter.java` | ✅ 逻辑对齐（含 ignore 规则、诊断码） |
| 技能模型 | `Skill{name,description,content,filePath,disableModelInvocation}` | `harness/Skill.java` (23) | ✅ |
| 提示词注入 | `formatSkillsForPrompt` | `harness/SkillPrompts.formatSkillsForSystemPrompt` — `SkillPrompts.java:14` | ✅ 实现存在 |
| 显式调用展开 | `formatSkillInvocation` | `Skills.formatInvocation` — `Skills.java:41` | ✅ |
| `/skill:name` 命令 | `agent-session.ts:1350-1382` | ❌ pi-java 无 | 🔴 |
| **实际接线** | `core/system-prompt.ts:162-164` 调用 | ❌ **`SystemPrompt.build()` 从不调用 `SkillPrompts`** | 🔴🔴 **死代码** |

实证：全仓检索 `SkillPrompts.` / `Skills.load` / `Skills.formatInvocation`，**只命中一处 Javadoc 注释**
（`PromptTemplate.java:10`）。即 pi-java 的 `SystemPrompt.java`（83 行，硬编码工具清单 + AGENTS.md）
与 `Skills`/`SkillPrompts`/`PromptTemplates` 之间**没有任何调用关系**。

#### ③ 智能体 —— 单智能体齐全，多智能体在插件里

| 项 | pi-java | 状态 |
|---|---|---|
| `AgentLoop` | 460 行，含全部 6 个钩子 | ✅ |
| `Agent` 门面 | `Agent.java` (277)，CAS 忙拒绝、steer/followUp、abort | ✅ |
| `AgentHarness` | `AgentHarness.java` (320)：prompt/steer/followUp/compact/abort/resume/recordUsage | ✅（TS 侧对应持久操作仍是 `HarnessNotImplemented`，Java 反而更完整） |
| 会话层 | `SessionTree` / `SessionState` / `LaneReducer`(687) / JSONL v4 / InMemory | ✅ |
| 压缩 | `Compaction` (415) + `BranchSummarization` (190) + `CompactionLlm` (186) | ✅ |
| 提示词模板 | `PromptTemplates.java` (151) + `PromptTemplate.java` | ✅ |
| 遥测 | `HarnessTelemetry` / `AiTelemetry` | ✅ |
| **多智能体** | `pi-plugins/pi-subagents/`：`AgentDefinition`(157) 解析 frontmatter 智能体文件、`AgentRegistry`(119)、`SubagentTool`(323)（run/status/output/stop/wait + background）、`ChildLauncher`(184)、`ChildProcess`(124)、`BackgroundRuns`(132)、`ChildModeApplier`(110)、`SubagentsPlugin`(179) | ✅ **已移植** |
| 其它插件 | `pi-web-access`（web_search/web_fetch/内容抽取/SSRF 防护）、`pi-trace-viewer`、`pi-web-ui` | ✅ |

> pi-java 的 `SubagentTool` 走**子进程启动**（`ChildProcess.start`），与 TS 版 spawn pi 进程同形。
> 对 agent-next 而言这是"进程外多智能体"，与"进程内意图执行"是两种东西，需单独判断是否采用。

#### ④ 扩展系统 —— 事件齐了，语义没齐

| 项 | pi-java | 状态 |
|---|---|---|
| `PiExtension` + `ExtensionApi` | `ext/ExtensionApi.java` (107)、`PiExtension.java`、ServiceLoader 装载 | ✅ |
| 事件类型 | 35 个全部定义（`ExtensionEvent.java` 42 个 record + `ExtensionEventNames` 35 个常量） | ✅ 命名对齐 |
| 注册能力 | `registerTool/Command/Shortcut/Flag/Provider/messageRenderer/markdownTransformer/entryRenderer` | ✅ |
| `registerProvider(Provider)`（自定义 `streamSimple` + oauth） | 仅 `registerProvider(String, ExtensionProviderConfig)`；`ExtensionModelConfig` 支持 `streamSimple` | 🟡 |
| **事件否决/改写语义** | `ExtensionApi.on` 返回的 `R` **被显式忽略**（`ExtensionApi.java:21-23` 注释自认），`tool_call` / `tool_result` 无实际拦截效果 | 🔴 |
| 工具有效集控制 | `getActiveTools` / `setActiveTools` 有声明 | 🟡 与 `AgentLoop` 的接线待确认 |

#### ⑤ 本体 / 知识库 —— 都没有

对 `pi-java/**/*.java` 检索 `ontology|knowledge|graph|embedding|retriev|vector|semantic`：

> **NOT PRESENT。**（与 pi 原版一致，原版没有的东西移植版当然也没有。）

### 2.3 pi-java 的整体判断

- **内核层（pi-ai / pi-agent）质量高、覆盖面广**：9 种协议适配、OAuth 全家桶、deferred tools、
  constrained sampling、会话树、压缩、SKILL/模板加载器都在，且有 ~344 个测试。
- **两个明确的"最后一公里"断裂**：
  1. `Agent` 门面不暴露 `AgentLoopConfig` 钩子 → 扩展事件无法否决工具、无法流式上报工具进度；
  2. `pi-coding-agent` 的 `SystemPrompt` 不接 `Skills`/`SkillPrompts`/`PromptTemplates` → 技能体系是死代码。
- **`pi-plugins` 是 pi-java 超出原版的部分**（subagents / web-access / trace-viewer / web-ui 都是 Java 侧自研补齐），
  说明这条路已经有人走过，移植风格可参考。

---

## 3. next-agent 意图执行现状与差距

### 3.1 当前执行架构

```
IntentRuntime.execute(spec, params, context, user, toolDecorator)      intent-sdk-pi/IntentRuntime.java
  ① scope 路由：非 LOCAL 且无网关 → REMOTE_UNAVAILABLE
  ② 页面上下文自动补全必填参数（"上下文即参数"）
  ③ 缺必填 → NEED_INPUT（不烧 token）
  ④ JsonSchemaValidator 校验入参 → VALIDATION_ERROR
  ⑤ 执行器路由：spec.executor → builtin-agent | profile 构建的实例 | 自定义 SPI
  ⑥ ExecutionOutcome → IntentResult 规范化（traceId、错误码语义化）
  ⑦ TraceStore 留痕（best-effort，不吞业务结果）
```

三种执行器（`ExecutorProfile.TYPE_*`）：

| 型 | 实现类 | 模型 | 特点 |
|---|---|---|---|
| `agent` | `AgentExecutor.java` (361) | 模型现场规划：系统提示词 + 工具集 + `AgentLoop` 推理循环 + `submit_result` 输出闸门 | 灵活、耗 token |
| `skill` | `SkillExecutor.java` (323) | **确定性 steps**：工具步骤直调 + LLM 步骤单次推理，`${...}` 模板串联 | 可审计、低 token、纯工具可零 LLM |
| `flow` | `FlowExecutor.java` (332) | 节点编排：`tool` / `agent` / `knowledge` 三类节点，`when` / `stopWhen` 条件 | 声明式组合能力 |
| `remote` | ❌ 构建期拒绝 | — | 规划 M2.3 |

### 3.2 五项能力对照表（★ = 本次要补）

| 能力 | pi 原版 | pi-java | next-agent 现在 | 结论 |
|---|---|---|---|---|
| **工具调用** | 完整（含 4 参 `execute`/钩子/并行/流式） | 内核完整，`onUpdate` 缺 | 有 `IntentTool` SPI + `PiToolAdapter` + 白名单两级收窄；`AgentExecutor` 用**固定 `SEQUENTIAL`**，**钩子全空** | 🔴 **接线为主，转译为辅** |
| **技能** | 完整（SKILL.md + 渐进披露 + `/skill:`） | 加载器完整但**未接线** | **无**。`ExecutorProfile.skills` 被构建期显式拒绝（`ExecutorProfiles.java:75-77`）；`SkillExecutor` 的 "skill" 是同名不同物（是确定性步骤流程） | 🔴 **命名冲突 + 能力缺失，需先解耦再补** |
| **智能体** | 完整（含 subagent 扩展示例） | 完整（含 `pi-subagents` 插件） | `AgentExecutor` 单智能体单循环；`toolDecorator` 恢复宿主上下文 | 🟡 **单智能体够用；多智能体/子智能体要自建** |
| **知识库** | **不存在** | **不存在** | `KnowledgeRetriever` SPI + `FlowExecutor` knowledge 节点**已通**；但**无任何检索实现**，`ExecutorProfile.knowledge` 被拒绝（`ExecutorProfiles.java:72-74`） | 🟠 **骨架好，缺实现（要自建）** |
| **本体** | **不存在** | **不存在** | **无** | 🔴 **完全自建** |

### 3.3 审计发现的既有缺陷（与本次五项能力无关，但挡在路上）

这些是**当前代码里已经存在的问题**，补能力之前应先修——否则新能力的观测与治理都建立在错误地基上。

| # | 缺陷 | 证据 | 影响 |
|---|---|---|---|
| G1 | **`maxTurns` 声明了但从不生效** | `AgentExecutor.java:76,99` 存字段；`runLoop` 只设 `model/apiKey/toolExecution`（:161-163）；`AgentLoopConfig` 里根本没有 maxTurns，只有 `shouldStopAfterTurn` 钩子 | `sales-analyst-v2.yaml:31` 宣称的"防失控"是假的，唯一刹车是 `policy.timeoutSeconds` |
| G2 | **装配网关后 REMOTE/COMPOSITE 会被当本地意图执行** | `IntentRuntime.java:117-122` 只判 `gatewayAvailable()` 做拒绝，**之后没有任何远程派发分支**；`HttpGatewayClient.invoke` 全仓 0 调用（`HttpGatewayClient.java:46-72` 是死代码） | 装了网关反而语义错误——与 `架构设计方案.md:104-105` 的描述相反 |
| G3 | **`IntentSpec.tools` 白名单在 skill/flow 路径失效** | `SkillExecutor.java:139`、`FlowExecutor.java:155` 用 `tools.get(...)` **只查是否注册，不查意图白名单** | 最小权限约束在这两条路径上形同虚设 |
| G4 | **异常分支留痕的 `params` 语义不一致** | `IntentRuntime.java:166-177` 三个 catch 传**未做上下文补全的原始 params**，正常分支传 `safeParams` | 失败执行的留痕缺上下文补全信息，回放对不上 |
| G5 | **`IntentAnalyzer` 是未接线死代码** | `IntentAnalyzer.java:27-34` 全仓 0 调用方 | `架构设计方案.md` 中"意图分析器"角色实际不存在 |
| G6 | **starter 无 REST API** | `IntentHostAutoConfiguration` 只有 7 个 `@ConditionalOnMissingBean` SPI Bean，无任何 controller；`集成交付指南.md:15` 却承诺"自动装配：REST API + 默认 SPI 实现" | 端点在本仓库之外（ruoyi-office 的 `yudao-module-intent`） |
| G7 | **配置键文档与代码不一致** | 文档 `intent.suggestions.{enabled,max,cacheSeconds}` / `intent.rulesDir`；代码是 `intent.host.*` + `rule-dir`（`IntentHostProperties.java:11,25`），**且没有 suggestions 相关配置项** | 按文档配不生效 |
| G8 | **`IntentThreadContext` / `IntentConfigState.executor` / `IntentHostProperties.traceDir`·`timeZone` 均无消费方** | `IntentThreadContext.java:9-26`；`IntentConfigState.java:18`；grep `getTraceDir|getTimeZone` 仅定义处 | 声明式配置读不进来 |
| G9 | **`SkillFailure` 在两个执行器里重复定义** | `SkillExecutor.java:310-322`、`FlowExecutor.java:319-331` 语义完全相同 | 维护成本；A 阶段改钩子时容易只改一处 |
| G10 | **大量死 import** | `KnowledgeRetriever` 在 6 个文件里 import 但未使用（`IntentRuntime.java:10`、`AgentExecutor.java:10`、`SkillExecutor.java:10`、`FlowExecutor.java:10`、`LlmConnector.java:10`、`IntentRuntimeConfig.java:10`） | 提示大范围重构未清理，阅读成本高 |
| G11 | **`"GATEWAY_ERROR"` 未登记进 `IntentErrorCodes`** | `HttpGatewayClient.java:68` | 错误码体系不闭合 |
| G12 | `INTENT_NOT_FOUND` / `FORBIDDEN` / `MVP_NOT_IMPLEMENTED` 定义但无产生点 | `IntentErrorCodes.java:21,25,28` | 角色/权限相关的错误语义尚未落地 |

### 3.4 next-agent 意图执行的具体薄弱点

按"对意图执行质量的影响"排序：

1. **系统提示词是手搓字符串**（`AgentExecutor.buildSystemPrompt` L282-315）
   拼了意图模板 + 工作规则 + 工具清单 + 下一步意图目录，但**没有 hook 点**：
   无法注入技能、无法注入知识片段、无法按执行器档案追加 guidelines。
   → 对应 pi 的 `BuildSystemPromptOptions`（`customPrompt/toolSnippets/promptGuidelines/appendSystemPrompt/contextFiles/skills`）。

2. **工具调用零治理**（`AgentExecutor.runLoop` L160-167）
   只设 `model/apiKey/toolExecution=SEQUENTIAL`：
   - 没有 `beforeToolCall` → 无法做**工具级权限/租户校验/参数改写/高危工具拦截/审计前置**；
   - 没有 `afterToolCall` → 无法做**结果脱敏/结果增强/敏感字段过滤**；
   - 固定串行 → 多个独立查询（如"取客户 + 取合同 + 取回款"）白白串行耗时。

3. **无流式反馈**（`F2.3 执行状态反馈`、`F2.4 取消执行` 标 M2）
   `runLoop` 里 `AgentEvent.MessageUpdate` 落到 `default -> {}` 被丢弃；
   `ToolExecutionUpdate` 更是无从谈起（pi-java 未实现 `onUpdate`）。
   → 前端只能"整卡返回"，做不到"分析中→推理中→查询数据中"。

4. **"技能"概念被占用**：`ExecutorProfile.TYPE_SKILL` + `SkillExecutor` 已经叫 skill，
   但它表达的是**确定性步骤流程（类似工作流/DAG）**，不是 pi 的 **SKILL.md 渐进披露技能**。
   直接补 pi 的技能会造成同名冲突，必须先改名（建议 `TYPE_WORKFLOW` / `WorkflowExecutor`）。

5. **知识库只有接口没有实现**：无 chunking、无 embedding、无向量检索、无 rerank、无引用回传。
   `KnowledgeRetriever.KnowledgeChunk(content, score, meta)` 里 `meta` 已经预留，但没人填。

6. **无本体**：意图参数是扁平 JSON Schema，工具返回值是无结构 Map，
   "客户/合同/回款"之间的关系只存在于宿主代码里，模型和检索器都看不见。

---

## 4. 推进路线：先转译、后自建

> **前置步骤 P0 · 修既有缺陷（1~2 天，强烈建议先做）**
> 至少修 G1（`maxTurns` 接 `shouldStopAfterTurn` 刹车）、G2（REMOTE/COMPOSITE 要么拒绝要么真派发，
> 不能静默本地执行）、G3（skill/flow 路径补意图工具白名单校验）。
> 这三条都会**直接干扰**后续能力的验收：G1 让"防失控"是假的，G2 让跨系统语义错乱，G3 让最小权限失效。

### 阶段 A · 接线（把 pi-java 已有的能力接到意图执行）— 零新算法，收益最大

| # | 任务 | 落点 | 验收 |
|---|---|---|---|
| A1 | **`Agent` 暴露 `AgentLoopConfig` 钩子**（`beforeToolCall` / `afterToolCall` / `toolExecution` / `transformContext` / 工具级 `executionMode`） | `pi-java/pi-agent/.../Agent.java:209` 附近加 setter 并透传 config | 能在 `Agent` 层否决一次工具调用 |
| A2 | **工具流式进度**：`AgentTool.execute` 增 4 参重载（default 委派 3 参）+ `AgentLoop` emit `ToolExecutionUpdate` | `AgentTool.java:46`、`AgentLoop.java:301/338/360` | 工具可上报部分结果并冒泡为事件 |
| A3 | **`SystemPrompt` 接技能与模板**：新增 `BuildSystemPromptOptions`（对齐 pi），`formatSkillsForSystemPrompt` + `promptGuidelines` + `appendSystemPrompt` + `contextFiles` 全部生效 | `pi-java/pi-coding-agent/.../SystemPrompt.java` | 传入 skills 后系统提示词出现 `<available_skills>` |
| A4 | **扩展事件否决赛义**：`tool_call` / `tool_result` 的返回值接进 `AgentLoopConfig.beforeToolCall/afterToolCall` | `pi-coding-agent/.../ext/ExtensionRuntime.java`、`Main.java:236-245` | 插件 block 后该工具不执行 |
| A5 | **`IntentTool` 增流式回调**：`execute(id, args, ctx, onUpdate)`，`PiToolAdapter` 透传 | `intent-sdk-core/.../tool/IntentTool.java`、`intent-sdk-pi/.../PiToolAdapter.java` | 宿主工具能上报进度 |
| A6 | **`AgentExecutor` 接钩子**：`beforeToolCall` 挂宿主权限校验 + 审计前置；`afterToolCall` 挂脱敏；`toolExecution` 改 PARALLEL（高危工具标 `sequential`） | `intent-sdk-pi/.../AgentExecutor.java:160-167` | 越权工具被拦截并落痕；独立查询并行 |

### 阶段 B · 技能（转译 pi 的技能体系，先解耦命名）

| # | 任务 | 落点 | 说明 |
|---|---|---|---|
| B1 | **改名解耦**：`TYPE_SKILL` → `TYPE_WORKFLOW`，`SkillExecutor` → `WorkflowExecutor`（保留旧名别名一个版本） | `intent-sdk-core/.../executor/ExecutorProfile.java` | 避免与 pi 的 SKILL.md 技能混淆 |
| B2 | **技能加载接线**：`ExecutorProfile.skills: List<String>` 从"声明即拒绝"改为"加载技能目录"，支持项目级/classpath 两处 | `intent-sdk-pi/.../ExecutorProfiles.java:75-77` | 用 `pi-agent/harness/Skills.load` |
| B3 | **渐进披露注入**：技能只在系统提示词注入 name/description/location，正文由模型按需读 | `AgentExecutor.buildSystemPrompt` | 对齐 pi `formatSkillsForPrompt` |
| B4 | **显式执行技能**：技能型意图（或 `ExecutorProfile` 声明 skills）走 `formatSkillInvocation` 直接内联正文 | `intent-sdk-pi` 新增 `SkillInvoker` | 对齐 pi `/skill:name args` |
| B5 | **技能附带工具白名单**：SKILL.md frontmatter 的 `allowed-tools` 收窄该技能可用工具 | 新增 | pi 标为 experimental，但对意图场景（最小权限）价值高 |

### 阶段 C · 知识库（骨架已有，补实现）

> 决策 2 已定：**只走 SPI**，SDK 不内置检索实现。故本节不含"内置关键词/向量实现"。

| # | 任务 | 落点 | 说明 |
|---|---|---|---|
| C1 | **声明式启用知识库附件**：解禁 `ExecutorProfile.knowledge`，映射为系统提示词前置的检索注入（agent 型）或自动插入 knowledge 节点（flow 型） | `ExecutorProfiles.java:72-74` | 当前只有 flow 节点可用 |
| C2 | **`KnowledgeRetriever` 契约文档**：chunk 粒度、`score` 语义与量纲、`meta` 必填键、引用回传格式 | 新增 doc + `KnowledgeRetriever.java` 补充 Javadoc | **决策 2 的必要配套**——没有契约，SPI 会退化成"各写各的"，评测也无法横向比较 |
| C3 | **检索增强纪律（SDK 侧）**：固定"先检索后推理"的提示词契约 + 引用回传要求 | `AgentExecutor.buildSystemPrompt` | 对齐 `F3.4 数据来源展示`；宿主实现质量不保证，但提示词契约由 SDK 统一 |
| C4 | **检索结果并入 trace**：`StepTrace.tool("knowledge:<kb>", ...)` 已有，补 `details.chunks` 到 `ExecutionTraceRecord` | `intent-sdk-pi/.../FlowExecutor.java:256-264` | 可回放、可评测 |
| — | ~~内置关键词/向量实现~~ | — | **已取消**（决策 2） |


### 阶段 D · 本体（自建，pi 无参照）

建议**不要把本体做成一个"本体服务"**，而是做成三处轻量约束——这样才与现有架构同构：

| # | 落点 | 本体在这里扮演什么 |
|---|---|---|
| D1 | **意图参数 Schema**（`IntentSpec.paramsSchema`） | 值域取自本体：`customerId` 不再只是 `string`，而是 `{"$entity":"crm.Customer"}`——校验时用宿主实体校验器，**拦住模型编造的 ID** |
| D2 | **工具参数/返回 Schema**（`IntentTool.parameters()`） | 工具声明自己吃/吐哪些实体与关系；编排层据此**自动推导工具链**（客户→合同→回款） |
| D3 | **知识库检索过滤**（`KnowledgeRetriever.retrieve`） | 检索带上本体标签过滤：`kb=crm-docs, filter={entity:"Contract", id:"C-1"}`，而不是纯语义相似 |
| D4 | **意图输出 Schema** | `blocks` 里的 `kv/table` 列名绑定本体属性，前端渲染与数据校验共用一份定义 |

本体的最小落地形态建议：
- **实体**：`{id, name, type, properties: {name → {type, required, enum?}}}`；
- **关系**：`{from, to, name, cardinality}`（如 `Customer --1:N--> Contract --1:N--> Payment`）；
- **加载**：YAML 随宿主存放（与 `IntentSpec` / `ExecutorProfile` 同风格），启动强校验；
- **使用**：只在 D1~D4 四个点被查询，**不引入图数据库**。

### 阶段 E · 智能体（**已冻结** — 决策 3）

> 决策 3 已定：**暂不动，保持 `pi-subagents` 子进程模型**。本阶段不实施。

| 场景 | 结论 |
|---|---|
| 单意图内需要"先侦察再执行" | 用 `FlowExecutor` 的 `agent` 节点（已支持）+ `when`/`stopWhen` 条件分支 |
| 需要并行独立子任务（如"同时审查合同/回款/履约"） | **用 A6 的 PARALLEL 工具执行**（或 `FlowExecutor` 多个 `tool` 节点），**不上多智能体** |
| 角色化子智能体（不同模型/工具集/系统提示词） | **不做**。理由见 §6 决策 3 的连带含义：子进程丢失宿主调用栈，绕开权限与事务模型 |
| `subagent` 工具是否暴露给 `AgentExecutor` | **不要**。它是 `pi-coding-agent` CLI 的能力，不是意图执行的能力 |


---

## 5. 优先级与工作量估算

| 阶段 | 内容 | 依赖 | 相对工作量 | 对"意图执行完善"的增益 |
|---|---|---|---|---|
| **P0** | 修既有缺陷 G1/G2/G3 | — | 低 | 高：不修则后续验收建立在错误地基上 |
| **A** | 接线（钩子/流式/技能注入/工具治理） | P0（G3） | 中 | **最高**：立刻拿到权限拦截、审计、并行、流式 |
| **B** | 技能体系（B1 改名 + 加载 + 渐进披露） | A3；B1 是 B2~B5 前置 | 中 | 高：显式"标准作业程序"沉淀，与 `WorkflowExecutor` 分工清晰 |
| **C** | 知识库（C1 解禁 + C2 契约 + C4 轨迹） | A（注入点） | 中 | 中高：意图回答有据可依；**实现质量由宿主负责**（决策 2） |
| **D** | 本体（四处轻量约束） | C3、A1 的参数校验 | 高 | 中高（但**唯一能根治"模型编造实体"的手段**） |
| **E** | 多智能体 | — | — | **已冻结**（决策 3） |

**建议执行顺序：P0（G1/G2/G3）→ A1→A6（打通治理与流式）→ B1→B2/B3/B4/B5（技能）→ C1/C2/C3/C4（知识库）→ D1/D2（本体先接参数与工具）→ D3/D4。E 不做。**


---

## 6. 三个必须先做的决策（已定稿）

> 状态：**已决策**（2026-02 确认）。下表为最终取向，后续 §4 各阶段按此执行。

| # | 决策点 | 结论 | 对路线的影响 |
|---|---|---|---|
| 1 | `skill` 命名冲突 | **改名**：`TYPE_SKILL` → `TYPE_WORKFLOW`，`SkillExecutor` → `WorkflowExecutor`（保留旧名别名一个版本） | 解锁阶段 B；B1 为 B2~B5 的前置 |
| 2 | 知识库 SPI vs 内置 | **只走 SPI**（`KnowledgeRetriever`），SDK 不内置 chunking / 关键词 / 向量实现 | 阶段 C 收敛为 C1（解禁声明）+ C4（检索并入轨迹）+ 一份**契约文档**（chunking/引用/score 约定由宿主遵守）；原 C2 取消，C3 的提示词契约保留（SDK 侧） |
| 3 | 多智能体进程内 vs 子进程 | **暂不动，保持 `pi-subagents` 子进程模型** | 阶段 E 冻结：不做进程内 `AgentLoop` 嵌套、不做角色化子智能体。若将来要"意图内并行子任务"，**用 A6 的 PARALLEL 工具执行**（`FlowExecutor` 多 `tool` 节点 / 并行工具批）替代，而不是多智能体 |

### 决策 3 的一个连带含义（须知晓）

保持子进程模型 = **接受"意图内不存在角色化子智能体"这个能力边界**。具体是：

- 子进程 `ChildProcess.start` 另起 JVM/进程，**宿主调用栈的登录用户、租户、数据权限、事务上下文全部丢失**——
  而"权限与事务随调用栈自然生效"正是 `intent-sdk-core` 的核心设计（`IntentTool.java:12-14`）。
  子智能体若调宿主工具，拿不到当前用户，等于绕开权限模型。
- 因此**不要**把 `subagent` 工具暴露给 `AgentExecutor` 的工具集。它是给 `pi-coding-agent` CLI 用的，
  不是给意图执行用的。若确需"疑似多智能体"的效果，用阶段 E 的替代方案。

### 决策 2 的一个连带含义（须知晓）

只走 SPI = **检索质量与 chunking 口径由各宿主自定**，SDK 不保证跨系统一致。
代价是评测（`F7.4 评测回归`）无法跨系统横向比较检索效果；
收益是 SDK 保持零依赖（`intent-sdk-core` 仍只有 Jackson），符合 `架构设计方案.md:96` 的依赖约束。
**建议补一份 `KnowledgeRetriever` 契约文档**（chunk 粒度、`score` 语义与量纲、`meta` 必填键、
引用回传格式），否则 SPI 会退化成"各写各的"。


---

## 附录 A：关键文件索引

### pi 原版（TS）
| 能力 | 文件 |
|---|---|
| 工具契约 | `packages/agent/src/types.ts:361-410` |
| 工具循环 | `packages/agent/src/agent-loop.ts`（钩子 598-756，并行 487-552，截断 379-404） |
| 技能（harness） | `packages/agent/src/harness/skills.ts` |
| 技能（应用） | `packages/coding-agent/src/core/skills.ts` |
| 技能文档 | `packages/coding-agent/docs/skills.md` |
| 系统提示词 | `packages/coding-agent/src/core/system-prompt.ts:28-169` |
| 资源装载 | `packages/coding-agent/src/core/resource-loader.ts` |
| 扩展 API | `packages/coding-agent/src/core/extensions/types.ts:1252-1500` |
| 多智能体（示例） | `packages/coding-agent/examples/extensions/subagent/index.ts` |

### pi-java
| 能力 | 文件 |
|---|---|
| 工具契约 | `pi-agent/src/main/java/dev/pi/agent/AgentTool.java` |
| 循环与钩子 | `pi-agent/src/main/java/dev/pi/agent/AgentLoop.java`、`AgentLoopConfig.java` |
| Agent 门面（钩子未透传） | `pi-agent/src/main/java/dev/pi/agent/Agent.java:203-228` |
| 技能加载 | `pi-agent/src/main/java/dev/pi/agent/harness/Skills.java`、`SkillPrompts.java`、`Skill.java` |
| 提示词模板 | `pi-agent/src/main/java/dev/pi/agent/harness/PromptTemplates.java` |
| AgentHarness | `pi-agent/src/main/java/dev/pi/agent/harness/AgentHarness.java` |
| 系统提示词（未接技能） | `pi-coding-agent/src/main/java/dev/pi/codingagent/SystemPrompt.java` |
| 扩展 API | `pi-coding-agent/src/main/java/dev/pi/codingagent/ext/ExtensionApi.java`、`ExtensionEvent.java` |
| 多智能体 | `pi-plugins/pi-subagents/src/main/java/dev/pi/subagents/` |

### next-agent
| 能力 | 文件 |
|---|---|
| 意图规范 | `intent-protocol/src/main/java/dev/intent/protocol/IntentSpec.java` |
| 运行时编排 | `intent-sdk-pi/src/main/java/dev/intent/sdk/pi/IntentRuntime.java` |
| agent 执行器 | `intent-sdk-pi/src/main/java/dev/intent/sdk/pi/AgentExecutor.java` |
| workflow 执行器 | `intent-sdk-pi/src/main/java/dev/intent/sdk/pi/SkillExecutor.java` |
| flow 执行器 | `intent-sdk-pi/src/main/java/dev/intent/sdk/pi/FlowExecutor.java` |
| 执行器档案 | `intent-sdk-core/src/main/java/dev/intent/sdk/executor/ExecutorProfile.java` |
| 档案校验/构建 | `.../executor/ExecutorProfileValidator.java`、`intent-sdk-pi/.../ExecutorProfiles.java` |
| 知识检索 SPI | `intent-sdk-core/src/main/java/dev/intent/sdk/executor/KnowledgeRetriever.java` |
| 工具 SPI | `intent-sdk-core/src/main/java/dev/intent/sdk/tool/IntentTool.java`、`HostToolRegistry.java` |
| pi 适配器 | `intent-sdk-pi/src/main/java/dev/intent/sdk/pi/PiToolAdapter.java` |

## 附录 B：next-agent 实际模块边界（文档与仓库的差异）

父 pom 只有 6 个模块（`pom.xml:37-42`）：`intent-protocol` / `intent-sdk-core` / `intent-sdk-pi` /
`intent-sdk-host` / `intent-spring-boot-starter` / `intent-sdk-gateway`。

**文档引用但本仓库不存在**：`intent-gateway-server`（网关服务端）、demo 宿主（模拟 CRM）、
`intent-ui-sdk`（前端组件）、`yudao-module-intent`（参考实现 + REST 端点）。
→ 判断任何"能力有没有"时，**不能只看文档**。

## 附录 C：检索证据（"不存在"结论的依据）

- pi 本体/知识库：对 `pi/packages/**/*.ts` 检索
  `ontology|knowledge|graph.?rag|vector|embedding|retriev|semantic` → 48 处命中**全部无关**
  （`undo-stack.ts`、CBOR `knownVectors`、OSC 133 "semantic prompt"、recraft "*vector*" 图片模型、注释里的 "semantics"）。
- pi-java 本体/知识库：对 `pi-java/**/*.java` 检索同类词 → **NOT PRESENT**。
- next-agent 本体：对 `next-agent` 检索 `本体|ontology|知识图谱|knowledge graph` → 命中项全是
  中文"实体"一词的普通用法（页面上下文实体、实体下拉候选），**无本体实现**。
- next-agent 知识库：`KnowledgeRetriever` 全仓仅 5 处引用，其中唯一的**实现**在测试桩
  `intent-sdk-pi/src/test/java/dev/intent/sdk/pi/FlowExecutorTest.java:37`（`StubRetriever`）。
- next-agent 技能：`ExecutorProfile.skills` 在 `ExecutorProfiles.java:75-77` 被显式拒绝：
  `"技能组合附件尚未实现，skills 声明暂不可用（规划中）"`。
