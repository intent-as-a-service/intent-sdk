package dev.intent.sdk.host;

import java.util.List;

/**
 * 意图的运行期配置（后台可改的那部分）：上架开关 + 可见角色 + 执行器引用。
 *
 * <p>与 {@code IntentSpec} 分开是因为二者变更频率与责任人不同：
 * spec 是开发写的契约，配置是运营在后台点的开关。</p>
 *
 * @param intentId 意图编号
 * @param enabled  是否上架（false = 目录与执行都拒绝）
 * @param roles    可见角色编码；空列表或含 {@code *} = 不限制
 * @param remark   备注
 * @param executor 指定执行器 id（可空 = 用 spec 声明或内置执行器）
 */
public record IntentConfigState(
        String intentId, boolean enabled, List<String> roles, String remark, String executor) {

    public IntentConfigState {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }

    /** 缺省配置：上架 + 不限制角色。 */
    public static IntentConfigState enabledFor(String intentId) {
        return new IntentConfigState(intentId, true, List.of("*"), null, null);
    }

    public boolean unrestricted() {
        return roles.isEmpty() || roles.contains("*");
    }
}
