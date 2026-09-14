package dev.intent.sdk.tool;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.function.BiFunction;

/**
 * 宿主工具的 Lambda 便捷实现：业务系统把内部 SDK / 内部 API 包装成
 * 一个 {@link IntentTool}，即可被意图执行引擎调用。
 *
 * <p>进程内直调——以当前登录用户上下文在宿主系统内执行，权限与事务
 * 天然沿用宿主系统。</p>
 */
public final class LambdaHostTool implements IntentTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String name;
    private final String description;
    private final Map<String, Object> parameters;
    private final BiFunction<Map<String, Object>, IntentToolContext, IntentToolResult> executor;

    public LambdaHostTool(String name, String description, Map<String, Object> parameters,
            BiFunction<Map<String, Object>, IntentToolContext, IntentToolResult> executor) {
        this.name = name;
        this.description = description;
        this.parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        this.executor = executor;
    }

    /** 文本结果便捷工厂。 */
    public static LambdaHostTool ofText(String name, String description, Map<String, Object> parameters,
            BiFunction<Map<String, Object>, IntentToolContext, String> executor) {
        return new LambdaHostTool(name, description, parameters,
                (args, context) -> IntentToolResult.text(executor.apply(args, context)));
    }

    /** 结构化结果便捷工厂（明细序列化为 JSON 文本回传模型，并随轨迹返回前端）。 */
    public static LambdaHostTool ofData(String name, String description, Map<String, Object> parameters,
            BiFunction<Map<String, Object>, IntentToolContext, Map<String, Object>> executor) {
        return new LambdaHostTool(name, description, parameters,
                (args, context) -> IntentToolResult.data(executor.apply(args, context)));
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public Map<String, Object> parameters() {
        return parameters;
    }

    @Override
    public IntentToolResult execute(String toolCallId, Map<String, Object> args, IntentToolContext context) {
        return executor.apply(args, context);
    }

    /** 结构化明细的 JSON 文本形式（供推理内核适配器回传模型）。 */
    public static String toJson(Map<String, Object> data) {
        try {
            return MAPPER.writeValueAsString(data);
        } catch (Exception e) {
            return String.valueOf(data);
        }
    }
}