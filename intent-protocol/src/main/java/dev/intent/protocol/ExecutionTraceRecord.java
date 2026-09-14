package dev.intent.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/** 执行留痕记录：持久化与历史查询使用的完整执行档案。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExecutionTraceRecord(
        String traceId,
        String intentId,
        String intentName,
        String userId,
        String userName,
        long startedAt,
        long durationMs,
        IntentStatus status,
        Map<String, Object> params,
        Map<String, Object> output,
        IntentError error,
        List<StepTrace> steps) implements Serializable {
}
