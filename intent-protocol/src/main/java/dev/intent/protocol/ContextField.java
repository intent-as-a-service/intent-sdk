package dev.intent.protocol;

/**
 * 页面上下文字段声明：意图需要前端从当前页面自动采集的实体。
 *
 * @param key      上下文键（如 customerId），前端采集后并入请求参数
 * @param title    展示名
 * @param required 页面上无法取得时是否允许缺失（缺失则进入表单补全）
 */
public record ContextField(String key, String title, boolean required) {
}
