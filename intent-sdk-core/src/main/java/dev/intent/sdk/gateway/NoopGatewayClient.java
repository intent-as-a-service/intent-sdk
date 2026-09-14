package dev.intent.sdk.gateway;

import dev.intent.protocol.IntentRequest;
import dev.intent.protocol.IntentResult;
import dev.intent.protocol.IntentSpec;

/**
 * 空网关实现：未装配网关时的缺省值。
 * 任何 remote/composite 意图都会被分析器拦截为不可用，不会走到这里。
 */
public final class NoopGatewayClient implements GatewayClient {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public IntentResult invoke(IntentRequest request, IntentSpec spec) {
        throw new UnsupportedOperationException("网关未装配");
    }
}
