package dev.intent.sdk.pi;

import dev.intent.sdk.tool.IntentTool;
import dev.intent.sdk.tool.IntentToolContext;
import dev.intent.sdk.tool.IntentToolResult;
import dev.intent.sdk.tool.LambdaHostTool;
import dev.pi.ai.AbortSignal;
import dev.pi.ai.TextContent;
import dev.pi.agent.AgentTool;

import java.util.List;
import java.util.Map;

/**
 * 推理内核工具适配器：把 SDK 自有工具契约 {@link IntentTool} 适配为 pi-agent 的
 * {@link AgentTool}。
 *
 * <p>本模块是唯一知道 pi-agent 工具形态的地方——换推理内核时只需再写一个
 * 同形态的适配器，意图规范、宿主工具与执行编排都零改动。</p>
 */
final class PiToolAdapter implements AgentTool {

    private static final System.Logger LOG = System.getLogger(PiToolAdapter.class.getName());

    private final IntentTool delegate;

    private PiToolAdapter(IntentTool delegate) {
        this.delegate = delegate;
    }

    static PiToolAdapter of(IntentTool tool) {
        return new PiToolAdapter(tool);
    }

    /** 把 pi 的取消令牌转成 SDK 的调用上下文。 */
    static IntentToolContext context(AbortSignal signal) {
        return () -> signal != null && signal.isAborted();
    }

    /** 工具结果中模型可见的文本（结构化结果序列化为 JSON 文本）。 */
    static String textOf(IntentToolResult result) {
        if (result.text() != null) {
            return result.text();
        }
        return LambdaHostTool.toJson(result.data());
    }

    @Override
    public String name() {
        return delegate.name();
    }

    @Override
    public String description() {
        return delegate.description();
    }

    @Override
    public Map<String, Object> parameters() {
        return delegate.parameters();
    }

    @Override
    public AgentToolResult execute(String toolCallId, Map<String, Object> args, AbortSignal signal)
            throws Exception {
        try {
            IntentToolResult result = delegate.execute(toolCallId, args, context(signal));
            return new AgentToolResult(List.of(new TextContent(textOf(result))), result.data(),
                    null, null, result.terminate());
        } catch (Error error) {
            // Error（如宿主工具触发的 ExceptionInInitializerError / NoClassDefFoundError）
            // 会被推理循环的 Exception 兜底漏掉，从而静默中断整轮执行。
            // 这里统一转成可捕获异常，交由循环降级为"工具执行失败"回传模型。
            LOG.log(System.Logger.Level.ERROR, "[intent] 宿主工具抛出 Error: " + delegate.name(), error);
            throw new IllegalStateException("工具执行异常（" + delegate.name() + "）: " + error, error);
        }
    }
}
