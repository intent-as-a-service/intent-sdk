package dev.intent.sdk.store;

import dev.intent.protocol.ExecutionTraceRecord;
import dev.intent.protocol.IntentFeedback;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** 内存留痕存储（测试与小规模部署）。 */
public final class InMemoryTraceStore implements TraceStore {

    private final Map<String, ExecutionTraceRecord> records = new ConcurrentHashMap<>();
    private final List<IntentFeedback> feedbacks = new CopyOnWriteArrayList<>();

    @Override
    public void save(ExecutionTraceRecord record) {
        records.put(record.traceId(), record);
    }

    @Override
    public List<ExecutionTraceRecord> list(String userId, int limit) {
        return records.values().stream()
                .filter(r -> userId == null || userId.equals(r.userId()))
                .sorted(Comparator.comparingLong(ExecutionTraceRecord::startedAt).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public Optional<ExecutionTraceRecord> get(String traceId) {
        return Optional.ofNullable(records.get(traceId));
    }

    @Override
    public void saveFeedback(IntentFeedback feedback) {
        feedbacks.add(feedback);
    }

    @Override
    public List<IntentFeedback> listFeedback(String intentId, int limit) {
        return feedbacks.stream()
                .filter(f -> intentId == null || intentId.equals(f.intentId()))
                .limit(limit)
                .toList();
    }
}
