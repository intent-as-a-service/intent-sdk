# 面向"业务 SDK 内嵌"的 pi-java 落地方案（含 ThreadLocal 纪律）

> 触发这次修订的初衷（用户原话）：
> "**核心是想做业务系统的 SDK 调用，避开在权限、安全等地方要重来一次，
> 依托已有的业务 SDK 能力完成 AI 的工具调用。**"
>
> 这个约束改变了优先级判断。本文是对
> `pi-java-能力差距分析.md`（能力差距）与 `pi-go-集成可行性评估.md`（内核选型）
> 的**修订与收敛**——不是推翻，是收窄。

---

## 0. 三条结论

1. **你的初衷直接支持"进程内 + Java 内核"这条路，而且比之前论证得更强。**
   因为 yudao 的权限与安全是 **ThreadLocal 承载的环境态**（`SecurityContextHolder` 用
   **TransmittableThreadLocal**、`TenantContextHolder`、`DataPermissionContextHolder`），
   环境态**无法跨进程传递**——一律跨进程，就必须重建一套，正是你要避开的事。

2. **当前 `AgentExecutor` 的 `SEQUENTIAL` 恰好是唯一安全的配置，这不是巧合而是必须。**
   但 `pi-java` 的并行分支用的是 `CompletableFuture.supplyAsync()`（默认 ForkJoinPool），
   **TTL 不会生效** → 一旦有人为了性能改成并行，登录态/租户/数据权限**全部丢失**，
   甚至 `TenantDatabaseInterceptor` 直接抛 `NullPointerException`。
   **必须把它固化成纪律，并在代码层拦住。**

3. **知识库不用自建——`ruoyi-office` 里已经有一整套完整 RAG。**
   我上一轮建议"只走 SPI"时隐含了"宿主得自己实现一套检索"的假设。
   **这个假设是错的。** yudao 的 `AiKnowledgeSegmentService.searchKnowledgeSegment()`
   已经是"向量检索 + DashScope rerank + 按知识库过滤 + topK/阈值"的成品。
   你的 `KnowledgeRetriever` 实现只需调用它，**约 10 行代码，0.5~1 人日**。

---

## 1. 为什么"进程内"是硬约束（而不是性能偏好）

### 1.1 yudao 的上下文是 ThreadLocal 承载的

| 上下文 | 实现 | 承载机制 | 证据 |
|---|---|---|---|
| 登录用户 / 权限 | `SecurityContextHolder` | **TransmittableThreadLocal** | `YudaoSecurityAutoConfiguration.java:83-90` 通过 `MethodInvokingFactoryBean` 把策略设为 `TransmittableThreadLocalSecurityContextHolderStrategy`；实现类 `TransmittableThreadLocalSecurityContextHolderStrategy.java:15` |
| 租户 | `TenantContextHolder` | ThreadLocal | `TenantContextHolder.java:12`；被 `TenantDatabaseInterceptor.java:42,48` 消费 |
| 数据权限 | `DataPermissionContextHolder` | ThreadLocal | `TenantDatabaseInterceptor` 同族机制 |

关键推论：**TTL 只在同进程内、且经过 `TtlRunnable`/TTL 执行器包装时才能传递上下文。**
跨进程（HTTP/RPC/子进程）传递的是**值**，而这个体系依赖的是**环境**：

- `@PreAuthorize("@ss.hasPermission(...)")` 读 `SecurityContextHolder` → 跨进程后为空 → **403 或 NPE**
- `TenantDatabaseInterceptor` 读 `TenantContextHolder.getRequiredTenantId()`
  （`TenantContextHolder.java:41` 明确 `throw new NullPointerException("TenantContextHolder 不存在租户编号！")`）
  → 跨进程后**直接抛异常**
- `@Transactional` 绑定 `TransactionSynchronizationManager`（也是 ThreadLocal）
  → 跨进程后工具在**新事务**里执行 → 失去"意图执行 + 业务写入同一事务"的原子性

### 1.2 所以：跨进程 = 重做权限安全 = 违背初衷

跨进程方案能传**值**（`AiUtils.buildCommonToolContext()` 就是这个思路：
把 `LOGIN_USER`、`TENANT_ID` 装进一个 Map 交给工具，见 `AiUtils.java:104-109`），
但**恢复不了环境**：工具在新线程上跑，`SecurityContextHolder` 仍是空的。

> 注意：yudao 现有的 `buildCommonToolContext()` 只能解决"给工具传登录人是谁"，
> 解决不了"工具调用的 Service 在 `@PreAuthorize` 和租户拦截器下能否通过"。
> 这正是"权限要重来一次"的具体形态。

**结论**：`pi-java`（同 JVM、同语言、同调用栈）不是"可选优化"，
而是**唯一能保住环境态的形态**。内核选型问题到此收敛。

---

## 2. ⚠️ 当前实现里最需要立刻处理的一件事：并行 = 丢用户上下文

### 2.1 事实

`pi-java` 的两条工具执行路径（`AgentLoop.java:312-387`）：

```java
// SEQUENTIAL（当前 AgentExecutor 的配置）—— 在调用线程上直接执行
for (ToolCall toolCall : toolCalls) {
    FinalizedToolCall finalized = executeAndFinalize(...);   // 同步，同线程
}

// PARALLEL —— 派发到 ForkJoinPool.commonPool()
futures.add(CompletableFuture.supplyAsync(() -> {
    FinalizedToolCall finalized = executeAndFinalize(...);   // 换了线程！
    ...
}));
```

`AgentExecutor.java:163` 现在设的是：
```java
config.toolExecution = AgentLoopConfig.ToolExecution.SEQUENTIAL;
```

### 2.2 风险

| 模式 | 工具执行线程 | ThreadLocal / TTL / 事务 | 结论 |
|---|---|---|---|
| `SEQUENTIAL`（当前） | **业务 HTTP 请求线程** | 全部继承，`@PreAuthorize`/租户/数据权限/事务**自动生效** | ✅ 安全，符合初衷 |
| `PARALLEL` | `ForkJoinPool.commonPool()` | **全部丢失**（TTL 未包装这个池）；租户缺失时**抛 NPE** | ❌ 会造成线上事故 |

而我在 `pi-java-能力差距分析.md` 的 A6 里建议过
"`toolExecution` 改 PARALLEL（高危工具标 `sequential`）"——
**那条建议在你的场景下是错的，现予撤销。**

### 2.3 建议做法（按优先级）

1. **保持 `SEQUENTIAL`**，并把它从"默认值"升级为**显式不变量**。
2. **加护栏**：`ExecutorProfile` 与 `IntentSpec` 都不暴露并行开关；
   或在 `AgentExecutor` 里断言 `toolExecution == SEQUENTIAL`，配错就启动失败。
3. **不要用 `CompletableFuture.supplyAsync()` 换线程**。若确实要并行（如三个独立只读查询），
   必须在业务侧用 TTL 包装的执行器（`TtlExecutors.getTtlExecutorService(...)`），
   且只对**明确声明为"无上下文依赖"的只读工具**开放——这需要 `IntentTool` 增加声明位，
   属于后续增强，**不要现在做**。
4. **工具线程名带 traceId**（`intent-tool-<traceId>`），排障时能从线程转储直接定位到意图。

---

## 3. 工具执行处的 ThreadLocal 纪律（本次最核心的落地内容）

即便是 `SEQUENTIAL`，也建议在**工具执行边界**做一次显式的上下文"进入/退出"。
原因不是现在会丢，而是**防未来**：一旦有人加了异步、加了线程池、加了 `@Async`，
环境态就静默丢失，且极难排查。显式化能把它变成编译期/运行期可见的约束。

### 3.1 落点：`PiToolAdapter`

`PiToolAdapter.java:61-67` 是工具执行的唯一收口处（`IntentTool` → `AgentTool` 的唯一转换点）：

```java
@Override
public AgentToolResult execute(String toolCallId, Map<String, Object> args, AbortSignal signal)
        throws Exception {
    IntentToolResult result = delegate.execute(toolCallId, args, context(signal));
    return new AgentToolResult(List.of(new TextContent(textOf(result))), result.data(),
            null, null, result.terminate());
}
```

### 3.2 建议形态（宿主侧实现，SDK 只给 SPI）

**SDK 侧**（`intent-sdk-core`，不依赖 Spring）——定义一个环境桥：

```java
/** 工具执行环境的进入/退出（宿主实现；SDK 只调用，不感知具体框架）。 */
public interface IntentToolEnvironment {
    /** 进入工具执行：恢复宿主环境态。返回一个"退出"动作，必须 finally 调用。 */
    AutoCloseable enter(IntentToolContext ctx);

    /** 无宿主环境时的空实现（SDK 自带，保证 intent-sdk-core 零依赖可用）。 */
    static IntentToolEnvironment noop() { ... }
}
```

**宿主侧**（`yudao-module-intent`，实现上面的桥）：

```java
public AutoCloseable enter(IntentToolContext ctx) {
    // 1. 保存现场，便于嵌套调用与异常路径恢复
    SecurityContext previousSecurity = SecurityContextHolder.getContext();
    Long previousTenant = TenantContextHolder.getTenantId();
    Boolean previousIgnore = TenantContextHolder.isIgnore();

    // 2. 恢复环境态（值来自 ExecutionRequest 携带的登录人/租户）
    SecurityContextHolder.setContext(buildSecurityContext(ctx.loginUser()));
    TenantContextHolder.setTenantId(ctx.tenantId());

    // 3. 退出时严格还原（finally 语义）
    return () -> {
        SecurityContextHolder.setContext(previousSecurity);
        TenantContextHolder.setTenantId(previousTenant);
        TenantContextHolder.setIgnore(previousIgnore);
    };
}
```

**`PiToolAdapter` 里包一层**：

```java
@Override
public AgentToolResult execute(String toolCallId, Map<String, Object> args, AbortSignal signal)
        throws Exception {
    try (AutoCloseable scope = environment.enter(context(signal))) {
        IntentToolResult result = delegate.execute(toolCallId, args, context(signal));
        return new AgentToolResult(List.of(new TextContent(textOf(result))), result.data(),
                null, null, result.terminate());
    }
}
```

### 3.3 为什么这样就"不重做权限安全"

因为进入工具后，**后续所有代码路径都是宿主原有的**：

```
工具方法体
  → 宿主 Service
      → @PreAuthorize 读 SecurityContextHolder          ✅ 生效
      → Mapper 被 TenantDatabaseInterceptor 加 tenant_id  ✅ 生效
      → 数据权限规则拼 SQL 条件                          ✅ 生效
      → @Transactional 加入当前事务                       ✅ 生效（同线程）
```

SDK 一行权限代码都不用写，也不需要知道 yudao 的存在。

### 3.4 事务注意

同线程只保证**能加入**当前事务，但工具方法自己要有 `@Transactional` 语义决策：

- 只读工具：`@Transactional(propagation = REQUIRES_NEW, readOnly = true)` 或干脆不开事务，
  避免长事务把写锁拖住（LLM 推理可能几十秒，**不要把 LLM 调用放在事务里**）；
- 写工具：`REQUIRED`，跟随意图执行的事务；
- **原则：LLM 调用绝不在事务边界内。** 事务只包工具执行那一刻。

---

## 4. 知识库：直接复用 yudao 现有 RAG（推翻我上一轮的建议）

### 4.1 yudao 已有的完整能力

| 组件 | 类 | 说明 |
|---|---|---|
| 知识库 | `AiKnowledgeDO` / `AiKnowledgeService` | 知识库元数据，含 `topK`、`similarityThreshold`、`embeddingModelId` |
| 文档 | `AiKnowledgeDocumentDO` / `AiKnowledgeDocumentService` | 文档管理、URL 读取、切片 |
| 分段 + 向量 | `AiKnowledgeSegmentDO` / `AiKnowledgeSegmentServiceImpl` | `writeVectorStore` / `deleteVectorStore` / `reindexKnowledgeSegmentByKnowledgeId` |
| **检索** | `AiKnowledgeSegmentService.searchKnowledgeSegment(AiKnowledgeSegmentSearchReqBO)` | **向量检索 + rerank + 知识库过滤 + topK/阈值**，是实现 `KnowledgeRetriever` 的**唯一入口** |
| Embedding 模型 | `AiModelFactoryImpl.getOrCreateEmbeddingModel` (`:291-311`) | 通义 / 言域 / 智谱 / MiniMax / OpenAI / Azure OpenAI / Ollama |
| **Rerank** | `searchDocument` (`AiKnowledgeSegmentServiceImpl.java:268-295`) | DashScope `RerankModel`；未配 rerank 时退化为纯向量 + 阈值 |
| 向量库 | `AiModelFactoryImpl.getOrCreateVectorStore` (`:316-331`) | `SimpleVectorStore`（默认启用）/ Qdrant / Redis / Milvus（均已实现，切换靠注释） |
| 聊天集成 | `AiChatMessageServiceImpl` | 已经注入 `AiKnowledgeSegmentService`，说明这条链路是被使用的 |

检索实现细节（`AiKnowledgeSegmentServiceImpl.java:268-295`）：
```java
Integer topK = ObjUtil.defaultIfNull(reqBO.getTopK(), knowledge.getTopK());
Double similarityThreshold = ObjUtil.defaultIfNull(reqBO.getSimilarityThreshold(), knowledge.getSimilarityThreshold());
int searchTopK = rerankModel != null ? topK * RERANK_RETRIEVAL_FACTOR : topK;
SearchRequest.builder().query(content).topK(searchTopK)
    .similarityThreshold(hasRerank ? ACCEPT_ALL : similarityThreshold)
    .filterExpression(eq(KNOWLEDGE_ID, reqBO.getKnowledgeId().toString()).build());
List<Document> documents = vectorStore.similaritySearch(...);
if (rerankModel != null) { /* DashScope rerank → topN, 再按阈值过滤 */ }
```

**即：分块、向量化、检索、重排、过滤、阈值——全部已有，且是按知识库独立配置的。**

### 4.2 SPI 实现（约 10 行）

```java
@Component
public class YudaoKnowledgeRetriever implements KnowledgeRetriever {

    @Resource
    private AiKnowledgeSegmentService segmentService;

    @Override
    public boolean supports(String kb) {
        // kb 即知识库编号（字符串形式），或做一层别名映射
        return kb != null && !kb.isBlank();
    }

    @Override
    public List<KnowledgeChunk> retrieve(String kb, String query, int topK) {
        AiKnowledgeSegmentSearchReqBO req = new AiKnowledgeSegmentSearchReqBO()
                .setKnowledgeId(Long.valueOf(kb))
                .setContent(query)
                .setTopK(topK);
        return segmentService.searchKnowledgeSegment(req).stream()
                .map(doc -> new KnowledgeChunk(doc.getContent(), doc.getScore(),
                        Map.of("knowledgeId", kb, "documentId", doc.getDocumentId())))
                .toList();
    }
}
```

> 字段名需按 `AiKnowledgeSegmentSearchRespBO` 实际定义核对（`service/knowledge/bo/` 下）。

**收益**：知识库能力从"待自建（中高工作量）"变成"**适配（0.5~1 人日）**"。
同时因为走的是宿主 Service，**租户隔离与权限自动生效**（同上 §3.3）。

### 4.3 于是路线里要改的

| 原编号 | 原计划 | 现计划 |
|---|---|---|
| C1 | 解禁 `ExecutorProfile.knowledge` 声明式附件 | **不变**（且现在更有价值——声明里直接填 yudao 知识库 ID） |
| C2 | 写 `KnowledgeRetriever` 契约文档 | **降级**：yudao 的 `SearchReqBO/RespBO` 已经是事实契约，文档只需说明"SDK 期望的 score 量纲与 meta 键" |
| C3 | 检索增强提示词纪律 | **不变**（SDK 侧） |
| C4 | 检索结果并入 trace | **不变** |
| — | ~~宿主自建检索~~ | **删除**（yudao 已有） |

### 4.4 顺带修正：其他"已有能力"清单

按同样的逻辑，接入前先确认 yudao/`ruoyi-office` 里**已经存在**的东西，不要重造：

- **知识库 / RAG** → 已存在（本节）
- **多模型接入**（通义/DeepSeek/智谱/MiniMax/Moonshot/OpenAI/Azure/Ollama/Gemini/Grok/百川/讯飞…）
  → `AiModelFactoryImpl` 已存在。**`intent-sdk-pi` 的 `LlmConnector` 是否要走它，需要决策**：
  走它 = 复用宿主的模型配置与 API Key 管理（符合初衷）；
  不走它 = 保持 SDK 独立但宿主要再配一份 Key。**建议走它**（用 `StreamFn` 注入点，
  `LlmConnector.streamFn()` 已经是唯一注入点，改造面很小）。
- **聊天/会话记录** → `AiChatConversationDO` / `AiChatMessageDO` 已存在。
  与 next-agent 的 `TraceStore` 有重叠，**留痕权威归口需要明确**（我上一份已列为待决项）。
- **工具定义管理** → `AiToolDO` / `AiToolController` 已存在（Spring AI 的 tool 注册）。
  与 `HostToolRegistry` 有重叠，需要决策谁注册。
- **工作流** → `AiWorkflowDO` / `AiWorkflowController` 已存在。与 `WorkflowExecutor` 有重叠。

> 这一条建议单独立项：**做一次"yudao 已有 AI 能力 ↔ next-agent 组件"的对照表**，
> 明确哪些是"复用"、哪些是"并行两套"、哪些要"以谁为准"。
> 不然后面会出现两套知识库、两套工具注册、两套留痕。

---

## 5. 修正后的落地顺序

| 阶段 | 内容 | 工作量 | 说明 |
|---|---|---|---|
| **P0** | 修既有缺陷 G1（`maxTurns` 不生效）、G2（REMOTE 静默本地执行）、G3（skill/flow 不校验工具白名单） | 1~2 人日 | 与初衷无关，但挡路 |
| **S1** | **ThreadLocal 纪律**：`IntentToolEnvironment` SPI + `PiToolAdapter` 包一层 + 工具上下文携带 `loginUser/tenantId` | **2~3 人日** | **本次核心**。做完即"不重做权限安全" |
| **S2** | **并行护栏**：固化 `SEQUENTIAL` 不变量 + 配置层拦截 + 工具线程名带 traceId | 0.5 人日 | 防未来事故 |
| **S3** | **知识库适配**：`YudaoKnowledgeRetriever`（调 `searchKnowledgeSegment`）+ 解禁 `ExecutorProfile.knowledge` | **1~1.5 人日** | 复用 yudao RAG |
| **S4** | **模型接入复用**：`LlmConnector` 走 `AiModelFactory`（决策后执行） | 1~2 人日 | 复用宿主模型配置 |
| **S5** | **模型/工具/留痕归口对照**：出对照表，定"以谁为准" | 1 人日 | 防两套并存 |
| **A** | 其余接线（`beforeToolCall` 挂权限校验与审计、`afterToolCall` 挂脱敏） | 2~3 人日 | `AgentExecutor` 已直接用 `AgentLoop`，**钩子现在就能用，不必先改 pi-java** |
| **A2** | 工具流式进度（`AgentTool` 四参 + `emit ToolExecutionUpdate`） | 1~2 人日 | **这一项必须改 pi-java** |
| **B** | 技能（B1 改名 → B2 加载 → B3 渐进披露） | 3~5 人日 | 按原计划 |
| **D** | 本体 | 暂缓 | 见 §6 |

**建议先做 P0 → S1 → S2 → S3，共约 5~7 人日，就能拿到"AI 工具调用完全复用宿主权限体系 + 知识库可用"的可演示闭环。**

---

## 6. 本体（在你这个场景下的具体价值）

有了 §3 的上下文继承，本体的价值变得**很具体**，而不是抽象的"知识图谱"：

1. **拦住模型编造的实体 ID**（最值钱）
   现在 `IntentSpec.paramsSchema` 里 `customerId` 只是 `{"type":"string"}`，
   模型可以传 `C-99999`，工具一路查到宿主，返回空或报错。
   本体可以让参数声明 `{"$entity":"crm.Customer"}`，**执行前用宿主实体校验器验一次**。
2. **知识库检索的实体过滤**
   yudao 的 `searchDocument` 已经支持 `FilterExpressionBuilder`
   （当前只按 `knowledgeId` 过滤）。本体可以再叠加 `objectType`/`objectId`
   元数据过滤 → 在客户详情页问"这个客户的合同风险"，只召回该客户的资料。
   **这是"本体 + 知识库"最有价值的一处结合，且改动面极小**（metadata 多写两个字段）。
3. **工具链自动推导**：`Customer --1:N--> Contract --1:N--> Payment`，
   编排层据此知道"查客户后能查合同"。

**建议**：D1（参数实体校验）与 D2（检索实体过滤）先做，**D3/D4 缓做**，且**不引入图数据库**。

---

## 7. 需要你拍板的三个点

| # | 决策点 | 选项 | 我的建议 |
|---|---|---|---|
| 1 | **模型接入是否走 yudao 的 `AiModelFactory`** | (a) 走（复用宿主模型配置与 Key 管理）<br>(b) 不走（SDK 独立配 Key） | **(a)**。符合"依托已有能力"的初衷，且 `LlmConnector.streamFn()` 已是唯一注入点，改造面小 |
| 2 | **工具注册归口** | (a) 用宿主 `AiToolDO` 统一管<br>(b) 保持 `HostToolRegistry` + Bean 注册 | **(b) 现阶段**，但 S5 对照后若发现 `AiToolDO` 已覆盖，再合并。理由是 `AiToolDO` 面向 Spring AI 的 tool 形态，与 `IntentTool` 契约不同 |
| 3 | **留痕权威** | (a) 以 next-agent `TraceStore` 为准<br>(b) 以宿主聊天/会话表为准 | **(a)**。意图留痕是执行审计（含参数/输出/错误/每步轨迹），语义比聊天记录强；宿主表只做展示 |

---

## 附录：本文引用的关键证据

| 结论 | 证据位置 |
|---|---|
| yudao 用 TTL 承载 SecurityContext | `ruoyi-office/.../security/config/YudaoSecurityAutoConfiguration.java:83-90`；`.../security/core/context/TransmittableThreadLocalSecurityContextHolderStrategy.java:15` |
| 租户缺失直接抛异常 | `.../tenant/core/context/TenantContextHolder.java:41` |
| 租户拦截器消费 ThreadLocal | `.../tenant/core/db/TenantDatabaseInterceptor.java:42,48` |
| yudao 已有"工具上下文"机制（但只传值） | `.../module/ai/util/AiUtils.java:33-34, 104-109` |
| yudao 已有完整知识库 | `.../module/ai/service/knowledge/AiKnowledgeSegmentService.java:131`（检索接口）、`AiKnowledgeSegmentServiceImpl.java:268-295`（向量+rerank+过滤） |
| yudao 已有 Embedding / VectorStore 工厂 | `.../ai/framework/ai/core/model/AiModelFactoryImpl.java:291-311`（embedding）、`:316-331`（vector store） |
| pi-java 串行 = 同线程 | `pi-java/pi-agent/src/main/java/dev/pi/agent/AgentLoop.java:331-351` |
| pi-java 并行 = `ForkJoinPool.commonPool()` | 同上 `:353-387`（`CompletableFuture.supplyAsync`） |
| 当前配置为串行 | `next-agent/intent-sdk-pi/src/main/java/dev/intent/sdk/pi/AgentExecutor.java:163` |
| 工具执行唯一收口 | `next-agent/intent-sdk-pi/src/main/java/dev/intent/sdk/pi/PiToolAdapter.java:61-67` |
