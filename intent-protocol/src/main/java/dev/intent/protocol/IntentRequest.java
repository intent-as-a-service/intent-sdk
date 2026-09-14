package dev.intent.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.util.Map;

/** 意图执行请求：点击意图按钮时由前端组件提交。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IntentRequest(
        String intentId,
        Map<String, Object> params,
        Map<String, Object> context,
        UserInfo user,
        String traceId) implements Serializable {

    public IntentRequest {
        params = params == null ? Map.of() : Map.copyOf(params);
        context = context == null ? Map.of() : Map.copyOf(context);
    }
}
