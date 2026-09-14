package dev.intent.sdk.catalog;

import dev.intent.protocol.IntentBadge;
import dev.intent.protocol.IntentCatalogEntry;
import dev.intent.protocol.IntentSuggestion;
import dev.intent.sdk.schema.JsonSchemaValidator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 目录装配器：把意图目录与宿主增强（动态提示 / 动态建议）合并成前端可直接渲染的结果。
 *
 * <p>不可信边界在这里收口——增强器产出的一切都要过准入校验：</p>
 * <ol>
 *   <li>徽标只挂到当前目录中已存在的意图上；</li>
 *   <li>建议引用的意图必须对当前用户<b>可见</b>（下架/无权限的意图不得从建议通道漏出）；</li>
 *   <li>建议参数必须在该意图入参 Schema 内声明，且条目型建议的必填参数必须齐备
 *       （否则一点就弹补参表单，个性化就白做了）；</li>
 *   <li>单个增强器抛错只降级它自己的产出，不影响其他增强器与意图菜单。</li>
 * </ol>
 *
 * <p>条数上限按增强器（每条业务规则）独立计算：一条规则条目多，也不能把其他规则的待办挤掉；
 * 渲染侧按目标意图分组、超量折叠。</p>
 */
public final class IntentCatalogAssembler {

    /** 装配结果：当前页面装载的目录项（含徽标）+ 动态建议。 */
    public record Result(List<IntentCatalogEntry> entries, List<IntentSuggestion> suggestions) {

        public static Result of(List<IntentCatalogEntry> entries) {
            return new Result(entries, List.of());
        }
    }

    private IntentCatalogAssembler() {
    }

    /**
     * @param context        求值上下文（当前用户 + 页面）
     * @param pageEntries    当前页面装载的目录项（前端渲染主体）
     * @param visibleEntries 当前用户可见的全部目录项（建议准入依据，不受页面过滤影响）
     * @param enrichers      宿主增强器（可为空）
     * @param maxSuggestions 单个增强器（每条业务规则）的建议条数上限（{@code <=0} 表示不限制）
     */
    public static Result assemble(IntentCatalogContext context,
            List<IntentCatalogEntry> pageEntries,
            List<IntentCatalogEntry> visibleEntries,
            List<IntentCatalogEnricher> enrichers,
            int maxSuggestions) {
        if (enrichers == null || enrichers.isEmpty()) {
            return Result.of(pageEntries);
        }
        Map<String, Integer> positionOf = new LinkedHashMap<>();
        for (int i = 0; i < pageEntries.size(); i++) {
            positionOf.put(pageEntries.get(i).id(), i);
        }
        Map<String, IntentCatalogEntry> visible = new LinkedHashMap<>();
        for (IntentCatalogEntry entry : visibleEntries) {
            visible.put(entry.id(), entry);
        }
        List<IntentCatalogEntry> entries = new ArrayList<>(pageEntries);
        List<IntentSuggestion> suggestions = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        int limit = maxSuggestions <= 0 ? Integer.MAX_VALUE : maxSuggestions;

        for (IntentCatalogEnricher enricher : enrichers) {
            for (Map.Entry<String, IntentBadge> badge : safeBadges(enricher, context).entrySet()) {
                Integer position = positionOf.get(badge.getKey());
                if (position == null || badge.getValue() == null) {
                    continue;
                }
                entries.set(position, entries.get(position).withBadge(badge.getValue()));
            }
            // 配额按规则独立：某条规则条目再多，也不会挤占其他规则的待办
            int accepted = 0;
            for (IntentSuggestion suggestion : safeSuggestions(enricher, context)) {
                if (accepted >= limit) {
                    break;
                }
                if (accept(suggestion, visible) && seen.add(keyOf(suggestion))) {
                    suggestions.add(suggestion);
                    accepted++;
                }
            }
        }
        return new Result(List.copyOf(entries), List.copyOf(suggestions));
    }

    /** 建议准入：意图可见 + 参数合法（未声明参数一律拒绝，避免静默塞脏数据）。 */
    private static boolean accept(IntentSuggestion suggestion, Map<String, IntentCatalogEntry> visible) {
        if (suggestion == null || isBlank(suggestion.intentId()) || isBlank(suggestion.title())) {
            return false;
        }
        IntentCatalogEntry entry = visible.get(suggestion.intentId());
        if (entry == null) {
            return false;
        }
        Map<String, Object> params = suggestion.params() == null ? Map.of() : suggestion.params();
        Map<String, Object> schema = entry.paramsSchema() == null ? Map.of() : entry.paramsSchema();
        Set<String> declared = declaredProperties(schema);
        for (String key : params.keySet()) {
            if (!declared.contains(key)) {
                return false;
            }
        }
        if (IntentSuggestion.KIND_ITEM.equals(suggestion.kind())) {
            for (String required : JsonSchemaValidator.requiredFields(schema)) {
                Object value = params.get(required);
                if (value == null || (value instanceof String text && text.isBlank())) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Set<String> declaredProperties(Map<String, Object> schema) {
        Object properties = schema.get("properties");
        if (properties instanceof Map<?, ?> map) {
            Set<String> keys = new LinkedHashSet<>();
            map.keySet().forEach(key -> keys.add(String.valueOf(key)));
            return keys;
        }
        return Set.of();
    }

    private static String keyOf(IntentSuggestion suggestion) {
        if (!isBlank(suggestion.id())) {
            return suggestion.id();
        }
        return suggestion.intentId() + "|" + (suggestion.params() == null ? "" : suggestion.params());
    }

    private static Map<String, IntentBadge> safeBadges(IntentCatalogEnricher enricher,
            IntentCatalogContext context) {
        try {
            Map<String, IntentBadge> badges = enricher.badges(context);
            return badges == null ? Map.of() : badges;
        } catch (Exception e) {
            warn("badges", enricher, e);
            return Map.of();
        }
    }

    private static List<IntentSuggestion> safeSuggestions(IntentCatalogEnricher enricher,
            IntentCatalogContext context) {
        try {
            List<IntentSuggestion> suggestions = enricher.suggestions(context);
            return suggestions == null ? List.of() : suggestions;
        } catch (Exception e) {
            warn("suggestions", enricher, e);
            return List.of();
        }
    }

    private static void warn(String method, IntentCatalogEnricher enricher, Exception e) {
        System.err.println("[intent-sdk] 目录增强 " + enricher.getClass().getSimpleName()
                + "#" + method + " 求值失败（已降级为无增强）: " + e.getMessage());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
