package dev.intent.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.util.List;

/** 意图目录响应：含网关装配状态（前端据此渲染不可用态）。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CatalogResponse(String systemName, GatewayStatus gatewayStatus,
        List<IntentCatalogEntry> entries, List<IntentSuggestion> suggestions) implements Serializable {

    /** 兼容构造：不含动态建议（老调用方无需改动）。 */
    public CatalogResponse(String systemName, GatewayStatus gatewayStatus, List<IntentCatalogEntry> entries) {
        this(systemName, gatewayStatus, entries, List.of());
    }
}
