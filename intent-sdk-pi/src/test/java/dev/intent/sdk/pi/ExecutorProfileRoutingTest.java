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
import dev.intent.sdk.llm.LlmConfig;
import dev.intent.sdk.tool.HostToolRegistry;
import dev.intent.sdk.tool.LambdaHostTool;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 档案化执行器接入运行时：按档案 id 路由、执行器级工具白名单上限、
 * 内置执行器并存。
 */
class ExecutorProfileRoutingTest {

    /** 离线端点（连接立即拒绝）：只有触网的用例才会真正走到模型调用。 */
    private static final LlmConfig OFFLINE_LLM =
            LlmConfig.openAiCompatible("http://127.0.0.1:1", "test-key", "test-model");

    private static HostToolRegistry hostTools() {
        return new HostToolRegistry()
                .register(LambdaHostTool.ofText("crm_get_customer", "查询客户", Map.of(), (args, s) -> "ok"))
                .register(LambdaHostTool.ofText("crm_send_sms", "发送短信", Map.of(), (args, s) -> "ok"));
    }

    private static List<IntentExecutor> builtExecutors(HostToolRegistry tools) {
        ExecutorProfile profile = new ExecutorProfile("sales-analyst", "agent", "销售分析师", null,
                OFFLINE_LLM, List.of("crm_get_customer"), List.of(), List.of(), null,
                new ExecutorProfile.Limits(12, 0), null, null);
        return ExecutorProfiles.build(List.of(profile), tools, List::of);
    }

    private static IntentSpec spec(String executor, List<String> tools) {
        IntentSpec.Builder builder = new IntentSpec.Builder()
                .id("crm.customer.analyze")
                .name("测试意图")
                .promptTemplate("测试模板");
        if (executor != null) {
            builder.executor(executor);
        }
        if (tools != null) {
            builder.tools(tools);
        }
        return builder.build();
    }

    private static IntentRuntime runtime(HostToolRegistry tools) {
        return new IntentRuntime(OFFLINE_LLM, tools, List::of, builtExecutors(tools), null, 4, 0);
    }

    @Test
    void 档案执行器按id路由且与内置执行器并存() {
        HostToolRegistry tools = hostTools();
        IntentRuntime runtime = runtime(tools);

        assertEquals(Set.of("builtin-agent", "sales-analyst"), Set.copyOf(runtime.executorIds()));

        IntentResult result = runtime.execute(spec("sales-analyst", null), Map.of(), Map.of(), null, null);
        // 离线端点：工具白名单解析通过 → 真实发起模型调用失败，证明路由到达了档案构建的执行器
        assertEquals(IntentStatus.FAILED, result.status());
        assertNotNull(result.error());
        assertTrue(Set.of(IntentErrorCodes.LLM_ERROR, IntentErrorCodes.OUTPUT_INVALID)
                .contains(result.error().code()));
    }

    @Test
    void 意图工具越界执行器白名单报配置错误() {
        IntentRuntime runtime = runtime(hostTools());

        IntentResult result = runtime.execute(spec("sales-analyst", List.of("crm_send_sms")),
                Map.of(), Map.of(), null, null);

        assertEquals(IntentStatus.FAILED, result.status());
        assertEquals(IntentErrorCodes.VALIDATION_ERROR, result.error().code());
        assertTrue(result.error().message().contains("白名单"));
    }

    @Test
    void 意图工具在执行器白名单内通过解析() {
        IntentRuntime runtime = runtime(hostTools());

        IntentResult result = runtime.execute(spec("sales-analyst", List.of("crm_get_customer")),
                Map.of(), Map.of(), null, null);

        assertEquals(IntentStatus.FAILED, result.status());
        assertTrue(Set.of(IntentErrorCodes.LLM_ERROR, IntentErrorCodes.OUTPUT_INVALID)
                .contains(result.error().code()));
    }
}
