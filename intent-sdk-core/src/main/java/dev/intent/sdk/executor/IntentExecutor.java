package dev.intent.sdk.executor;

/**
 * 意图执行器 SPI：契约（IntentSpec）与执行能力解耦的扩展点。
 *
 * <p>内置 {@link #BUILTIN_ID}（{@code builtin-agent}，推理内核适配器提供，
 * 见 {@code intent-sdk-pi}）覆盖常规"提示词 + 宿主工具"型意图；
 * 宿主可通过实现本接口注册自定义执行器——
 * 例如：工作流引擎执行器、规则引擎执行器、多智能体编排执行器、RAG 检索执行器。
 * 意图规范通过 {@code executor: <id>} 声明由谁执行，缺省走内置推理循环。</p>
 *
 * <p>实现要求：无状态、可并发（每次运行独立的会话与中间状态）；
 * 不得抛出异常表示业务失败——用 {@link ExecutionOutcome#failure} 表达。</p>
 */
public interface IntentExecutor {

    /** 内置推理循环执行器标识（IntentSpec.executor 缺省值）。 */
    String BUILTIN_ID = "builtin-agent";

    /** 执行器唯一标识（IntentSpec.executor 按此路由）。 */
    String id();

    /** 执行一个意图。实现应自行控制耗时（超时由规范 policy.timeoutSeconds 约束）。 */
    ExecutionOutcome run(ExecutionRequest request);
}
