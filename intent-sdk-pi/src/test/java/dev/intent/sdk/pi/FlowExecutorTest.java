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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 流程型执行器（能力编排）：tool / knowledge 节点离线端到端、条件跳过与中止、
 * 知识检索 SPI 装配、agent 节点离线失败语义、编排校验。
 */
class FlowExecutorTest {

    private static final LlmConfig OFFLINE_LLM =
            LlmConfig.openAiCompatible("http://127.0.0.1:1", "test-key", "test-model");

    /** 桩检索器：负责所有知识库，返回固定内容。 */
    static final class StubRetriever implements KnowledgeRetriever {
        @Override
        public boolean supports(String kb) {
            return true;
        }

        @Override
        public List<KnowledgeRetriever.KnowledgeChunk> retrieve(String kb, String query, int topK) {
            return List.of(KnowledgeRetriever.KnowledgeChunk.of("产品手册要点：支持七天退货"));
        }
    }

    private static HostToolRegistry hostTools() {
        return new HostToolRegistry()
                .register(LambdaHostTool.ofText("crm_get_customer", "查询客户",
                        Map.of("customerId", Map.of("type", "string")),
                        (args, s) -> "客户: " + args.get("customerId")))
                .register(LambdaHostTool.ofData("crm_echo_details", "回显结构化数据",
                        Map.of(), (args, s) -> Map.of("level", "A")));
    }

    private static IntentResult execute(ExecutorProfile profile, Map<String, Object> params,
            List<KnowledgeRetriever> retrievers) {
        HostToolRegistry tools = hostTools();
        IntentRuntime runtime = new IntentRuntime(OFFLINE_LLM, tools, List::of,
                ExecutorProfiles.build(List.of(profile), tools, List::of, retrievers),
                null, null, 4, 0);
        IntentSpec spec = new IntentSpec.Builder()
                .id("crm.customer.analyze").name("客户分析")
                .promptTemplate("流程执行不需要提示词模板，但契约要求必填")
                .executor(profile.id())
                .build();
        return runtime.execute(spec, params, Map.of(), null, null);
    }

    @Test
    void 流程按序执行_条件跳过_知识检索_出参组装() {
        ExecutorProfile profile = new ExecutorProfile("flow-sop", "agent", "流程示例", null,
                null, List.of(), List.of(), List.of(), null, null,
                List.of(),
                Map.of(
                        "title", "客户报告",
                        "summary", "${nodes.n1.text}",
                        "blocks", "[{\"kind\":\"text\",\"text\":\"${nodes.n4.text}\"}]"),
                List.of(
                        new ExecutorProfile.FlowNode("n1", "tool", "crm_get_customer",
                                Map.of("customerId", "${params.customerId}"),
                                null, null, null, null, null, null),
                        new ExecutorProfile.FlowNode("n2", "tool", "crm_echo_details", null,
                                null, null, null, null, "true", null),
                        new ExecutorProfile.FlowNode("n3", "tool", "crm_echo_details", null,
                                null, null, null, null, "${params.skipMe}", null),
                        new ExecutorProfile.FlowNode("n4", "knowledge", null, null,
                                null, "产品手册", "退货政策", 3, null, null)));

        IntentResult result = execute(profile,
                Map.of("customerId", "C1001", "skipMe", "no"), List.of(new StubRetriever()));

        assertEquals(IntentStatus.SUCCESS, result.status());
        assertEquals("客户报告", result.output().get("title"));
        assertEquals("客户: C1001", result.output().get("summary"));
        assertTrue(String.valueOf(result.output().get("blocks")).contains("七天退货"));
    }

    @Test
    void 中止条件成立提前结束() {
        ExecutorProfile profile = new ExecutorProfile("flow-sop", "agent", "流程示例", null,
                null, List.of(), List.of(), List.of(), null, null,
                List.of(),
                Map.of(
                        "title", "报告",
                        "summary", "${nodes.n1.text}",
                        "blocks", "[{\"kind\":\"text\",\"text\":\"${nodes.n1.text}\"}]"),
                List.of(
                        new ExecutorProfile.FlowNode("n1", "tool", "crm_get_customer",
                                Map.of("customerId", "${params.customerId}"),
                                null, null, null, null, null, null),
                        new ExecutorProfile.FlowNode("n2", "tool", "crm_echo_details", null,
                                null, null, null, null, null, "${params.stop}")));

        IntentResult result = execute(profile,
                Map.of("customerId", "C1001", "stop", "yes"), List.of());

        assertEquals(IntentStatus.SUCCESS, result.status());
        assertEquals("客户: C1001", result.output().get("summary"));
        // n1 工具轨迹 + 中止条件成立轨迹
        assertEquals(2, result.steps().size());
    }

    @Test
    void knowledge节点无检索器明确报错() {
        ExecutorProfile profile = new ExecutorProfile("flow-sop", "agent", "流程示例", null,
                null, List.of(), List.of(), List.of(), null, null,
                List.of(), Map.of(),
                List.of(new ExecutorProfile.FlowNode("n1", "knowledge", null, null,
                        null, "产品手册", null, null, null, null)));

        IntentResult result = execute(profile, Map.of(), List.of());

        assertEquals(IntentStatus.FAILED, result.status());
        assertEquals(IntentErrorCodes.VALIDATION_ERROR, result.error().code());
        assertTrue(result.error().message().contains("检索器"));
    }

    @Test
    void agent节点离线失败报LLM_ERROR() {
        ExecutorProfile profile = new ExecutorProfile("flow-sop", "agent", "流程示例", null,
                OFFLINE_LLM, List.of(), List.of(), List.of(), null, null,
                List.of(), Map.of(),
                List.of(new ExecutorProfile.FlowNode("n1", "agent", null, null,
                        "分析客户 ${params.customerId}", null, null, null, null, null)));

        IntentResult result = execute(profile, Map.of("customerId", "C1001"), List.of());

        assertEquals(IntentStatus.FAILED, result.status());
        assertEquals(IntentErrorCodes.LLM_ERROR, result.error().code());
    }

    @Test
    void 纯变量出参不因内容形似JSON而被结构化() {
        // 工具返回 JSON 文本（宿主 ofData 工具的常态）：summary 引用必须保持字符串，
        // blocks JSON 模板内插 JSON 文本须经转义后仍可解析为数组
        ExecutorProfile profile = new ExecutorProfile("flow-sop", "agent", "流程示例", null,
                null, List.of(), List.of(), List.of(), null, null,
                List.of(),
                Map.of(
                        "title", "报告",
                        "summary", "${nodes.n1.text}",
                        "blocks", "[{\"kind\":\"text\",\"text\":\"${nodes.n1.text}\"}]"),
                List.of(new ExecutorProfile.FlowNode("n1", "tool", "crm_get_customer",
                        Map.of("customerId", "${params.customerId}"),
                        null, null, null, null, null, null)));

        IntentResult result = execute(profile, Map.of("customerId", "12"), List.of());

        assertEquals(IntentStatus.SUCCESS, result.status());
        assertTrue(result.output().get("summary") instanceof String,
                "summary 应为字符串，实际: " + result.output().get("summary").getClass());
        assertTrue(result.output().get("blocks") instanceof List,
                "blocks 应为数组，实际: " + result.output().get("blocks").getClass());
        assertTrue(String.valueOf(result.output().get("summary")).contains("12"));
    }

    @Test
    void 流程编排校验规则() {
        ExecutorProfile bad = new ExecutorProfile("flow-sop", "agent", "流程示例", null,
                null, List.of(), List.of(), List.of(), null, null,
                List.of(), Map.of(),
                List.of(
                        new ExecutorProfile.FlowNode("n1", "tool", null, null,
                                null, null, null, null, null, null),
                        new ExecutorProfile.FlowNode("n1", "agent", null, null, null,
                                null, null, null, null, null),
                        new ExecutorProfile.FlowNode("n2", "workflow", null, null, null,
                                null, null, null, null, null)));
        List<String> errors = ExecutorProfileValidator.validate(bad);
        assertTrue(errors.stream().anyMatch(e -> e.contains("缺少 tool")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("id 重复")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("tool/agent/knowledge")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("必须配置 model")));
    }
}
