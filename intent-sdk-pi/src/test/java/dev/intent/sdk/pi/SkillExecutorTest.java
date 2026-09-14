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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * skill 型执行器离线端到端：纯工具技能零 LLM 即可跑通（确定性、低 token），
 * 步骤链路模板渲染、出参模板组装与 Schema 校验、失败语义化。
 */
class SkillExecutorTest {

    private static final LlmConfig OFFLINE_LLM =
            LlmConfig.openAiCompatible("http://127.0.0.1:1", "test-key", "test-model");

    private static HostToolRegistry hostTools() {
        return new HostToolRegistry()
                .register(LambdaHostTool.ofText("crm_get_customer", "查询客户",
                        Map.of("customerId", Map.of("type", "string")),
                        (args, s) -> "客户: " + args.get("customerId")))
                .register(LambdaHostTool.ofData("crm_echo_details", "回显结构化数据",
                        Map.of(),
                        (args, s) -> Map.of("level", "A", "score", 87)));
    }

    /** 催收标准流程：查客户 → 取结构化档案 → 按模板组装出参。纯工具步骤，不触网。 */
    private static ExecutorProfile collectSkill(Map<String, String> output) {
        return new ExecutorProfile("collect-sop", "skill", "催收标准流程", null,
                null, List.of(), List.of(), List.of(), null, null,
                List.of(new ExecutorProfile.SkillStep("lookup", "crm_get_customer",
                                null, Map.of("customerId", "${params.customerId}")),
                        new ExecutorProfile.SkillStep("profile", "crm_echo_details", null, Map.of())),
                output);
    }

    private static IntentSpec spec() {
        return new IntentSpec.Builder()
                .id("collect.customer.follow")
                .name("催收跟进")
                .promptTemplate("技能执行不需要提示词模板，但契约要求必填")
                .executor("collect-sop")
                .build();
    }

    private static IntentResult execute(ExecutorProfile profile, IntentSpec spec,
            Map<String, Object> params) {
        HostToolRegistry tools = hostTools();
        IntentRuntime runtime = new IntentRuntime(OFFLINE_LLM, tools, List::of,
                ExecutorProfiles.build(List.of(profile), tools, List::of), null, 4, 0);
        return runtime.execute(spec, params, Map.of(), null, null);
    }

    @Test
    void 纯工具技能离线端到端并按模板组装出参() {
        IntentResult result = execute(collectSkill(Map.of(
                        "title", "催收报告",
                        "summary", "${steps.lookup.text}；等级=${steps.profile.details.level}，评分=${steps.profile.details.score}",
                        "blocks", "[{\"kind\":\"text\",\"text\":\"${steps.lookup.text}\"}]")),
                spec(), Map.of("customerId", "C1001"));

        assertEquals(IntentStatus.SUCCESS, result.status());
        assertEquals("催收报告", result.output().get("title"));
        assertEquals("客户: C1001；等级=A，评分=87", result.output().get("summary"));
        assertInstanceOf(List.class, result.output().get("blocks"));
        assertEquals(2, result.steps().size());
    }

    @Test
    void 缺省出参走标准卡片() {
        // 单工具步骤：缺省 summary/blocks 取最后一步文本，行为可确定
        ExecutorProfile single = new ExecutorProfile("collect-sop", "skill", "催收标准流程", null,
                null, List.of(), List.of(), List.of(), null, null,
                List.of(new ExecutorProfile.SkillStep("lookup", "crm_get_customer",
                        null, Map.of("customerId", "${params.customerId}"))),
                Map.of());
        IntentResult result = execute(single, spec(), Map.of("customerId", "C1001"));

        assertEquals(IntentStatus.SUCCESS, result.status());
        assertEquals("催收跟进", result.output().get("title"));
        assertEquals("客户: C1001", result.output().get("summary"));
        assertInstanceOf(List.class, result.output().get("blocks"));
    }

    @Test
    void 模板变量缺失报配置错误() {
        IntentResult result = execute(collectSkill(Map.of(
                        "title", "报告",
                        "summary", "${params.absent}")),
                spec(), Map.of("customerId", "C1001"));

        assertEquals(IntentStatus.FAILED, result.status());
        assertEquals(IntentErrorCodes.VALIDATION_ERROR, result.error().code());
        assertTrue(result.error().message().contains("模板变量未找到"));
    }

    @Test
    void 步骤引用未注册工具报TOOL_ERROR() {
        ExecutorProfile profile = new ExecutorProfile("collect-sop", "skill", "催收标准流程", null,
                null, List.of(), List.of(), List.of(), null, null,
                List.of(new ExecutorProfile.SkillStep("ghost", "crm_not_registered", null, Map.of())),
                Map.of());
        IntentResult result = execute(profile, spec(), Map.of("customerId", "C1001"));

        assertEquals(IntentStatus.FAILED, result.status());
        assertEquals(IntentErrorCodes.TOOL_ERROR, result.error().code());
        assertTrue(result.error().message().contains("crm_not_registered"));
    }

    @Test
    void LLM步骤离线失败报LLM_ERROR并留痕() {
        ExecutorProfile profile = new ExecutorProfile("collect-sop", "skill", "催收标准流程", null,
                OFFLINE_LLM, List.of(), List.of(), List.of(), null, null,
                List.of(new ExecutorProfile.SkillStep("summarize", null,
                        "总结客户 ${params.customerId}", Map.of())),
                Map.of());
        IntentResult result = execute(profile, spec(), Map.of("customerId", "C1001"));

        assertEquals(IntentStatus.FAILED, result.status());
        assertNotNull(result.error());
        assertEquals(IntentErrorCodes.LLM_ERROR, result.error().code());
    }
}
