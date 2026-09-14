package dev.intent.sdk.pi;

import dev.intent.sdk.executor.ExecutionOutcome;
import dev.intent.sdk.executor.ExecutionRequest;
import dev.intent.sdk.executor.ExecutorNotFoundException;
import dev.intent.sdk.executor.ExecutorProfile;
import dev.intent.sdk.executor.ExecutorProfileException;
import dev.intent.sdk.executor.ExecutorProfileValidator;
import dev.intent.sdk.executor.IntentExecutor;
import dev.intent.sdk.executor.KnowledgeRetriever;

import dev.intent.protocol.IntentSpec;
import dev.intent.sdk.executor.IntentExecutor;
import dev.intent.sdk.gateway.GatewayClient;
import dev.intent.sdk.gateway.NoopGatewayClient;
import dev.intent.sdk.llm.LlmConfig;
import dev.intent.sdk.store.JsonlTraceStore;
import dev.intent.sdk.store.TraceStore;
import dev.intent.sdk.tool.HostToolRegistry;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 意图执行引擎配置。推荐经 {@link IntentRuntime#create(IntentRuntimeConfig)} 装配运行时。
 *
 * <p>装配示例：</p>
 * <pre>{@code
 * IntentRuntime runtime = IntentRuntime.create(IntentRuntimeConfig.builder(llmConfig)
 *         .tools(hostTools)
 *         .traceDir(Path.of("data/intent-traces"))
 *         .customExecutors(List.of(new RagExecutor(...)))
 *         .build());
 * }</pre>
 */
public final class IntentRuntimeConfig {

    private final LlmConfig llm;
    private final HostToolRegistry tools;
    private final TraceStore traceStore;
    private final GatewayClient gateway;
    private final int maxTurns;
    private final int outputMaxRetries;
    private final Path traceDir;
    /** 意图目录供给者：供执行引擎在提示词中注入"可推荐的下一步意图"目录（可空）。 */
    private final Supplier<List<IntentSpec>> specCatalog;
    /** 自定义执行器：意图规范通过 executor: &lt;id&gt; 引用，与内置 builtin-agent 并存。 */
    private final List<IntentExecutor> customExecutors;

    private IntentRuntimeConfig(Builder b) {
        this.llm = Objects.requireNonNull(b.llm, "llm 配置必填");
        this.tools = b.tools == null ? new HostToolRegistry() : b.tools;
        this.traceStore = b.traceStore;
        this.gateway = b.gateway == null ? new NoopGatewayClient() : b.gateway;
        this.maxTurns = b.maxTurns;
        this.outputMaxRetries = b.outputMaxRetries;
        this.traceDir = b.traceDir;
        this.specCatalog = b.specCatalog;
        this.customExecutors = b.customExecutors == null ? List.of() : List.copyOf(b.customExecutors);
    }

    public static Builder builder(LlmConfig llm) {
        return new Builder(llm);
    }

    public LlmConfig llm() { return llm; }
    public HostToolRegistry tools() { return tools; }
    public TraceStore traceStore() { return traceStore; }
    public GatewayClient gateway() { return gateway; }
    public int maxTurns() { return maxTurns; }
    public int outputMaxRetries() { return outputMaxRetries; }
    public Path traceDir() { return traceDir; }
    public Supplier<List<IntentSpec>> specCatalog() { return specCatalog; }
    public List<IntentExecutor> customExecutors() { return customExecutors; }

    public static final class Builder {
        private final LlmConfig llm;
        private HostToolRegistry tools;
        private TraceStore traceStore;
        private GatewayClient gateway;
        private int maxTurns = 12;
        private int outputMaxRetries = 1;
        private Path traceDir;
        private Supplier<List<IntentSpec>> specCatalog;
        private List<IntentExecutor> customExecutors;

        private Builder(LlmConfig llm) {
            this.llm = llm;
        }

        /** 宿主工具注册表。 */
        public Builder tools(HostToolRegistry v) { this.tools = v; return this; }
        /** 留痕存储（优先于 traceDir；不设置且无 traceDir = 不落盘）。 */
        public Builder traceStore(TraceStore v) { this.traceStore = v; return this; }
        /** 网关客户端（缺省 Noop = 未装配）。 */
        public Builder gateway(GatewayClient v) { this.gateway = v; return this; }
        /** 单次执行最大推理轮数（防失控）。 */
        public Builder maxTurns(int v) { this.maxTurns = v; return this; }
        /** 输出不合规时的整体重试次数。 */
        public Builder outputMaxRetries(int v) { this.outputMaxRetries = v; return this; }
        /** JSONL 留痕目录（设置 traceStore 时忽略）。 */
        public Builder traceDir(Path v) { this.traceDir = v; return this; }
        /** 意图目录供给者（供提示词注入"可推荐的下一步意图"）。 */
        public Builder specCatalog(Supplier<List<IntentSpec>> v) { this.specCatalog = v; return this; }
        /** 自定义执行器（意图规范通过 executor: &lt;id&gt; 引用；id 不可为内置 builtin-agent）。 */
        public Builder customExecutors(List<IntentExecutor> v) { this.customExecutors = v; return this; }

        public IntentRuntimeConfig build() {
            if (this.traceStore == null && this.traceDir != null) {
                this.traceStore = new JsonlTraceStore(this.traceDir);
            }
            return new IntentRuntimeConfig(this);
        }
    }
}
