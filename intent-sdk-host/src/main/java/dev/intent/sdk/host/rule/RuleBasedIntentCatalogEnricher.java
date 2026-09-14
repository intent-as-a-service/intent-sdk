package dev.intent.sdk.host.rule;

import dev.intent.protocol.IntentBadge;
import dev.intent.protocol.IntentSuggestion;
import dev.intent.sdk.catalog.IntentCatalogContext;
import dev.intent.sdk.catalog.IntentCatalogEnricher;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 声明式目录增强器：SDK 内置，宿主不再为每条业务规则写一个 Java 类。
 *
 * <p>执行流程（每条规则独立配额、独立降级）：</p>
 * <ol>
 *   <li>范围判定：页面白名单 + 对象类型；</li>
 *   <li>取数：{@link IntentFactProvider} 按 dataset 取行；</li>
 *   <li>条件过滤：{@link RuleEvaluator}；</li>
 *   <li>渲染：{@link TemplateRenderer} 出文案，{@link ParamMapper} 出参数；</li>
 *   <li>产出徽标与条目。</li>
 * </ol>
 *
 * <p>参数合法性不在这里判——装配器会再校验一次（意图可见 + 参数在 Schema 内 + 条目型必填齐备），
 * 这里只管产出；单条规则抛错只跳过它自己，不影响其他规则与意图菜单。</p>
 */
public final class RuleBasedIntentCatalogEnricher implements IntentCatalogEnricher {

    private final List<IntentSuggestionRule> rules;
    private final IntentFactProvider facts;

    public RuleBasedIntentCatalogEnricher(List<IntentSuggestionRule> rules, IntentFactProvider facts) {
        this.rules = rules == null ? List.of() : List.copyOf(rules);
        this.facts = facts;
    }

    public List<IntentSuggestionRule> rules() {
        return rules;
    }

    @Override
    public Map<String, IntentBadge> badges(IntentCatalogContext context) {
        if (facts == null) {
            return Map.of();
        }
        Map<String, IntentBadge> result = new LinkedHashMap<>();
        for (IntentSuggestionRule rule : rules) {
            if (!rule.hasBadge() || !inScope(rule, context)) {
                continue;
            }
            try {
                int count = Math.max(0, facts.count(rule.dataset(), context));
                if (count <= 0) {
                    continue;
                }
                // count = dataset 級的權威總數；matched = 本規則 filter 之後的命中數。
                // 有 filter 的規則必須用 matched 決定要不要提醒，否則會出現
                // "4 筆逾期超 60 天" 但實際 0 筆命中的假警報。
                int matched = rule.filter() == null ? count : matchedCount(rule, context, count);
                if (rule.filter() != null && matched <= 0) {
                    continue;
                }
                String text = TemplateRenderer.render(rule.badge().text(),
                        RuleContext.of(context, null, count, matched));
                String level = rule.badge().level() == null ? IntentBadge.LEVEL_INFO : rule.badge().level();
                result.putIfAbsent(rule.intentId(), new IntentBadge(text, level, matched));
            } catch (Exception e) {
                warn(rule, e);
            }
        }
        return result;
    }

    @Override
    public List<IntentSuggestion> suggestions(IntentCatalogContext context) {
        if (facts == null) {
            return List.of();
        }
        List<IntentSuggestion> result = new ArrayList<>();
        for (IntentSuggestionRule rule : rules) {
            if (!rule.hasItems() || !inScope(rule, context)) {
                continue;
            }
            try {
                result.addAll(itemsOf(rule, context));
            } catch (Exception e) {
                warn(rule, e);
            }
        }
        return result;
    }

    private List<IntentSuggestion> itemsOf(IntentSuggestionRule rule, IntentCatalogContext context) {
        IntentSuggestionRule.Items items = rule.items();
        int limit = items.effectiveLimit();
        List<Map<String, Object>> rows = facts.rows(rule.dataset(), context, limit);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<IntentSuggestion> suggestions = new ArrayList<>();
        int index = 0;
        for (Map<String, Object> row : rows) {
            if (suggestions.size() >= limit) {
                break;
            }
            index++;
            RuleContext ruleContext = RuleContext.of(context, row, rows.size());
            if (!RuleEvaluator.test(rule.filter(), ruleContext)) {
                continue;
            }
            Map<String, Object> params = ParamMapper.map(items, ruleContext);
            suggestions.add(IntentSuggestion.item(
                    rule.id() + "-" + rowKey(row, index),
                    rule.intentId(),
                    TemplateRenderer.render(items.title(), ruleContext),
                    TemplateRenderer.render(items.subtitle(), ruleContext),
                    params,
                    TemplateRenderer.render(items.reason(), ruleContext)));
        }
        return suggestions;
    }

    private static String rowKey(Map<String, Object> row, int fallbackIndex) {
        Object id = row.get("id");
        return id == null ? String.valueOf(fallbackIndex) : String.valueOf(id);
    }

    /**
     * 本規則 filter 之後的命中數（上限 = 宿主單次取數上限，預設 50 筆）。
     * 只用作「要不要提醒」與 badge 文案，不做精確統計口徑。
     */
    private int matchedCount(IntentSuggestionRule rule, IntentCatalogContext context, int total) {
        List<Map<String, Object>> rows = facts.rows(rule.dataset(), context, 0);
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        int matched = 0;
        for (Map<String, Object> row : rows) {
            if (RuleEvaluator.test(rule.filter(), RuleContext.of(context, row, total))) {
                matched++;
            }
        }
        return matched;
    }

    private static boolean inScope(IntentSuggestionRule rule, IntentCatalogContext context) {
        if (rule.scope() == null) {
            return true;
        }
        String page = context == null ? null : context.page();
        if (!rule.scope().acceptsPage(page)) {
            return false;
        }
        String requiredObjectType = rule.scope().objectType();
        if (requiredObjectType != null && !requiredObjectType.isBlank()) {
            return context != null && requiredObjectType.equals(context.objectType());
        }
        return true;
    }

    private static void warn(IntentSuggestionRule rule, Exception e) {
        System.err.println("[intent-sdk] 规则求值失败（已跳过该规则）: " + rule.id() + " -> " + e.getMessage());
    }
}
