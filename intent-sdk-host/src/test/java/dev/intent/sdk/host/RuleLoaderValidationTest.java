package dev.intent.sdk.host;

import dev.intent.protocol.ContextField;
import dev.intent.protocol.IntentCatalogEntry;
import dev.intent.protocol.IntentScope;
import dev.intent.sdk.host.rule.IntentRuleException;
import dev.intent.sdk.host.rule.IntentRuleLoader;
import dev.intent.sdk.host.rule.IntentSuggestionRule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 规则加载校验：写错的规则必须在<b>启动期</b>报错，而不是运行时静默不出东西。
 *
 * <p>这里的每一条负例都对应一次真实故障场景。</p>
 */
class RuleLoaderValidationTest {

    private static String rule(String body) {
        return "- id: r1\n  intent: demo.intent\n  dataset: ds\n" + body;
    }

    @Test
    void unknownOperatorIsRejected() {
        IntentRuleException e = assertThrows(IntentRuleException.class, () -> IntentRuleLoader.parse(
                rule("  filter:\n    op: roughly\n    left: row.a\n    right: 1\n"
                        + "  items:\n    title: t\n    reason: why\n"), "test"));
        assertTrue(e.getMessage().contains("算子"), e.getMessage());
    }

    @Test
    void unknownContextFieldInTemplateIsRejected() {
        IntentRuleException e = assertThrows(IntentRuleException.class, () -> IntentRuleLoader.parse(
                rule("  items:\n    title: \"{{ctx.customerName}} 的合同\"\n    reason: why\n"), "test"));
        assertTrue(e.getMessage().contains("ctx.customerName"), e.getMessage());
    }

    @Test
    void missingReasonIsRejected() {
        IntentRuleException e = assertThrows(IntentRuleException.class, () -> IntentRuleLoader.parse(
                rule("  items:\n    title: \"{{row.name}}\"\n"), "test"));
        assertTrue(e.getMessage().contains("reason"), e.getMessage());
    }

    @Test
    void missingDatasetIsRejected() {
        IntentRuleException e = assertThrows(IntentRuleException.class, () -> IntentRuleLoader.parse(
                "- id: r1\n  intent: demo.intent\n  items:\n    title: t\n    reason: why\n", "test"));
        assertTrue(e.getMessage().contains("dataset"), e.getMessage());
    }

    @Test
    void duplicateRuleIdIsRejected() {
        String yaml = rule("  items:\n    title: t\n    reason: why\n")
                + rule("  items:\n    title: t\n    reason: why\n");
        IntentRuleException e = assertThrows(IntentRuleException.class,
                () -> IntentRuleLoader.parse(yaml, "test"));
        assertTrue(e.getMessage().contains("重复"), e.getMessage());
    }

    @Test
    void unknownIntentIsRejectedByCatalogValidation() {
        List<IntentSuggestionRule> rules = IntentRuleLoader.parse(
                rule("  items:\n    title: t\n    reason: why\n"), "test");
        IntentRuleException e = assertThrows(IntentRuleException.class,
                () -> IntentRuleLoader.validate(rules, id -> null));
        assertTrue(e.getMessage().contains("未登记"), e.getMessage());
    }

    @Test
    void undeclaredParamIsRejected() {
        List<IntentSuggestionRule> rules = IntentRuleLoader.parse(
                rule("  items:\n    title: t\n    reason: why\n    params:\n      typo: { from: row.id }\n"),
                "test");
        IntentRuleException e = assertThrows(IntentRuleException.class,
                () -> IntentRuleLoader.validate(rules, id -> entry(false)));
        assertTrue(e.getMessage().contains("typo"), e.getMessage());
    }

    @Test
    void unmappableRequiredParamIsRejected() {
        // 意图必填 contractId，但规则只映射了 focus，上下文也补不上 -> 点击必弹表单
        List<IntentSuggestionRule> rules = IntentRuleLoader.parse(
                rule("  items:\n    title: t\n    reason: why\n    params:\n      focus: { value: x }\n"),
                "test");
        IntentRuleException e = assertThrows(IntentRuleException.class,
                () -> IntentRuleLoader.validate(rules, id -> entry(false)));
        assertTrue(e.getMessage().contains("contractId"), e.getMessage());
    }

    @Test
    void requiredParamCoveredByContextIsAccepted() {
        List<IntentSuggestionRule> rules = IntentRuleLoader.parse(
                rule("  items:\n    title: t\n    reason: why\n    params:\n      focus: { value: x }\n"),
                "test");
        IntentRuleLoader.validate(rules, id -> entry(true));
    }

    @Test
    void validRulePassesAllValidation() {
        List<IntentSuggestionRule> rules = IntentRuleLoader.parse(
                rule("  items:\n    title: t\n    reason: why\n"
                        + "    params:\n      contractId: { from: row.id, type: string }\n"), "test");
        IntentRuleLoader.validate(rules, id -> entry(false));
        assertEquals(1, rules.size());
    }

    private static IntentCatalogEntry entry(boolean contextSuppliesContractId) {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "contractId", Map.of("type", "string"),
                        "focus", Map.of("type", "string")),
                "required", List.of("contractId"));
        List<ContextField> context = contextSuppliesContractId
                ? List.of(new ContextField("contractId", "合同编号", true))
                : List.of();
        return new IntentCatalogEntry("demo.intent", "演示意图", "desc", IntentScope.LOCAL,
                null, "plan", schema, context, List.of("crm/customer"));
    }
}