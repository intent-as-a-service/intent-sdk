package dev.intent.protocol;

/** 意图错误码（语义固定，前端据此渲染提示）。 */
public final class IntentErrorCodes {

    /** 缺少必填参数，需表单补全。 */
    public static final String MISSING_PARAMS = "MISSING_PARAMS";
    /** 入参未通过 Schema 校验。 */
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    /** 意图需要跨系统网关，但当前未装配。 */
    public static final String REMOTE_UNAVAILABLE = "REMOTE_UNAVAILABLE";
    /** LLM 调用失败。 */
    public static final String LLM_ERROR = "LLM_ERROR";
    /** 工具执行失败。 */
    public static final String TOOL_ERROR = "TOOL_ERROR";
    /** 输出未通过出参 Schema 校验（含重试后仍失败）。 */
    public static final String OUTPUT_INVALID = "OUTPUT_INVALID";
    /** 执行超时。 */
    public static final String TIMEOUT = "TIMEOUT";
    /** 意图不存在。 */
    public static final String INTENT_NOT_FOUND = "INTENT_NOT_FOUND";
    /** 意图声明的执行器未注册（契约与执行配置不一致，管理员需修复 executor 引用）。 */
    public static final String EXECUTOR_NOT_FOUND = "EXECUTOR_NOT_FOUND";
    /** 当前用户无权使用该意图。 */
    public static final String FORBIDDEN = "FORBIDDEN";

    /** MVP 阶段暂未实现的功能。 */
    public static final String MVP_NOT_IMPLEMENTED = "MVP_NOT_IMPLEMENTED";

    private IntentErrorCodes() {
    }
}
