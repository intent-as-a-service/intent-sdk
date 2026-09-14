package dev.intent.sdk.host;

import java.util.List;

/**
 * 意图可见性策略 SPI：判断某个身份能否使用某个意图。
 *
 * <p>入参是<b>已经解析好的角色要求</b>，而不是 IntentSpec——
 * 因为"配置优先于契约"这件事各宿主口径不同（后台配置的角色要盖过 spec 里声明的），
 * 解析归宿主，判定走这里，SDK 只提供缺省实现。</p>
 */
@FunctionalInterface
public interface IntentPermissionPolicy {

    /**
     * @param principal     当前身份（可能匿名）
     * @param requiredRoles  该意图要求的角色；空或含 {@code *} = 不限制
     */
    boolean canUse(IntentPrincipal principal, List<String> requiredRoles);

    /** 缺省实现：不限制角色则放行，否则要求角色交集非空。 */
    static IntentPermissionPolicy roleBased() {
        return (principal, requiredRoles) -> {
            if (requiredRoles == null || requiredRoles.isEmpty() || requiredRoles.contains("*")) {
                return true;
            }
            return principal != null && principal.hasAnyRole(requiredRoles);
        };
    }

    /** 全部放行（仅用于单机演示或已在网关层鉴权的场景）。 */
    static IntentPermissionPolicy allowAll() {
        return (principal, requiredRoles) -> true;
    }
}
