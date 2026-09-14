package dev.intent.sdk.executor;

import java.util.List;

/** 执行器档案校验异常：携带全部档案错误。 */
public class ExecutorProfileException extends RuntimeException {

    private final List<String> errors;

    public ExecutorProfileException(String profileId, List<String> errors) {
        super("执行器档案不合法 [" + profileId + "]: " + String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> getErrors() {
        return errors;
    }
}
