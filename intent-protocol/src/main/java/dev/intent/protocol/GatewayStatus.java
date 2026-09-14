package dev.intent.protocol;

/** 网关装配状态。 */
public enum GatewayStatus {
    /** 已装配网关，跨系统意图可用。 */
    ENABLED,
    /** 未装配网关，scope=remote/composite 的意图不可用。 */
    UNAVAILABLE
}
