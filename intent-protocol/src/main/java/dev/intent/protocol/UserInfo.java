package dev.intent.protocol;

import java.io.Serializable;
import java.util.Map;

/**
 * 发起意图调用的用户信息（来自宿主系统登录态）。
 *
 * <p>本地执行时供宿主工具读取（HostThreadContext）；跨系统执行时由网关映射进短时凭证。</p>
 */
public record UserInfo(String id, String name, String deptName, Map<String, Object> extra) implements Serializable {

    public UserInfo {
        extra = extra == null ? Map.of() : Map.copyOf(extra);
    }

    public static UserInfo of(String id, String name) {
        return new UserInfo(id, name, null, Map.of());
    }
}
