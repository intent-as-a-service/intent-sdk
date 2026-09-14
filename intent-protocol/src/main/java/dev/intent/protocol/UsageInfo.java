package dev.intent.protocol;

import java.io.Serializable;

/** Token 用量汇总。 */
public record UsageInfo(long inputTokens, long outputTokens, long totalTokens) implements Serializable {

    public static final UsageInfo ZERO = new UsageInfo(0, 0, 0);
}
