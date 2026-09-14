package dev.intent.sdk.host.rule;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * 一条声明式事实规则 = 查询 + 条件 + 输出模板 + 参数映射。
 *
 * <p>业务团队只写这个 YAML，不写 Java：</p>
 * <pre>
 * - id: crm.contract.expiring
 *   intent: crm.contract.risk-review
 *   dataset: crm.expiring-contracts
 *   scope:   { page: ["crm/*", ""] }
 *   filter:
 *     all:
 *       - { op: gte, left: row.endTime, right: now() }
 *       - { op: lte, left: daysUntil(row.endTime), right: 7 }
 *   badge:   { text: "{{matched}} 份合同即将到期", level: warning }
 *   items:
 *     limit: 5
 *     title: "合同「{{row.name}}」{{row.endTime | expiryText}}"
 *     reason: "我负责的合同临近到期，建议提前审查履约与续约风险"
 *     params:
 *       contractId: { from: row.id, type: string }
 *       focus:      { value: "到期与续约风险" }
 * </pre>
 *
 * @param id      规则编号（日志与排查用）
 * @param intentId 点击后执行的意图（加载期校验存在性与参数合法性）
 * @param dataset 事实数据集标识（交给 {@link IntentFactProvider} 取数）
 * @param scope   触发范围：哪些页面、哪个对象类型
 * @param filter  行过滤条件（空 = 全取）
 * @param badge   聚合产出（可空）
 * @param items   条目产出（可空）
 */
public record IntentSuggestionRule(
        String id,
        @JsonProperty("intent") String intentId,
        String dataset,
        Scope scope,
        RuleCondition filter,
        Badge badge,
        Items items) {

    /**
     * 触发范围。
     *
     * @param page       页面前缀白名单（空 = 所有页面；空串 = 意图中心）；支持 {@code crm/*}
     * @param objectType 要求的当前对象类型（空 = 不要求；设置后必须上下文带同类型对象）
     */
    public record Scope(List<String> page, String objectType) {

        public Scope {
            page = page == null ? List.of() : List.copyOf(page);
        }

        public boolean acceptsPage(String currentPage) {
            if (page.isEmpty()) {
                return true;
            }
            String actual = currentPage == null ? "" : currentPage;
            for (String pattern : page) {
                if (pattern == null) {
                    continue;
                }
                if (pattern.endsWith("*")) {
                    if (actual.startsWith(pattern.substring(0, pattern.length() - 1))) {
                        return true;
                    }
                } else if (pattern.equals(actual)) {
                    return true;
                }
            }
            return false;
        }
    }

    /** 徽标：只显示数字与级别，不逐条展开。 */
    public record Badge(String text, String level) {
    }

    /**
     * 条目：逐条可执行。
     *
     * @param limit  最多产出几条（各自独立配额，防止一条规则挤掉别的规则）
     * @param params 参数映射：必须命中目标意图入参 Schema（加载期校验）
     */
    public record Items(int limit, String title, String subtitle, String reason,
                        Map<String, ParamMapping> params, String dedupKey) {

        public Items {
            params = params == null ? Map.of() : Map.copyOf(params);
        }

        public int effectiveLimit() {
            return limit <= 0 ? 5 : limit;
        }
    }

    /**
     * 参数映射：{@code from} 从行/上下文取值，{@code value} 为常量，二者取其一。
     *
     * @param type 目标类型（string / number / boolean），用于把数字型主键转成 Schema 要求的字符串
     */
    public record ParamMapping(String from, Object value, String type) {
    }

    public boolean hasBadge() {
        return badge != null && badge.text() != null && !badge.text().isBlank();
    }

    public boolean hasItems() {
        return items != null;
    }

    /** 规则自检：缺关键字段的规则在加载期就该被拒。 */
    public List<String> selfCheck() {
        List<String> problems = new java.util.ArrayList<>();
        if (id == null || id.isBlank()) {
            problems.add("缺少 id");
        }
        if (intentId == null || intentId.isBlank()) {
            problems.add("缺少 intent");
        }
        if (dataset == null || dataset.isBlank()) {
            problems.add("缺少 dataset");
        }
        if (!hasBadge() && !hasItems()) {
            problems.add("badge 与 items 至少要有一个");
        }
        if (hasItems()) {
            if (items.title() == null || items.title().isBlank()) {
                problems.add("items.title 不能为空");
            }
            if (items.reason() == null || items.reason().isBlank()) {
                problems.add("items.reason 不能为空（每个入口都要说得出为什么）");
            }
        }
        return problems;
    }
}
