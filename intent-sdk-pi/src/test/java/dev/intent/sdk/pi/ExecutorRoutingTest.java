package dev.intent.sdk.pi;

import dev.intent.sdk.executor.ExecutionOutcome;
import dev.intent.sdk.executor.ExecutionRequest;
import dev.intent.sdk.executor.ExecutorProfile;
import dev.intent.sdk.executor.ExecutorProfileException;
import dev.intent.sdk.executor.ExecutorProfileLoader;
import dev.intent.sdk.executor.ExecutorProfileValidator;
import dev.intent.sdk.executor.IntentExecutor;
import dev.intent.sdk.executor.KnowledgeRetriever;

import dev.intent.protocol.IntentErrorCodes;
import dev.intent.protocol.IntentResult;
import dev.intent.protocol.IntentSpec;
import dev.intent.protocol.IntentStatus;
import dev.intent.protocol.StepTrace;
import dev.intent.protocol.UsageInfo;
import dev.intent.sdk.executor.ExecutionOutcome;
import dev.intent.sdk.executor.ExecutionRequest;
import dev.intent.sdk.executor.IntentExecutor;
import dev.intent.sdk.llm.LlmConfig;
import dev.intent.sdk.store.InMemoryTraceStore;
import dev.intent.sdk.store.JsonlTraceStore;
import dev.intent.sdk.tool.HostToolRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 执行器路由与留痕：缺省执行器、具名执行器、未注册执行器（EXECUTOR_NOT_FOUND）、
 * TraceStore 落盘、create(config) 装配入口。
 */
class ExecutorRoutingTest {

    /** 离线端点（连接立即拒绝）：只有触网的用例才会真正走到模型调用。 */
    private static final LlmConfig OFFLINE_LLM =
            LlmConfig.openAiCompatible("http://127.0.0.1:1", "test-key", "test-model");

    /** 桩执行器：记录请求并返回成功输出（不触网）。 */
    static final class StubExecutor implements IntentExecutor {
        private final String id;
        final List<ExecutionRequest> requests = new ArrayList<>();

        StubExecutor(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public ExecutionOutcome run(ExecutionRequest request) {
            requests.add(request);
            return ExecutionOutcome.success(Map.of("ok", true),
                    List.of(StepTrace.llm("stub 执行", 0)), UsageInfo.ZERO);
        }
    }

    private static IntentSpec spec(String id, String executor) {
        IntentSpec.Builder builder = new IntentSpec.Builder()
                .id(id)
                .name("测试意图")
                .promptTemplate("测试模板");
        if (executor != null) {
            builder.executor(executor);
        }
        return builder.build();
    }

    @Test
    void 具名执行器被路由并留痕() {
        InMemoryTraceStore traceStore = new InMemoryTraceStore();
        StubExecutor stub = new StubExecutor("sales-analyst");
        IntentRuntime runtime = new IntentRuntime(OFFLINE_LLM, new HostToolRegistry(),
                List::of, List.of(stub), null, traceStore, 4, 1);

        IntentResult result = runtime.execute(spec("crm.customer.analyze", "sales-analyst"),
                Map.of(), Map.of(), null, null);

        assertEquals(IntentStatus.SUCCESS, result.status());
        assertEquals(Map.of("ok", true), result.output());
        assertEquals(1, stub.requests.size());
        assertEquals(Set.of("builtin-agent", "sales-analyst"), Set.copyOf(runtime.executorIds()));

        var trace = traceStore.get(result.traceId());
        assertTrue(trace.isPresent());
        assertEquals("crm.customer.analyze", trace.get().intentId());
        assertEquals(IntentStatus.SUCCESS, trace.get().status());
        assertFalse(trace.get().steps().isEmpty());
    }

    @Test
    void 缺省路由到内置执行器并因离线端点失败() {
        // 离线端点连接拒绝 → 内置执行器真实发起模型调用后失败；
        // 借此证明路由到达了 builtin-agent（而非 EXECUTOR_NOT_FOUND，也未被静默换道）
        StubExecutor stub = new StubExecutor("sales-analyst");
        IntentRuntime runtime = new IntentRuntime(OFFLINE_LLM, new HostToolRegistry(),
                List::of, List.of(stub), null, 4, 0);

        IntentResult result = runtime.execute(spec("crm.customer.analyze", null),
                Map.of(), Map.of(), null, null);

        assertEquals(IntentStatus.FAILED, result.status());
        assertNotNull(result.error());
        assertTrue(stub.requests.isEmpty());
        assertNotEquals(IntentErrorCodes.EXECUTOR_NOT_FOUND, result.error().code());
        assertTrue(Set.of(IntentErrorCodes.LLM_ERROR, IntentErrorCodes.OUTPUT_INVALID)
                .contains(result.error().code()));
    }

    @Test
    void 未注册执行器报EXECUTOR_NOT_FOUND且留痕() {
        InMemoryTraceStore traceStore = new InMemoryTraceStore();
        IntentRuntime runtime = new IntentRuntime(OFFLINE_LLM, new HostToolRegistry(),
                List::of, List.of(), null, traceStore, 4, 1);

        IntentResult result = runtime.execute(spec("crm.customer.analyze", "ghost-executor"),
                Map.of(), Map.of(), null, null);

        assertEquals(IntentStatus.FAILED, result.status());
        assertEquals(IntentErrorCodes.EXECUTOR_NOT_FOUND, result.error().code());
        assertTrue(result.error().message().contains("ghost-executor"));

        var trace = traceStore.get(result.traceId());
        assertTrue(trace.isPresent());
        assertEquals(IntentStatus.FAILED, trace.get().status());
        assertEquals(IntentErrorCodes.EXECUTOR_NOT_FOUND, trace.get().error().code());
    }

    @Test
    void 自定义执行器不可覆盖内置id() {
        StubExecutor impostor = new StubExecutor("builtin-agent");
        assertThrows(IllegalArgumentException.class, () -> new IntentRuntime(OFFLINE_LLM,
                new HostToolRegistry(), List::of, List.of(impostor), null, 4, 1));
    }

    @Test
    void 运行期热更新执行器() {
        InMemoryTraceStore traceStore = new InMemoryTraceStore();
        IntentRuntime runtime = new IntentRuntime(OFFLINE_LLM, new HostToolRegistry(),
                List::of, List.of(), null, traceStore, 4, 1);
        runtime.execute(spec("crm.customer.analyze", "late-executor"), Map.of(), Map.of(), null, null);

        // 注册后路由生效
        StubExecutor late = new StubExecutor("late-executor");
        runtime.registerExecutor(late);
        IntentResult ok = runtime.execute(spec("crm.customer.analyze", "late-executor"),
                Map.of(), Map.of(), null, null);
        assertEquals(IntentStatus.SUCCESS, ok.status());
        assertEquals(1, late.requests.size());

        // 替换同 id 执行器 → 新实例接管
        StubExecutor replacement = new StubExecutor("late-executor");
        runtime.registerExecutor(replacement);
        runtime.execute(spec("crm.customer.analyze", "late-executor"), Map.of(), Map.of(), null, null);
        assertEquals(1, replacement.requests.size());

        // 注销后回到 EXECUTOR_NOT_FOUND；内置执行器不可注销/不可覆盖
        runtime.unregisterExecutor("late-executor");
        IntentResult gone = runtime.execute(spec("crm.customer.analyze", "late-executor"),
                Map.of(), Map.of(), null, null);
        assertEquals(IntentErrorCodes.EXECUTOR_NOT_FOUND, gone.error().code());
        runtime.unregisterExecutor("builtin-agent");
        assertThrows(IllegalArgumentException.class,
                () -> runtime.registerExecutor(new StubExecutor("builtin-agent")));
        assertTrue(runtime.executorIds().contains("builtin-agent"));
    }

    @Test
    void NEED_INPUT失败路径同样留痕() {
        InMemoryTraceStore traceStore = new InMemoryTraceStore();
        IntentRuntime runtime = new IntentRuntime(OFFLINE_LLM, new HostToolRegistry(),
                List::of, List.of(), null, traceStore, 4, 1);
        Map<String, Object> paramsSchema = Map.of(
                "type", "object",
                "properties", Map.of("customerId", Map.of("type", "string")),
                "required", List.of("customerId"));

        IntentResult result = runtime.execute(
                new IntentSpec.Builder().id("crm.customer.analyze").name("测试意图")
                        .promptTemplate("测试模板").paramsSchema(paramsSchema).build(),
                Map.of(), Map.of(), null, null);

        assertEquals(IntentStatus.NEED_INPUT, result.status());
        var trace = traceStore.get(result.traceId());
        assertTrue(trace.isPresent());
        assertEquals(IntentStatus.NEED_INPUT, trace.get().status());
    }

    @Test
    void create工厂按配置装配执行器与JSONL留痕(@TempDir Path dir) {
        StubExecutor stub = new StubExecutor("sales-analyst");
        IntentRuntimeConfig config = IntentRuntimeConfig.builder(OFFLINE_LLM)
                .traceDir(dir)
                .customExecutors(List.of(stub))
                .maxTurns(4)
                .outputMaxRetries(0)
                .build();

        assertNotNull(config.traceStore());
        IntentRuntime runtime = IntentRuntime.create(config);
        IntentResult result = runtime.execute(spec("crm.customer.analyze", "sales-analyst"),
                Map.of(), Map.of(), null, null);

        assertEquals(IntentStatus.SUCCESS, result.status());
        JsonlTraceStore store = (JsonlTraceStore) config.traceStore();
        var records = store.list(null, 10);
        assertEquals(1, records.size());
        assertEquals(result.traceId(), records.get(0).traceId());
    }
}
