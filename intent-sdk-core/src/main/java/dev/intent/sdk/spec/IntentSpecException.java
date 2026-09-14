package dev.intent.sdk.spec;

import dev.intent.protocol.IntentSpec;

import java.util.List;

/** 意图规范校验异常：携带全部规范错误。 */
public class IntentSpecException extends RuntimeException {

    private final List<String> errors;

    public IntentSpecException(String specId, List<String> errors) {
        super("意图规范不合法 [" + specId + "]: " + String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> getErrors() {
        return errors;
    }
}
