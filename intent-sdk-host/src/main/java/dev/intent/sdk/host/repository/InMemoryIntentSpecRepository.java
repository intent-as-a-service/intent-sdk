package dev.intent.sdk.host.repository;

import dev.intent.protocol.IntentSpec;
import dev.intent.sdk.host.IntentSpecRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 内存意图仓储：种子加载进内存后可增删改，重启还原。
 *
 * <p>写入时保持插入顺序，{@link #findAll()} 结果稳定——
 * 目录渲染顺序依赖它，用无序集合会导致界面顺序每次刷新都在跳。</p>
 */
public final class InMemoryIntentSpecRepository implements IntentSpecRepository {

    private final Map<String, IntentSpec> specs = new LinkedHashMap<>();
    private final Set<String> builtin = new LinkedHashSet<>();

    @Override
    public synchronized List<IntentSpec> findAll() {
        return List.copyOf(new ArrayList<>(specs.values()));
    }

    @Override
    public synchronized void upsert(IntentSpec spec, String source) {
        if (spec == null || spec.getId() == null || spec.getId().isBlank()) {
            throw new IllegalArgumentException("意图编号不能为空");
        }
        specs.put(spec.getId(), spec);
        if ("builtin".equals(source)) {
            builtin.add(spec.getId());
        } else {
            builtin.remove(spec.getId());
        }
    }

    @Override
    public synchronized boolean delete(String intentId) {
        if (intentId == null) {
            return false;
        }
        builtin.remove(intentId);
        return specs.remove(intentId) != null;
    }

    @Override
    public synchronized String sourceOf(String intentId) {
        return builtin.contains(intentId) ? "builtin" : "custom";
    }

    /** 只读快照，便于测试断言。 */
    public synchronized Map<String, IntentSpec> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(specs));
    }
}
