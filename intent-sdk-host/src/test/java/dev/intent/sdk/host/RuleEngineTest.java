package dev.intent.sdk.host;

import dev.intent.protocol.IntentBadge;
import dev.intent.protocol.IntentSuggestion;
import dev.intent.sdk.catalog.IntentCatalogContext;
import dev.intent.sdk.host.rule.IntentFactProvider;
import dev.intent.sdk.host.rule.IntentRuleLoader;
import dev.intent.sdk.host.rule.IntentSuggestionRule;
import dev.intent.sdk.host.rule.RuleBasedIntentCatalogEnricher;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 声明式规则引擎端到端：用 CRM「合同即将到期」这条真实规则当夹具。
 *
 * <p>验证的是"一条规则 = 查询 + 条件 + 输出模板 + 参数映射"这四件事都能跑通，
 * 且产出的东西能一键执行（参数齐、类型对）。</p>
 */
class RuleEngineTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private static final String EXPIRING_YAML = """
            - id: crm.contract.expiring
              intent: crm.contract.risk-review
              dataset: crm.expiring-contracts
              scope:
                page: ["crm/*", ""]
              filter:
                all:
                  - op: gte
                    left: row.endTime
                    right: today()
                  - op: lte
                    left: daysUntil(row.endTime)
                    right: 7
              badge:
                text: "{{count}} 份合同即将到期"
                level: warning
              items:
                limit: 5
                title: "合同「{{row.name}}」{{row.endTime | expiryText}}"
                subtitle: "编号 {{row.no}} · 点击一键做风险审查"
                reason: "我负责的合同临近到期，建议提前审查履约与续约风险"
                params:
                  contractId: { from: row.id, type: string }
                  focus: { value: "到期与续约风险" }
            """;

    private static final String OBJECT_SCOPED_YAML = """
            - id: crm.customer.contract-expiring
              intent: crm.contract.risk-review
              dataset: crm.expiring-contracts
              scope:
                page: ["crm/customer"]
                objectType: customer
              items:
                limit: 3
                title: "客户「{{ctx.objectName}}」的合同「{{row.name}}」即将到期"
                reason: "当前客户名下有合同临近到期"
                params:
                  contractId: { from: row.id, type: string }
            """;

/**
     * 带 filter 的规则：badge 出现在"过滤后确实命中"时，文案用 {{matched}}，
     * {{count}} 仍是数据集总数。避免"写着 60 天、实际 0 条命中"的假提醒。
     */
    private static final String OVERDUE_BADGE_YAML = """
            - id: crm.contract.overdue-only
              intent: crm.contract.risk-review
              dataset: crm.expiring-contracts
              scope:
                page: ["crm/customer"]
              filter:
                all:
                  - op: lt
                    left: daysUntil(row.endTime)
                    right: 0
              badge:
                text: "{{matched}}/{{count}} 份合同已过期"
                level: danger
              items:
                limit: 5
                title: "合同「{{row.name}}」{{row.endTime | expiryText}}"
                reason: "已经过期，需要马上确认续约还是止损"
                params:
                  contractId: { from: row.id, type: string }
            """;

    private static final String NEVER_MATCH_BADGE_YAML = """
            - id: crm.contract.far-future
              intent: crm.contract.risk-review
              dataset: crm.expiring-contracts
              scope:
                page: ["crm/customer"]
              filter:
                all:
                  - op: gte
                    left: daysUntil(row.endTime)
                    right: 30
              badge:
                text: "{{matched}} 份合同 30 天后到期"
                level: info
              items:
                limit: 5
                title: "合同「{{row.name}}」{{row.endTime | expiryText}}"
                reason: "远期到期，仅作储备提醒"
                params:
                  contractId: { from: row.id, type: string }
            """;

    private static IntentCatalogContext context(String page) {
        return new IntentCatalogContext("1", "admin", "1", page, null, null, null, "Asia/Shanghai");
    }

    private static Map<String, Object> contract(long id, String name, String no, LocalDateTime endTime) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("name", name);
        row.put("no", no);
        row.put("endTime", endTime);
        return row;
    }

    /** 一行 3 天后到期 + 一行已过期 5 天（应被条件滤掉）。 */
    private static List<Map<String, Object>> expiringRows() {
        LocalDate today = LocalDate.now(ZONE);
        return List.of(
                contract(123L, "续约合同", "HT-001", today.plusDays(3).atTime(10, 0)),
                contract(456L, "已过期合同", "HT-002", today.minusDays(5).atTime(10, 0)));
    }

    private static IntentFactProvider facts(List<Map<String, Object>> rows, int count) {
        return new IntentFactProvider() {
            @Override
            public List<Map<String, Object>> rows(String datasetId, IntentCatalogContext ctx, int limit) {
                return rows;
            }

            @Override
            public int count(String datasetId, IntentCatalogContext ctx) {
                return count;
            }
        };
    }

    private static RuleBasedIntentCatalogEnricher enricher(String yaml, IntentFactProvider provider) {
        List<IntentSuggestionRule> rules = IntentRuleLoader.parse(yaml, "test");
        return new RuleBasedIntentCatalogEnricher(rules, provider);
    }

    @Test
    void badgeUsesHostReportedCount() {
        RuleBasedIntentCatalogEnricher enricher = enricher(EXPIRING_YAML, facts(expiringRows(), 1));
        Map<String, IntentBadge> badges = enricher.badges(context("crm/customer"));
        IntentBadge badge = badges.get("crm.contract.risk-review");
        assertEquals("1 份合同即将到期", badge.text());
        assertEquals(IntentBadge.LEVEL_WARNING, badge.level());
        assertEquals(1, badge.count());
    }

    @Test
    void noBadgeWhenNothingPending() {
        RuleBasedIntentCatalogEnricher enricher = enricher(EXPIRING_YAML, facts(List.of(), 0));
        assertTrue(enricher.badges(context("crm/customer")).isEmpty(), "没有待办就不该出现徽标");
    }

    @Test
    void suggestionCarriesOneClickParams() {
        RuleBasedIntentCatalogEnricher enricher = enricher(EXPIRING_YAML, facts(expiringRows(), 1));
        List<IntentSuggestion> suggestions = enricher.suggestions(context("crm/customer"));
        assertEquals(1, suggestions.size(), "过滤条件必须滤掉已过期那条");
        IntentSuggestion suggestion = suggestions.get(0);
        assertEquals("crm.contract.risk-review", suggestion.intentId());
        assertTrue(suggestion.title().contains("续约合同"), suggestion.title());
        assertTrue(suggestion.title().contains("还有 3 天到期"), suggestion.title());
        assertTrue(suggestion.reason().contains("临近到期"), "理由必须能说清为什么");
        assertEquals("123", suggestion.params().get("contractId"), "主键要按 Schema 转成字符串");
        assertEquals("到期与续约风险", suggestion.params().get("focus"));
    }

    @Test
    void pageScopeBlocksUnrelatedPages() {
        RuleBasedIntentCatalogEnricher enricher = enricher(EXPIRING_YAML, facts(expiringRows(), 1));
        assertTrue(enricher.suggestions(context("system/user")).isEmpty());
        assertTrue(enricher.badges(context("system/user")).isEmpty());
    }

    @Test
    void intentCenterPageStillCounts() {
        RuleBasedIntentCatalogEnricher enricher = enricher(EXPIRING_YAML, facts(expiringRows(), 1));
        assertEquals(1, enricher.suggestions(context("")).size(), "空串 page = 意图中心，应照常提示");
    }

    @Test
    void objectScopedRuleOnlyFiresWithMatchingObject() {
        RuleBasedIntentCatalogEnricher enricher = enricher(OBJECT_SCOPED_YAML, facts(expiringRows(), 1));
        assertTrue(enricher.suggestions(context("crm/customer")).isEmpty(),
                "上下文里没有对象时，对象级规则不该出");

        IntentCatalogContext withObject = new IntentCatalogContext(
                "1", "admin", "1", "crm/customer", "customer", "9", "客户A", "Asia/Shanghai");
        List<IntentSuggestion> suggestions = enricher.suggestions(withObject);
        assertEquals(2, suggestions.size(), "该规则没有过滤条件，数据集几行就产出几条");
        assertTrue(suggestions.stream().allMatch(s -> s.title().contains("客户A")),
                "对象级规则必须把当前对象写进文案");
    }

    @Test
    void oneBrokenRuleDoesNotBreakOthers() {
        IntentFactProvider provider = new IntentFactProvider() {
            @Override
            public List<Map<String, Object>> rows(String datasetId, IntentCatalogContext ctx, int limit) {
                if ("boom".equals(datasetId)) {
                    throw new IllegalStateException("模拟宿主查询异常");
                }
                return expiringRows();
            }

            @Override
            public int count(String datasetId, IntentCatalogContext ctx) {
                return "boom".equals(datasetId) ? 0 : 1;
            }
        };
        String twoRules = EXPIRING_YAML + OBJECT_SCOPED_YAML;
        RuleBasedIntentCatalogEnricher enricher = new RuleBasedIntentCatalogEnricher(
                IntentRuleLoader.parse(twoRules, "test"), provider);
        assertEquals(1, enricher.suggestions(context("crm/customer")).size(), "坏规则只降级它自己");
    }

    @Test
    void itemLimitIsRespected() {
        LocalDate today = LocalDate.now(ZONE);
        List<Map<String, Object>> many = List.of(
                contract(1L, "A", "N1", today.plusDays(1).atTime(9, 0)),
                contract(2L, "B", "N2", today.plusDays(2).atTime(9, 0)),
                contract(3L, "C", "N3", today.plusDays(3).atTime(9, 0)),
                contract(4L, "D", "N4", today.plusDays(4).atTime(9, 0)),
                contract(5L, "E", "N5", today.plusDays(5).atTime(9, 0)),
                contract(6L, "F", "N6", today.plusDays(6).atTime(9, 0)));
        RuleBasedIntentCatalogEnricher enricher = enricher(EXPIRING_YAML, facts(many, many.size()));
        assertEquals(5, enricher.suggestions(context("crm/customer")).size(), "limit=5 必须生效");
        assertFalse(enricher.suggestions(context("crm/customer")).isEmpty());
    }
@Test
    void filteredBadgeUsesMatchedCountNotDatasetTotal() {
        RuleBasedIntentCatalogEnricher enricher = enricher(OVERDUE_BADGE_YAML, facts(expiringRows(), 2));
        IntentBadge badge = enricher.badges(context("crm/customer")).get("crm.contract.risk-review");
        assertEquals("1/2 份合同已过期", badge.text(), "{{matched}} 走过滤后命中数，{{count}} 走数据集总数");
        assertEquals(1, badge.count(), "徽标对外暴露的也是命中数");
    }

    @Test
    void filteredBadgeSuppressedWhenNothingMatches() {
        RuleBasedIntentCatalogEnricher enricher = enricher(NEVER_MATCH_BADGE_YAML, facts(expiringRows(), 2));
        assertTrue(enricher.badges(context("crm/customer")).isEmpty(),
                "过滤器一条都没命中时不能出徽标，否则是假警报");
    }
}
