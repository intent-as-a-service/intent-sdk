package dev.intent.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;

/** 用户对一次意图执行的评价反馈。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IntentFeedback(String traceId, String intentId, String userId, String rating, String comment)
        implements Serializable {
}
