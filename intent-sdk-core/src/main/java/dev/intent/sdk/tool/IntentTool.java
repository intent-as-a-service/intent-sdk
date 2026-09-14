package dev.intent.sdk.tool;

import java.util.Map;

/**
 * 宿主工具的 SDK 自有契约：意图执行引擎可调用工具的唯一形态。
 *
 * <p>本接口刻意不含任何第三方推理框架类型——推理内核（pi-agent 等）通过
 * 适配器实现它，因此宿主只依赖本接口即可完成接入，SDK 的核心制品可以脱离
 * 任何具体 agent 框架独立发布。</p>
 *
 * <p>实现要求：进程内直调宿主能力（SDK / 内部 API），以当前调用栈的登录用户
 * 执行，权限与事务天然沿用宿主系统。实现可抛异常表示失败，执行引擎会把它
 * 转成错误工具结果回传模型。</p>
 */
public interface IntentTool {

    /** 工具名（IntentSpec.tools 白名单按此匹配，模型按此调用）。 */
    String name();

    /** 工具描述（进入模型提示词，直接决定模型是否会正确使用本工具）。 */
    String description();

    /** 入参 JSON Schema（模型据此产出参数，执行引擎据此校验）。 */
    Map<String, Object> parameters();

    /**
     * 执行工具调用。
     *
     * @param toolCallId 模型给出的调用 id（回传轨迹用）
     * @param args       已按 parameters() 校验的入参
     * @param context    调用上下文（协作式取消）
     */
    IntentToolResult execute(String toolCallId, Map<String, Object> args, IntentToolContext context)
            throws Exception;
}