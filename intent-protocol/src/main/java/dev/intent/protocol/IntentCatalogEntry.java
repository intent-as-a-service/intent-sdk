package dev.intent.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/** 意图目录项：前端渲染意图按钮组所需的最小信息。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IntentCatalogEntry(
        String id,
        String name,
        String description,
        IntentScope scope,
        String targetSystem,
        String cardType,
        Map<String, Object> paramsSchema,
        List<ContextField> context,
        List<String> pages,
        List<String> aliases,
        IntentBadge badge) implements Serializable {

    /** 兼容构造：不含口语别名与动态提示（老调用方无需改动）。 */
    public IntentCatalogEntry(String id, String name, String description, IntentScope scope,
            String targetSystem, String cardType, Map<String, Object> paramsSchema,
            List<ContextField> context, List<String> pages) {
        this(id, name, description, scope, targetSystem, cardType, paramsSchema, context, pages, null, null);
    }

    /** 兼容构造：含动态提示但不含口语别名（目录增强的老调用方）。 */
    public IntentCatalogEntry(String id, String name, String description, IntentScope scope,
            String targetSystem, String cardType, Map<String, Object> paramsSchema,
            List<ContextField> context, List<String> pages, IntentBadge badge) {
        this(id, name, description, scope, targetSystem, cardType, paramsSchema, context, pages, null, badge);
    }

    /** 附加动态提示（目录增强用；其余字段原样保留）。 */
    public IntentCatalogEntry withBadge(IntentBadge newBadge) {
        return new IntentCatalogEntry(id, name, description, scope, targetSystem, cardType,
                paramsSchema, context, pages, aliases, newBadge);
    }
}
