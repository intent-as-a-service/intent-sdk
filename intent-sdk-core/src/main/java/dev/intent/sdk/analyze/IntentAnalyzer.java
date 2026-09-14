package dev.intent.sdk.analyze;

import dev.intent.protocol.IntentSpec;
import dev.intent.protocol.IntentScope;

/**
 * 意图分析器：点击意图后先判断执行位置（规范化的分流闸）。
 *
 * <ul>
 *   <li>{@code LOCAL} —— 本系统上下文内闭环执行；</li>
 *   <li>{@code REMOTE} —— 经网关路由至目标系统的意图接口执行（需已装配网关）；</li>
 *   <li>{@code UNAVAILABLE} —— 意图不可用（如未装配网关时的 remote/composite 意图），
 *       调用方必须显式提示，不允许静默失败。</li>
 * </ul>
 */
public final class IntentAnalyzer {

    /** 分流结论。 */
    public enum Route { LOCAL, REMOTE, UNAVAILABLE }

    private final boolean gatewayEnabled;

    public IntentAnalyzer(boolean gatewayEnabled) {
        this.gatewayEnabled = gatewayEnabled;
    }

    public Route decide(IntentSpec spec) {
        IntentScope scope = spec.getScope();
        if (scope == IntentScope.LOCAL) {
            return Route.LOCAL;
        }
        // remote / composite 依赖网关
        return gatewayEnabled ? Route.REMOTE : Route.UNAVAILABLE;
    }

    public boolean gatewayEnabled() {
        return gatewayEnabled;
    }
}
