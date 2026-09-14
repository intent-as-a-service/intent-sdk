package dev.intent.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * 意图执行位置声明（IntentSpec.scope）。
 *
 * <ul>
 *   <li>{@code LOCAL} —— 仅在本系统上下文内执行，无网关也可用；</li>
 *   <li>{@code REMOTE} —— 由目标系统的意图接口执行，必须装配网关；</li>
 *   <li>{@code COMPOSITE} —— 本地编排中混合调用远程意图，必须装配网关。</li>
 * </ul>
 */
public enum IntentScope {
    LOCAL,
    REMOTE,
    COMPOSITE;

    @JsonCreator
    public static IntentScope from(String value) {
        return valueOf(value.trim().toUpperCase());
    }
}
