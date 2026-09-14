package dev.intent.sdk.pi;

import dev.intent.sdk.executor.ExecutionOutcome;
import dev.intent.sdk.executor.ExecutionRequest;
import dev.intent.sdk.executor.ExecutorNotFoundException;
import dev.intent.sdk.executor.ExecutorProfile;
import dev.intent.sdk.executor.ExecutorProfileException;
import dev.intent.sdk.executor.ExecutorProfileValidator;
import dev.intent.sdk.executor.IntentExecutor;
import dev.intent.sdk.executor.KnowledgeRetriever;

import dev.intent.protocol.ExecutionTraceRecord;
import dev.intent.protocol.IntentError;
import dev.intent.protocol.IntentErrorCodes;
import dev.intent.protocol.IntentResult;
import dev.intent.protocol.IntentScope;
import dev.intent.protocol.IntentSpec;
import dev.intent.protocol.IntentStatus;
import dev.intent.protocol.StepTrace;
import dev.intent.protocol.UsageInfo;
import dev.intent.protocol.UserInfo;
import dev.intent.sdk.executor.ExecutionOutcome;
import dev.intent.sdk.executor.ExecutionRequest;
import dev.intent.sdk.executor.ExecutorNotFoundException;
import dev.intent.sdk.executor.IntentExecutor;
import dev.intent.sdk.gateway.GatewayClient;
import dev.intent.sdk.gateway.NoopGatewayClient;
import dev.intent.sdk.llm.LlmConfig;
import dev.intent.sdk.schema.JsonSchemaValidator;
import dev.intent.sdk.store.TraceStore;
import dev.intent.sdk.tool.HostToolRegistry;
import dev.intent.sdk.tool.IntentTool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 意图运行时：执行编排层——契约校验、执行器路由、结果规范化、执行留痕。
 *
 * <p>执行内核已剥离为 {@link IntentExecutor} SPI（内置 {@link AgentExecutor}），
 * 本类只做与执行方式无关的通用编排：</p>
 * <ol>
 *   <li>入参校验：缺必填参数 → NEED_INPUT（前端渲染补全表单）；不符 Schema → VALIDATION_ERROR；</li>
 *   <li>scope 路由：REMOTE/COMPOSITE 需要网关，未装配 → REMOTE_UNAVAILABLE；</li>
 *   <li>执行器路由：spec.executor 指向的执行器执行（缺省 builtin-agent）；
 *       执行器未注册 → EXECUTOR_NOT_FOUND（意图不可执行，明确报错而非静默降级）；</li>
 *   <li>结果规范化：统一包装为 {@link IntentResult}（traceId 生成、错误码语义化）；</li>
 *   <li>执行留痕：每次执行（无论成败）写入 {@link TraceStore}（装配了才写），可回放、可审计。</li>
 * </ol>
 *
 * <p>推荐装配方式：{@link IntentRuntimeConfig} 描述配置（含自定义执行器与留痕存储），
 * {@link #create(IntentRuntimeConfig)} 构建运行时；构造器保留供简单场景直用。</p>
 */
public class IntentRuntime {

    private final AgentExecutor builtinExecutor;
    private final Map<String, IntentExecutor> executors = new LinkedHashMap<>();
    private final GatewayClient gateway;
    /** 执行留痕存储（null = 不落盘，步骤轨迹仅随 IntentResult 返回）。 */
    private final TraceStore traceStore;

    public IntentRuntime(LlmConfig llm, HostToolRegistry tools,
            Supplier<List<IntentSpec>> specCatalog, List<IntentExecutor> customExecutors,
            GatewayClient gateway, int maxTurns, int outputMaxRetries) {
        this(llm, tools, specCatalog, customExecutors, gateway, null, maxTurns, outputMaxRetries);
    }

    /**
     * @param traceStore 执行留痕存储（可空 = 不落盘）
     */
    public IntentRuntime(LlmConfig llm, HostToolRegistry tools,
            Supplier<List<IntentSpec>> specCatalog, List<IntentExecutor> customExecutors,
            GatewayClient gateway, TraceStore traceStore, int maxTurns, int outputMaxRetries) {
        this.builtinExecutor = new AgentExecutor(llm, tools, specCatalog, maxTurns, outputMaxRetries);
        this.executors.put(AgentExecutor.BUILTIN_ID, builtinExecutor);
        if (customExecutors != null) {
            for (IntentExecutor executor : customExecutors) {
                if (AgentExecutor.BUILTIN_ID.equals(executor.id())) {
                    throw new IllegalArgumentException(
                            "执行器 id 保留: " + AgentExecutor.BUILTIN_ID + "（自定义执行器不可覆盖内置推理循环）");
                }
                executors.put(executor.id(), executor);
            }
        }
        this.gateway = gateway == null ? new NoopGatewayClient() : gateway;
        this.traceStore = traceStore;
    }

    /** 便捷重载：无自定义执行器、无网关、不留痕。 */
    public IntentRuntime(LlmConfig llm, HostToolRegistry tools,
            Supplier<List<IntentSpec>> specCatalog, int maxTurns, int outputMaxRetries) {
        this(llm, tools, specCatalog, List.of(), null, null, maxTurns, outputMaxRetries);
    }

    /** 推荐入口：从配置构建运行时（配置集中管理执行器、网关、留痕、限额）。 */
    public static IntentRuntime create(IntentRuntimeConfig config) {
        return new IntentRuntime(config.llm(), config.tools(), config.specCatalog(),
                config.customExecutors(), config.gateway(), config.traceStore(),
                config.maxTurns(), config.outputMaxRetries());
    }

    /**
     * 执行一个意图。任何失败都以 FAILED 结果返回（不抛异常），业务侧无需捕获。
     *
     * @param toolDecorator 宿主上下文装饰器（可空，见 ExecutionRequest）
     */
    public IntentResult execute(IntentSpec spec, Map<String, Object> params, Map<String, Object> context,
            UserInfo user, java.util.function.UnaryOperator<IntentTool> toolDecorator) {
        String traceId = UUID.randomUUID().toString();
        long startedAt = System.currentTimeMillis();
        try {
            // ① scope 路由：跨系统意图必须装配网关
            if (spec.getScope() != IntentScope.LOCAL && !gatewayAvailable()) {
                return finish(traceId, spec, startedAt, IntentStatus.FAILED, null, null,
                        new IntentError(IntentErrorCodes.REMOTE_UNAVAILABLE,
                                "该意图需要跨系统网关支持，当前宿主未装配网关"), List.of(), null,
                        params, user);
            }
            Map<String, Object> safeParams = new LinkedHashMap<>(params == null ? Map.of() : params);
            // 页面上下文自动补全缺失的必填参数（如 customerId）：业务页面已定位对象时，
            // 意图点击即执行，无需用户重复填写——"上下文即参数"的开箱体验
            for (String field : JsonSchemaValidator.requiredFields(spec.getParamsSchema())) {
                Object current = safeParams.get(field);
                boolean blank = current == null || (current instanceof String s && s.isBlank());
                if (blank && context != null) {
                    Object fromContext = context.get(field);
                    if (fromContext != null && !String.valueOf(fromContext).isBlank()) {
                        safeParams.put(field, fromContext);
                    }
                }
            }
            // ② 缺必填参数 → NEED_INPUT（不烧 token，直接让前端补全）
            List<String> missing = missingRequired(spec.getParamsSchema(), safeParams);
            if (!missing.isEmpty()) {
                return finish(traceId, spec, startedAt, IntentStatus.NEED_INPUT, null, missing,
                        new IntentError(IntentErrorCodes.MISSING_PARAMS,
                                "缺少必填参数，请补全后重新执行: " + String.join("、", missing)), List.of(), null,
                        safeParams, user);
            }
            // ③ 入参 Schema 校验
            List<String> errors = JsonSchemaValidator.validate(spec.getParamsSchema(), safeParams);
            if (!errors.isEmpty()) {
                return finish(traceId, spec, startedAt, IntentStatus.FAILED, null, null,
                        new IntentError(IntentErrorCodes.VALIDATION_ERROR,
                                "参数未通过校验: " + String.join("；", errors)), List.of(), null,
                        safeParams, user);
            }
            // ④ 执行器路由与执行
            IntentExecutor executor = resolveExecutor(spec);
            ExecutionOutcome outcome = executor.run(new ExecutionRequest(
                    spec, safeParams, context, user, traceId, toolDecorator));
            if (outcome.submitted()) {
                return finish(traceId, spec, startedAt, IntentStatus.SUCCESS,
                        outcome.output(), null, null, outcome.steps(), outcome.usage(),
                        safeParams, user);
            }
            return finish(traceId, spec, startedAt, IntentStatus.FAILED, null, null,
                    new IntentError(outcome.errorCode() == null
                            ? IntentErrorCodes.OUTPUT_INVALID : outcome.errorCode(),
                            outcome.errorMessage() == null ? "执行失败" : outcome.errorMessage()),
                    outcome.steps(), outcome.usage(), safeParams, user);
        } catch (ExecutorNotFoundException e) {
            return finish(traceId, spec, startedAt, IntentStatus.FAILED, null, null,
                    new IntentError(IntentErrorCodes.EXECUTOR_NOT_FOUND, e.getMessage()),
                    List.of(), null, params, user);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return finish(traceId, spec, startedAt, IntentStatus.FAILED, null, null,
                    new IntentError(IntentErrorCodes.VALIDATION_ERROR, e.getMessage()), List.of(), null,
                    params, user);
        } catch (Exception e) {
            return finish(traceId, spec, startedAt, IntentStatus.FAILED, null, null,
                    new IntentError(IntentErrorCodes.LLM_ERROR, "意图执行异常: " + e.getMessage()), List.of(), null,
                    params, user);
        }
    }

    /** 运行时状态（供宿主健康检查）。 */
    public String getStatus() {
        return "ready";
    }

    /** 已注册执行器清单（诊断用；也可作为意图保存时 executor 引用校验的白名单）。 */
    public List<String> executorIds() {
        synchronized (this) {
            return List.copyOf(executors.keySet());
        }
    }

    /**
     * 运行期注册或替换执行器（后台维护执行器档案后热更新用；id 为
     * {@link AgentExecutor#BUILTIN_ID} 时拒绝）。
     */
    public synchronized void registerExecutor(IntentExecutor executor) {
        if (AgentExecutor.BUILTIN_ID.equals(executor.id())) {
            throw new IllegalArgumentException(
                    "执行器 id 保留: " + AgentExecutor.BUILTIN_ID + "（不可覆盖内置推理循环）");
        }
        executors.put(executor.id(), executor);
    }

    /** 运行期注销执行器（builtin-agent 不可注销；id 不存在时静默忽略）。 */
    public synchronized void unregisterExecutor(String id) {
        if (AgentExecutor.BUILTIN_ID.equals(id)) {
            return;
        }
        executors.remove(id);
    }

    // ------------------------------------------------------------ helpers

    private IntentExecutor resolveExecutor(IntentSpec spec) {
        String id = spec.getExecutor();
        if (id == null || id.isBlank()) {
            return builtinExecutor;
        }
        IntentExecutor executor;
        synchronized (this) {
            executor = executors.get(id.trim());
        }
        if (executor == null) {
            throw new ExecutorNotFoundException("意图声明的执行器未注册: " + id
                    + "（意图 " + spec.getId() + "，已注册: " + executors.keySet() + "）");
        }
        return executor;
    }

    private boolean gatewayAvailable() {
        return gateway.isAvailable();
    }

    /** paramsSchema.required 中缺失的字段名。 */
    private static List<String> missingRequired(Map<String, Object> schema, Map<String, Object> params) {
        List<String> missing = new ArrayList<>();
        for (String field : JsonSchemaValidator.requiredFields(schema)) {
            Object value = params.get(field);
            if (value == null || (value instanceof String s && s.isBlank())) {
                missing.add(field);
            }
        }
        return missing;
    }

    private IntentResult finish(String traceId, IntentSpec spec, long startedAt,
            IntentStatus status, Map<String, Object> output, List<String> missingParams,
            IntentError error, List<StepTrace> steps, UsageInfo usage,
            Map<String, Object> params, UserInfo user) {
        long duration = System.currentTimeMillis() - startedAt;
        recordTrace(traceId, spec, startedAt, duration, status, params, output, error, steps, user);
        return new IntentResult(traceId, spec.getId(), status,
                output, missingParams, error,
                steps == null ? List.of() : List.copyOf(steps),
                usage == null ? UsageInfo.ZERO : usage, duration);
    }

    /** 执行留痕（best-effort：留痕故障不吞业务结果，仅告警）。 */
    private void recordTrace(String traceId, IntentSpec spec, long startedAt, long duration,
            IntentStatus status, Map<String, Object> params, Map<String, Object> output,
            IntentError error, List<StepTrace> steps, UserInfo user) {
        if (traceStore == null) {
            return;
        }
        try {
            traceStore.save(new ExecutionTraceRecord(
                    traceId, spec.getId(), spec.getName(),
                    user == null ? null : user.id(),
                    user == null ? null : user.name(),
                    startedAt, duration, status,
                    params, output, error,
                    steps == null ? List.of() : List.copyOf(steps)));
        } catch (Exception e) {
            System.err.println("[intent-sdk] 写入执行留痕失败（traceId=" + traceId + "）: " + e.getMessage());
        }
    }
}
