package dev.intent.protocol;

import java.io.Serializable;

/** 执行错误信息。 */
public record IntentError(String code, String message) implements Serializable {
}
