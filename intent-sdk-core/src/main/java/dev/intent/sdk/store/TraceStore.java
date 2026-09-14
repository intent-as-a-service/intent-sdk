package dev.intent.sdk.store;

import dev.intent.protocol.ExecutionTraceRecord;
import dev.intent.protocol.IntentFeedback;

import java.util.List;
import java.util.Optional;

/**
 * 执行留痕存储：可信四道防线之"执行留痕"的落地。
 *
 * <p>M1 提供内存与 JSONL 文件实现；规模化阶段可替换为数据库实现（接口不变）。</p>
 */
public interface TraceStore {

    void save(ExecutionTraceRecord record);

    /** 按用户查询最近执行（userId 为空查全部）。 */
    List<ExecutionTraceRecord> list(String userId, int limit);

    Optional<ExecutionTraceRecord> get(String traceId);

    void saveFeedback(IntentFeedback feedback);

    List<IntentFeedback> listFeedback(String intentId, int limit);
}
