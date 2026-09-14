package dev.intent.sdk.executor;

/**
 * 意图声明的执行器未注册（IntentSpec.executor 指向了不存在的执行器）。
 *
 * <p>运行时将其映射为 {@code EXECUTOR_NOT_FOUND}——区别于入参校验失败
 * （VALIDATION_ERROR），提示管理员修复意图的执行器引用而非用户参数。
 * 执行器缺失时意图不可执行：明确报错，不静默降级到默认执行器。</p>
 */
public class ExecutorNotFoundException extends IllegalStateException {

    public ExecutorNotFoundException(String message) {
        super(message);
    }
}
