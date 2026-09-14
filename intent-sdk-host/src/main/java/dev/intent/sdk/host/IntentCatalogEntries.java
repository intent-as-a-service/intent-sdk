package dev.intent.sdk.host;

import dev.intent.protocol.IntentCatalogEntry;
import dev.intent.protocol.IntentSpec;

/**
 * IntentSpec → 目录项 的转换：规则校验与目录渲染都要用同一份口径，
 * 所以放在 SDK 里统一下来，避免各宿主自己拼字段拼出偏差。
 */
public final class IntentCatalogEntries {

    private IntentCatalogEntries() {
    }

    public static IntentCatalogEntry of(IntentSpec spec) {
        if (spec == null) {
            return null;
        }
        return new IntentCatalogEntry(spec.getId(), spec.getName(), spec.getDescription(),
                spec.getScope(), spec.getTargetSystem(), spec.getCardType(),
                spec.getParamsSchema(), spec.getContext(), spec.getPages(),
                spec.getAliases() == null || spec.getAliases().isEmpty() ? null : spec.getAliases(),
                null);
    }
}
