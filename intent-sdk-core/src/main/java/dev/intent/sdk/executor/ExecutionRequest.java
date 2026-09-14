package dev.intent.sdk.executor;

import dev.intent.protocol.IntentSpec;
import dev.intent.protocol.UserInfo;
import dev.intent.sdk.tool.IntentTool;

import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * 一次意图执行请求（执行器 SPI 的输入）。
 *
 * @param spec          意图规范（契约恒定）
 * @param params        已通过入参校验的任务参数
 * @param context       页面上下文（前端随请求携带）
 * @param user          发起用户（可空 = 系统调用）
 * @param traceId       执行留痕 id（由运行时生成）
 * @param toolDecorator 宿主上下文装饰器（可空）；执行器应在工具线程上应用，
 *                      保证宿主工具的数据权限 / 租户 / 登录用户随调用栈生效
 */
public record ExecutionRequest(
        IntentSpec spec,
        Map<String, Object> params,
        Map<String, Object> context,
        UserInfo user,
        String traceId,
        UnaryOperator<IntentTool> toolDecorator) implements java.io.Serializable {

    public ExecutionRequest {
        params = params == null ? Map.of() : Map.copyOf(params);
        context = context == null ? Map.of() : Map.copyOf(context);
    }
}
