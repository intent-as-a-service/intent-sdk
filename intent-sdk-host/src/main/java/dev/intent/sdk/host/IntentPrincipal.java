package dev.intent.sdk.host;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 当前身份：SDK 需要知道的关于"你是谁"的最小集合。
 *
 * <p>刻意只保留 userId / userName / tenantId / roles 四项——
 * 多一项都会让宿主耦合进来越深，而目录过滤与鉴权只需要这四项。</p>
 *
 * @param userId   用户编号（可空 = 匿名）
 * @param userName 用户昵称（可空）
 * @param tenantId 租户编号（可空 = 单租户）
 * @param roles    角色编码集合（保持声明顺序，便于测试与排查）
 */
public record IntentPrincipal(String userId, String userName, String tenantId, Set<String> roles) {

    public IntentPrincipal {
        roles = roles == null
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(roles));
    }

    public static IntentPrincipal anonymous() {
        return new IntentPrincipal(null, null, null, Set.of());
    }

    public static IntentPrincipal of(String userId, String userName, String tenantId,
            Collection<String> roles) {
        return new IntentPrincipal(userId, userName, tenantId,
                roles == null ? Set.of() : new LinkedHashSet<>(roles));
    }

    public boolean isAnonymous() {
        return userId == null || userId.isBlank();
    }

    public boolean hasRole(String role) {
        return role != null && roles.contains(role);
    }

    public boolean hasAnyRole(Collection<String> required) {
        if (required == null || required.isEmpty()) {
            return true;
        }
        for (String role : required) {
            if (roles.contains(role)) {
                return true;
            }
        }
        return false;
    }

    /** 用户编号转 Long（宿主普遍用 Long 主键）；非法或匿名返回 null。 */
    public Long userIdAsLong() {
        if (isAnonymous()) {
            return null;
        }
        try {
            return Long.valueOf(userId.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
