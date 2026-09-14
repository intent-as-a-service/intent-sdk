package dev.intent.sdk.tool;

/**
 * 工具调用上下文：目前只承载协作式取消。
 *
 * <p>宿主工具若可能长时间运行，应在循环中检查 {@link #cancelled()} 并及时返回。</p>
 */
public interface IntentToolContext {

    /** 本次执行是否已被取消（超时熔断 / 调用方主动终止）。 */
    boolean cancelled();

    /** 永不取消的上下文（默认实现）。 */
    static IntentToolContext none() {
        return () -> false;
    }
}