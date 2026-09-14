package dev.intent.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.intent.protocol.IntentRequest;
import dev.intent.protocol.IntentResult;
import dev.intent.protocol.IntentSpec;
import dev.intent.sdk.gateway.GatewayClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * HTTP 网关客户端（可组装组件）。
 *
 * <p>宿主装配本 Bean 并配置网关地址后，IntentAnalyzer 即解锁 remote/composite
 * 意图；网关侧（intent-gateway-server，M2）负责目录路由、短时凭证换发与审计。</p>
 *
 * <p>M1 阶段网关服务端尚未启用，本客户端已具备完整调用契约，供联调与后续里程碑使用。</p>
 */
public final class HttpGatewayClient implements GatewayClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String baseUrl;
    private final String appKey;
    private final String appSecret;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public HttpGatewayClient(String baseUrl, String appKey, String appSecret) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/$", "");
        this.appKey = appKey;
        this.appSecret = appSecret;
    }

    @Override
    public boolean isAvailable() {
        return baseUrl != null && !baseUrl.isBlank();
    }

    @Override
    public IntentResult invoke(IntentRequest request, IntentSpec spec) {
        try {
            Map<String, Object> body = Map.of(
                    "intentId", request.intentId(),
                    "params", request.params(),
                    "context", request.context(),
                    "user", request.user() == null ? Map.of() : request.user(),
                    "traceId", request.traceId() == null ? "" : request.traceId());
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/gateway/intent/invoke"))
                    .header("Content-Type", "application/json")
                    .header("X-App-Key", appKey == null ? "" : appKey)
                    .header("X-App-Secret", appSecret == null ? "" : appSecret)
                    .timeout(Duration.ofSeconds(Math.max(10, spec.getPolicy().getTimeoutSeconds())))
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            return MAPPER.readValue(response.body(), IntentResult.class);
        } catch (Exception e) {
            return new IntentResult(request.traceId(), request.intentId(),
                    dev.intent.protocol.IntentStatus.FAILED, null, null,
                    new dev.intent.protocol.IntentError("GATEWAY_ERROR",
                            "网关调用失败: " + e.getMessage()),
                    null, null, 0);
        }
    }
}
