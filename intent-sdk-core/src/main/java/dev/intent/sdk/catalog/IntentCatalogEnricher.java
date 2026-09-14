package dev.intent.sdk.catalog;

import dev.intent.protocol.IntentBadge;
import dev.intent.protocol.IntentSuggestion;

import java.util.List;
import java.util.Map;

/**
 * 意图目录增强 SPI：把宿主的业务事实变成<b>用户相关</b>的提示与建议。
 *
 * <p>业务系统实现该接口并声明为 Bean 即可接入（平台零改动），典型实现：
 * 「我负责的合同即将到期」→ 给 {@code crm.contract.risk-review} 挂徽标，
 * 并按合同逐条产出「合同「XX」还有 3 天到期」的建议。</p>
 *
 * <p>约定（实现方需遵守）：</p>
 * <ol>
 *   <li><b>事实来自宿主规则/查询，不经过 LLM</b>——数字与对象必须可解释、可复现；</li>
 *   <li>同步求值、必须快（目录接口高频调用）：只做轻量查询，重活交给缓存/后台任务；</li>
 *   <li>可抛错但不该抛错：装配器会隔离异常并降级为「无增强」，不影响意图菜单可用性；</li>
 *   <li>建议只做「准入内」的事：装配器会再次校验意图可见性与参数合法性，
 *       从这里拿不到越权或下架的意图。</li>
 * </ol>
 */
public interface IntentCatalogEnricher {

    /**
     * 动态提示：intentId → 徽标（如 {@code 2 份合同即将到期}）。
     * 只对当前目录中已存在的意图生效，返回空 Map = 无提示。
     */
    default Map<String, IntentBadge> badges(IntentCatalogContext context) {
        return Map.of();
    }

    /**
     * 动态建议：用户相关的可执行条目（条目型 params 齐备、聚合型由前端展开）。
     * 返回空列表 = 无建议。
     */
    default List<IntentSuggestion> suggestions(IntentCatalogContext context) {
        return List.of();
    }
}
