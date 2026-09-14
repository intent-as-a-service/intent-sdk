package dev.intent.sdk.pi;

import dev.intent.sdk.executor.ExecutionOutcome;
import dev.intent.sdk.executor.ExecutionRequest;
import dev.intent.sdk.executor.ExecutorProfile;
import dev.intent.sdk.executor.ExecutorProfileException;
import dev.intent.sdk.executor.ExecutorProfileLoader;
import dev.intent.sdk.executor.ExecutorProfileValidator;
import dev.intent.sdk.executor.IntentExecutor;
import dev.intent.sdk.executor.KnowledgeRetriever;

import dev.intent.sdk.llm.LlmConfig;
import dev.intent.sdk.tool.HostToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 执行器档案：YAML 解析与默认值、结构校验、工厂构建与把关。 */
class ExecutorProfileTest {

    private static final String VALID_YAML = """
            id: sales-analyst-v2
            type: agent
            name: 销售分析师
            model:
              baseUrl: https://api.deepseek.com
              provider: deepseek
              modelId: deepseek-chat
            tools:
              - crm_get_customer
            limits:
              maxTurns: 8
            """;

    @Test
    void 解析YAML与默认值() {
        ExecutorProfile profile = ExecutorProfileLoader.parse(VALID_YAML, true);

        assertEquals("sales-analyst-v2", profile.id());
        assertEquals("agent", profile.type());
        assertEquals("销售分析师", profile.name());
        assertEquals("https://api.deepseek.com", profile.model().baseUrl());
        assertEquals(List.of("crm_get_customer"), profile.tools());
        assertEquals(8, profile.limits().maxTurns());
        assertEquals(1, profile.limits().outputMaxRetries());
        assertTrue(profile.knowledge().isEmpty());
    }

    @Test
    void 缺model或端点报错() {
        ExecutorProfile noModel = new ExecutorProfile("a1", "agent", null, null,
                null, List.of(), List.of(), List.of(), null, null, null, null);
        assertTrue(ExecutorProfileValidator.validate(noModel).stream().anyMatch(e -> e.contains("model")));
    }

    @Test
    void 未知类型或非法工具名报错() {
        ExecutorProfile badType = new ExecutorProfile("a1", "workflow", null, null,
                null, List.of(), List.of(), List.of(), null, null, null, null);
        assertTrue(ExecutorProfileValidator.validate(badType).stream().anyMatch(e -> e.contains("type")));

        ExecutorProfile badTool = new ExecutorProfile("a1", "agent", null, null,
                LlmConfig.deepseek("k"), List.of("Bad-Tool"), List.of(), List.of(), null, null, null, null);
        assertTrue(ExecutorProfileValidator.validate(badTool).stream().anyMatch(e -> e.contains("Bad-Tool")));
    }

    @Test
    void 内置id保留() {
        ExecutorProfile impostor = new ExecutorProfile("builtin-agent", "agent", null, null,
                LlmConfig.deepseek("k"), List.of(), List.of(), List.of(), null, null, null, null);
        assertTrue(ExecutorProfileValidator.validate(impostor).stream()
                .anyMatch(e -> e.contains("保留")));
    }

    @Test
    void 工厂构建出同id执行器() {
        ExecutorProfile profile = ExecutorProfileLoader.parse(VALID_YAML, true);
        List<IntentExecutor> built = ExecutorProfiles.build(List.of(profile),
                new HostToolRegistry(), List::of);

        assertEquals(1, built.size());
        assertEquals("sales-analyst-v2", built.get(0).id());
        assertInstanceOf(AgentExecutor.class, built.get(0));
    }

    @Test
    void 工厂拒绝未实现的附件声明() {
        ExecutorProfile withKnowledge = new ExecutorProfile("a1", "agent", null, null,
                LlmConfig.deepseek("k"), List.of(),
                List.of(new ExecutorProfile.KnowledgeRef("产品手册", 5)), List.of(), null, null,
                List.of(), Map.of());
        ExecutorProfileException e1 = assertThrows(ExecutorProfileException.class,
                () -> ExecutorProfiles.build(List.of(withKnowledge), new HostToolRegistry(), List::of));
        assertTrue(e1.getMessage().contains("knowledge"));
    }

    @Test
    void 工厂拒绝remote类型与缺步骤的skill() {
        ExecutorProfile remoteType = new ExecutorProfile("a2", "remote", null, null,
                null, List.of(), List.of(), List.of(), null, null, List.of(), Map.of());
        ExecutorProfileException e = assertThrows(ExecutorProfileException.class,
                () -> ExecutorProfiles.build(List.of(remoteType), new HostToolRegistry(), List::of));
        assertTrue(e.getMessage().contains("remote"));

        ExecutorProfile skillNoSteps = new ExecutorProfile("a3", "skill", null, null,
                null, List.of(), List.of(), List.of(), null, null, List.of(), Map.of());
        assertTrue(ExecutorProfileValidator.validate(skillNoSteps).stream()
                .anyMatch(e2 -> e2.contains("steps")));
    }

    @Test
    void skill校验规则() {
        // 步骤必须 tool / prompt 二选一
        ExecutorProfile badStep = new ExecutorProfile("s1", "skill", null, null, null,
                List.of(), List.of(), List.of(), null, null,
                List.of(new ExecutorProfile.SkillStep("a", null, null, Map.of())), Map.of());
        assertTrue(ExecutorProfileValidator.validate(badStep).stream()
                .anyMatch(e -> e.contains("tool 或 prompt")));
        // LLM 步骤要求 model
        ExecutorProfile llmNoModel = new ExecutorProfile("s1", "skill", null, null, null,
                List.of(), List.of(), List.of(), null, null,
                List.of(new ExecutorProfile.SkillStep("a", null, "总结", Map.of())), Map.of());
        assertTrue(ExecutorProfileValidator.validate(llmNoModel).stream()
                .anyMatch(e -> e.contains("model")));
    }

    @Test
    void skill档案构建为SkillExecutor并支持JSON往返() {
        ExecutorProfile skill = new ExecutorProfile("collect-sop", "skill", "催收SOP", null,
                null, List.of(), List.of(), List.of(), null, null,
                List.of(new ExecutorProfile.SkillStep("lookup", "crm_get_customer", null, Map.of())),
                Map.of());
        List<IntentExecutor> built = ExecutorProfiles.build(List.of(skill),
                new HostToolRegistry(), List::of);
        assertEquals(1, built.size());
        assertInstanceOf(SkillExecutor.class, built.get(0));

        // DB 化往返：toJson → parse(json, false) 语义等价
        ExecutorProfile roundTrip = ExecutorProfileLoader.parse(
                ExecutorProfileLoader.toJson(skill), false);
        assertEquals(skill, roundTrip);
    }

    @Test
    void 工厂拒绝重复id() {
        ExecutorProfile p = ExecutorProfileLoader.parse(VALID_YAML, true);
        ExecutorProfileException e = assertThrows(ExecutorProfileException.class,
                () -> ExecutorProfiles.build(List.of(p, p), new HostToolRegistry(), List::of));
        assertTrue(e.getMessage().contains("重复"));
    }
}
