package dev.intent.protocol;

/** 意图执行结果状态。 */
public enum IntentStatus {
    /** 执行成功，output 为通过出参 Schema 校验的结构化结果。 */
    SUCCESS,
    /** 缺少必填参数，需前端渲染补全表单后重新提交。 */
    NEED_INPUT,
    /** 执行失败，见 error。 */
    FAILED
}
