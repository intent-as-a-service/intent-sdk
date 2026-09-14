package dev.intent.sdk.tool;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工具调用结果。
 *
 * @param text      模型可见的文本结果（工具调用的 content）
 * @param data      结构化明细（随执行轨迹返回给前端渲染，不进入模型上下文）
 * @param error     是否失败（失败文本同样回传模型，让其自行纠正）
 * @param terminate 是否终止推理循环（submit_result 这类输出闸门工具使用）
 */
public record IntentToolResult(String text, Map<String, Object> data, boolean error, boolean terminate) {

    public IntentToolResult {
        // 结构化明细来自业务取数，null 是合法值（如"该客户无跟进记录"时的 lastFollowUpTime）：
        // Map.copyOf 会对 null 值抛 NPE，且异常无消息，表现为工具莫名失败。这里改为
        // 保留 null 的不可变副本，语义（不可变、随轨迹回传）不变。
        data = data == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(data));
    }

    /** 纯文本结果。 */
    public static IntentToolResult text(String text) {
        return new IntentToolResult(text, Map.of(), false, false);
    }

    /** 结构化结果（序列化为 JSON 文本回传模型，明细随轨迹返回）。 */
    public static IntentToolResult data(Map<String, Object> data) {
        return new IntentToolResult(null, data, false, false);
    }

    /** 失败结果（文本回传模型，让其自行修正后重试）。 */
    public static IntentToolResult error(String message) {
        return new IntentToolResult(message, Map.of(), true, false);
    }

    /** 终止循环的结果（输出闸门通过后使用）。 */
    public static IntentToolResult terminate(Map<String, Object> data) {
        return new IntentToolResult(null, data, false, true);
    }
}
