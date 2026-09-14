package dev.intent.sdk.host.repository;

import dev.intent.sdk.host.IntentConfigRepository;
import dev.intent.sdk.host.IntentConfigState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 内存配置仓储：进程内有效，重启还原（零建表模式的最轻实现）。 */
public final class InMemoryIntentConfigRepository implements IntentConfigRepository {

    private final Map<String, IntentConfigState> store = new ConcurrentHashMap<>();

    @Override
    public List<IntentConfigState> findAll() {
        return List.copyOf(new ArrayList<>(new LinkedHashMap<>(store).values()));
    }

    @Override
    public void save(IntentConfigState state) {
        if (state != null && state.intentId() != null) {
            store.put(state.intentId(), state);
        }
    }
}
