package dev.intent.sdk.gateway;

/**
 * 跨系统意图网关客户端（可组装能力点）。
 *
 * <p>SDK 通过本接口探测网关并调用远程意图；未装配时使用
 * {@link NoopGatewayClient}，分析器据此把 remote/composite 意图判为不可用。</p>
 */
public interface GatewayClient {

    /** 网关是否已装配且可达。 */
    boolean isAvailable();

    /** 调用目标系统意图接口（M2：携带网关换发的短时凭证）。 */
    dev.intent.protocol.IntentResult invoke(dev.intent.protocol.IntentRequest request,
            dev.intent.protocol.IntentSpec spec);
}
