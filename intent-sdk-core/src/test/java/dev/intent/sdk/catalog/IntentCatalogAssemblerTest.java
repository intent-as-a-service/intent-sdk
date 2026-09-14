package dev.intent.sdk.catalog;

import dev.intent.protocol.IntentBadge;
import dev.intent.protocol.IntentCatalogEntry;
import dev.intent.protocol.IntentScope;
import dev.intent.protocol.IntentSuggestion;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 目录装配（动态提示 / 动态建议）：增强产出必须过可见性与参数准入，异常必须被隔离。 */
class IntentCatalogAssemblerTest {

    private static final IntentCatalogContext CONTEXT = new IntentCatalogContext("1", "admin", "crm/customer");

    private static Map<String, Object> contractSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "contractId", Map.of("type", "string"),
                        "focus", Map.of("type", "string")),
                "required", List.of("contractId"));
    }

    private static IntentCatalogEntry entry(String id, Map<String, Object> schema) {
        return new IntentCatalogEntry(id, id, "描述", IntentScope.LOCAL, null, "analysis", schema,
                List.of(), List.of("crm/customer"));
    }

    private static IntentCatalogEnricher enricher(Map<String, IntentBadge> badges,
            List<IntentSuggestion> suggestions) {
        return new IntentCatalogEnricher() {
            @Override
            public Map<String, IntentBadge> badges(IntentCatalogContext context) {
                return badges;
            }

            @Override
            public List<IntentSuggestion> suggestions(IntentCatalogContext context) {
                return suggestions;
            }
        };
    }

    private static IntentSuggestion item(String intentId, Map<String, Object> params) {
        return IntentSuggestion.item("s-" + intentId + "-" + params, intentId, "标题", "副标题", params, "原因");
    }

    @Test
    void 无增强器时目录原样返回() {
        IntentCatalogEntry analyze = entry("crm.customer.analyze", Map.of());
        IntentCatalogAssembler.Result result = IntentCatalogAssembler.assemble(
                CONTEXT, List.of(analyze), List.of(analyze), List.of(), 3);
        assertEquals(List.of(analyze), result.entries());
        assertTrue(result.suggestions().isEmpty());
    }

    @Test
    void 徽标只挂到已装载的目录项上() {
        IntentCatalogEntry risk = entry("crm.contract.risk-review", contractSchema());
        IntentCatalogEntry analyze = entry("crm.customer.analyze", Map.of());
        IntentCatalogAssembler.Result result = IntentCatalogAssembler.assemble(
                CONTEXT, List.of(risk, analyze), List.of(risk, analyze),
                List.of(enricher(Map.of(
                        "crm.contract.risk-review", IntentBadge.warning("2 份合同即将到期", 2),
                        "crm.contract.not-loaded", IntentBadge.warning("不该出现", 1)), List.of())),
                3);
        assertEquals("2 份合同即将到期", result.entries().get(0).badge().text());
        assertEquals(2, result.entries().get(0).badge().count());
        assertNull(result.entries().get(1).badge());
    }

    @Test
    void 条目型建议参数齐备时保留() {
        IntentCatalogEntry risk = entry("crm.contract.risk-review", contractSchema());
        IntentSuggestion suggestion = item("crm.contract.risk-review",
                Map.of("contractId", "12", "focus", "到期与续约风险"));
        IntentCatalogAssembler.Result result = IntentCatalogAssembler.assemble(
                CONTEXT, List.of(risk), List.of(risk),
                List.of(enricher(Map.of(), List.of(suggestion))), 3);
        assertEquals(List.of(suggestion), result.suggestions());
    }

    @Test
    void 不可见意图的建议被拒() {
        IntentCatalogEntry analyze = entry("crm.customer.analyze", Map.of());
        IntentSuggestion hidden = item("crm.contract.risk-review", Map.of("contractId", "12"));
        IntentCatalogAssembler.Result result = IntentCatalogAssembler.assemble(
                CONTEXT, List.of(analyze), List.of(analyze),
                List.of(enricher(Map.of(), List.of(hidden))), 3);
        assertTrue(result.suggestions().isEmpty());
    }

    @Test
    void 未声明参数与缺失必填参数被拒() {
        IntentCatalogEntry risk = entry("crm.contract.risk-review", contractSchema());
        IntentSuggestion undeclared = item("crm.contract.risk-review",
                Map.of("contractId", "12", "customerId", "3"));
        IntentSuggestion missingRequired = item("crm.contract.risk-review", Map.of("focus", "账期"));
        IntentCatalogAssembler.Result result = IntentCatalogAssembler.assemble(
                CONTEXT, List.of(risk), List.of(risk),
                List.of(enricher(Map.of(), List.of(undeclared, missingRequired))), 3);
        assertTrue(result.suggestions().isEmpty());
    }

    @Test
    void 聚合型建议允许参数不齐备() {
        IntentCatalogEntry risk = entry("crm.contract.risk-review", contractSchema());
        IntentSuggestion aggregate = new IntentSuggestion("agg-1", "crm.contract.risk-review",
                "3 份合同即将到期", null, IntentSuggestion.KIND_AGGREGATE, 3, Map.of(), "原因", "agg-1");
        IntentCatalogAssembler.Result result = IntentCatalogAssembler.assemble(
                CONTEXT, List.of(risk), List.of(risk),
                List.of(enricher(Map.of(), List.of(aggregate))), 3);
        assertEquals(1, result.suggestions().size());
    }

    @Test
    void 增强器抛错被隔离且不影响其他增强器() {
        IntentCatalogEntry risk = entry("crm.contract.risk-review", contractSchema());
        IntentCatalogEnricher broken = new IntentCatalogEnricher() {
            @Override
            public Map<String, IntentBadge> badges(IntentCatalogContext context) {
                throw new IllegalStateException("事实查询失败");
            }

            @Override
            public List<IntentSuggestion> suggestions(IntentCatalogContext context) {
                throw new IllegalStateException("事实查询失败");
            }
        };
        IntentSuggestion ok = item("crm.contract.risk-review", Map.of("contractId", "12"));
        IntentCatalogAssembler.Result result = IntentCatalogAssembler.assemble(
                CONTEXT, List.of(risk), List.of(risk),
                List.of(broken, enricher(Map.of(
                        "crm.contract.risk-review", IntentBadge.warning("1 份合同即将到期", 1)),
                        List.of(ok))),
                3);
        assertEquals("1 份合同即将到期", result.entries().get(0).badge().text());
        assertEquals(List.of(ok), result.suggestions());
    }

    @Test
    void 建议条数受上限约束并去重() {
        IntentCatalogEntry risk = entry("crm.contract.risk-review", contractSchema());
        IntentSuggestion first = item("crm.contract.risk-review", Map.of("contractId", "1"));
        IntentSuggestion second = item("crm.contract.risk-review", Map.of("contractId", "2"));
        IntentSuggestion duplicated = IntentSuggestion.item("s-crm.contract.risk-review-{contractId=1}",
                "crm.contract.risk-review", "标题", null, Map.of("contractId", "3"), "原因");
        IntentCatalogAssembler.Result result = IntentCatalogAssembler.assemble(
                CONTEXT, List.of(risk), List.of(risk),
                List.of(enricher(Map.of(), List.of(first, second, duplicated))), 2);
        assertEquals(2, result.suggestions().size());
        assertEquals("1", result.suggestions().get(0).params().get("contractId"));
        assertEquals("2", result.suggestions().get(1).params().get("contractId"));
    }

    @Test
    void 每条规则的条数上限独立计算() {
        IntentCatalogEntry risk = entry("crm.contract.risk-review", contractSchema());
        IntentCatalogEntry analyze = entry("crm.customer.analyze", Map.of());
        IntentSuggestion contract = item("crm.contract.risk-review", Map.of("contractId", "1"));
        IntentSuggestion customer = item("crm.customer.analyze", Map.of());
        IntentSuggestion customerExtra = item("crm.customer.analyze", Map.of("focus", "续约"));
        IntentCatalogAssembler.Result result = IntentCatalogAssembler.assemble(
                CONTEXT, List.of(risk, analyze), List.of(risk, analyze),
                List.of(enricher(Map.of(), List.of(contract)),
                        enricher(Map.of(), List.of(customer, customerExtra))),
                1);
        // 上限=1 时：两条规则各出 1 条（而不是先到先得把配额吃光）
        assertEquals(List.of(contract, customer), result.suggestions());
    }
}
