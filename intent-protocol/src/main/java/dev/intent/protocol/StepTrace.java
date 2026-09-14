package dev.intent.protocol;

import java.io.Serializable;

/** 单步执行轨迹（工具调用或一次模型推理）。 */
public record StepTrace(
        String kind,
        String name,
        String argsSummary,
        String resultSummary,
        boolean ok,
        String error,
        long durationMs) implements Serializable {

    public static final String KIND_TOOL = "tool";
    public static final String KIND_LLM = "llm";

    public static StepTrace tool(String name, String argsSummary, String resultSummary, boolean ok,
            String error, long durationMs) {
        return new StepTrace(KIND_TOOL, name, argsSummary, resultSummary, ok, error, durationMs);
    }

    public static StepTrace llm(String summary, long durationMs) {
        return new StepTrace(KIND_LLM, "llm-turn", null, summary, true, null, durationMs);
    }
}
