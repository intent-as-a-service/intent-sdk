package dev.intent.sdk.host.rule;

import dev.intent.sdk.catalog.IntentCatalogContext;

import java.util.List;
import java.util.Map;

/**
 * 多模块事实源聚合：每个业务模块各声明一个 {@link IntentFactProvider}，
 * 由平台侧聚合成一个喂给规则引擎。
 *
 * <p>分发规则：只问"声明支持该数据集"的提供者，按 Spring 注入顺序取第一个
 * 返回非 null 的结果（null = 本提供者没有这个数据集，继续往下问）。
 * 没有任何提供者认领时返回空集合——规则因此产出空徽标/空建议，
 * 而不是抛异常把整个目录接口打挂。</p>
 */
public final class CompositeIntentFactProvider implements IntentFactProvider {

    private final List<IntentFactProvider> providers;

    public CompositeIntentFactProvider(List<IntentFactProvider> providers) {
        this.providers = providers == null ? List.of() : List.copyOf(providers);
    }

    public boolean isEmpty() {
        return providers.isEmpty();
    }

    public int size() {
        return providers.size();
    }

    @Override
    public List<Map<String, Object>> rows(String datasetId, IntentCatalogContext context, int limit) {
        for (IntentFactProvider provider : providers) {
            if (!provider.supports(datasetId)) {
                continue;
            }
            List<Map<String, Object>> rows = provider.rows(datasetId, context, limit);
            if (rows != null) {
                return rows;
            }
        }
        return List.of();
    }

    @Override
    public int count(String datasetId, IntentCatalogContext context) {
        for (IntentFactProvider provider : providers) {
            if (!provider.supports(datasetId)) {
                continue;
            }
            int count = provider.count(datasetId, context);
            if (count >= 0) {
                return count;
            }
        }
        return 0;
    }

    /** 数据集并集：多模块各报各的，重复 id 以先注入者为准。 */
    @Override
    public List<DatasetSpec> datasets() {
        java.util.LinkedHashMap<String, DatasetSpec> merged = new java.util.LinkedHashMap<>();
        for (IntentFactProvider provider : providers) {
            for (DatasetSpec spec : provider.datasets()) {
                if (spec != null && spec.id() != null) {
                    merged.putIfAbsent(spec.id(), spec);
                }
            }
        }
        return List.copyOf(merged.values());
    }

    @Override
    public boolean supports(String datasetId) {
        for (IntentFactProvider provider : providers) {
            if (provider.supports(datasetId)) {
                return true;
            }
        }
        return false;
    }
}