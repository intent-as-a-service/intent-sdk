package dev.intent.sdk.host;

import dev.intent.sdk.tool.IntentTool;

import java.util.function.UnaryOperator;

/**
 * 上下文桥 SPI：把宿主的<b>安全上下文 / 租户上下文</b>透传到执行器的工作线程。
 *
 * <p>为什么需要它：执行工具时可能已经离开原始请求线程，
 * 若不显式把 SecurityContext / TenantContext 搬过去，工具里的数据权限会失效
 * （最典型的症状是"越权查到别人的数据"或"查不到任何数据"）。</p>
 *
 * <p>宿主实现通常是"捕获当前上下文 → 在工作线程里恢复 → 用完还原"，
 * 参考 ruoyi-office 的 {@code HostContextBridge}。</p>
 */
@FunctionalInterface
public interface IntentContextBridge {

    /** 工具装饰器：返回一个在正确上下文里执行原工具的新工具。 */
    UnaryOperator<IntentTool> toolDecorator();

    /** 无线程池、无租户的实现：直接透传（SDK 默认，进程内单线程场景够用）。 */
    static IntentContextBridge passthrough() {
        return () -> UnaryOperator.identity();
    }
}
