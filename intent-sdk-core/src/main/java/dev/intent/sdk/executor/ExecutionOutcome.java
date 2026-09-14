package dev.intent.sdk.executor;

import dev.intent.protocol.StepTrace;
import dev.intent.protocol.UsageInfo;

import java.util.List;
import java.util.Map;

/**
 * 执行器输出（执行器 SPI 的返回）。
 *
 * <p>{@code submitted=true} 表示结果已通过出参 Schema 校验（output 可信）；
 * 否则 {@code errorCode/errorMessage} 说明失败原因，运行时统一包装为
 * {@link dev.intent.protocol.IntentResult}。</p>
 */
public record ExecutionOutcome(
        boolean submitted,
        Map<String, Object> output,
        String errorCode,
        String errorMessage,
        List<StepTrace> steps,
        UsageInfo usage) implements java.io.Serializable {

    public ExecutionOutcome {
        steps = steps == null ? List.of() : List.copyOf(steps);
        usage = usage == null ? UsageInfo.ZERO : usage;
    }

    /** 校验通过的成功输出。 */
    public static ExecutionOutcome success(Map<String, Object> output, List<StepTrace> steps, UsageInfo usage) {
        return new ExecutionOutcome(true, output, null, null, steps, usage);
    }

    /** 校验未通过（重试耗尽）或执行器内部失败。 */
    public static ExecutionOutcome failure(String errorCode, String errorMessage,
            List<StepTrace> steps, UsageInfo usage) {
        return new ExecutionOutcome(false, null, errorCode, errorMessage, steps, usage);
    }
}
