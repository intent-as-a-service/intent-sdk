package dev.intent.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/** 意图执行结果（IntentResult）：本地与远程返回同构。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IntentResult(
        String traceId,
        String intentId,
        IntentStatus status,
        /** 出参 Schema 校验通过的结构化结果（标准信封：title/summary/blocks/followups）。 */
        Map<String, Object> output,
        /** 补全表单所需：缺失的必填参数名。 */
        List<String> missingParams,
        IntentError error,
        List<StepTrace> steps,
        UsageInfo usage,
        long durationMs) implements Serializable {
}
