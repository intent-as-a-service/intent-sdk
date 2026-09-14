package dev.intent.sdk.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.intent.protocol.IntentSpec;
import dev.intent.sdk.schema.JsonSchemaValidator;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 结构化结果提交工具：意图执行引擎的输出契约执行者。
 *
 * <p>模型完成推理后必须调用本工具提交结果；参数即 IntentSpec 的输出 Schema，
 * 提交时立即校验——不合规的结果会被作为工具错误回传模型自行修正，通过校验的
 * 结果终止循环。这是"输出规范 → 可信"的关键闸门：未经校验的数据不会离开引擎。</p>
 */
public final class SubmitResultTool implements IntentTool {

    /** 引擎内部工具名。 */
    public static final String NAME = "submit_result";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final IntentSpec spec;
    private final AtomicReference<Map<String, Object>> captured = new AtomicReference<>();

    public SubmitResultTool(IntentSpec spec) {
        this.spec = spec;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "提交最终结构化结果。完成分析后必须调用本工具提交结果，不要用文本回复代替。";
    }

    @Override
    public Map<String, Object> parameters() {
        return spec.getOutputSchema();
    }

    @Override
    public IntentToolResult execute(String toolCallId, Map<String, Object> args, IntentToolContext context) {
        List<String> errors = JsonSchemaValidator.validate(spec.getOutputSchema(), args);
        if (!errors.isEmpty()) {
            return IntentToolResult.error("结果不符合输出规范，请修正后重新提交：\n- "
                    + String.join("\n- ", errors));
        }
        Map<String, Object> payload = MAPPER.convertValue(args, MAPPER.getTypeFactory()
                .constructMapType(Map.class, String.class, Object.class));
        captured.set(payload);
        return IntentToolResult.terminate(payload);
    }

    /** 是否已捕获通过校验的结果。 */
    public boolean isSubmitted() {
        return captured.get() != null;
    }

    /** 获取提交并通过校验的结果。 */
    public Map<String, Object> captured() {
        return captured.get();
    }
}