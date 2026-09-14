package dev.intent.sdk.pi;

import dev.intent.sdk.executor.ExecutionOutcome;
import dev.intent.sdk.executor.ExecutionRequest;
import dev.intent.sdk.executor.ExecutorNotFoundException;
import dev.intent.sdk.executor.ExecutorProfile;
import dev.intent.sdk.executor.ExecutorProfileException;
import dev.intent.sdk.executor.ExecutorProfileValidator;
import dev.intent.sdk.executor.IntentExecutor;
import dev.intent.sdk.executor.KnowledgeRetriever;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.intent.sdk.executor.ExecutionOutcome;
import dev.intent.sdk.executor.ExecutionRequest;
import dev.intent.protocol.IntentErrorCodes;
import dev.intent.protocol.IntentSpec;
import dev.intent.protocol.StepTrace;
import dev.intent.protocol.UsageInfo;
import dev.intent.sdk.schema.JsonSchemaValidator;
import dev.intent.sdk.tool.HostToolRegistry;
import dev.intent.sdk.tool.IntentTool;
import dev.intent.sdk.tool.IntentToolResult;
import dev.pi.ai.AbortSignal;
import dev.pi.ai.AssistantMessage;
import dev.pi.ai.EventStream;
import dev.pi.agent.AgentContext;
import dev.pi.agent.AgentEvent;
import dev.pi.agent.AgentLoop;
import dev.pi.agent.AgentLoopConfig;
import dev.pi.agent.AgentMessage;
import dev.pi.agent.AgentTool;
import dev.pi.agent.LlmMessage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * skill 型执行器（type=skill）：预编排的确定性流程——固定步骤，每步可调工具。
 *
 * <p>步骤两类：工具步骤（直调宿主工具，参数模板渲染）与 LLM 步骤（提示词模板 +
 * 单次推理，无工具无推理循环）。步骤输出存入上下文 {@code steps.<name>.text / steps.<name>.details}，
 * 供后续步骤与出参模板引用；模板变量来源 {@code params} / {@code context} / {@code steps}。
 * 流程结束按档案 output 模板组装出参并经出参 Schema 校验（缺省 = title/summary 标准卡片）。</p>
 *
 * <p>与 agent 型的分工：agent 型把"怎么做"交给模型现场规划（工具集 + 推理循环）；
 * skill 型把"怎么做"固化为流程——确定性、可审计、低 token，适合标准作业程序
 * （如"标准催收七步法"：纯工具步骤甚至不触网即可运行）。</p>
 */
public final class SkillExecutor implements IntentExecutor {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)}");
    private static final int RESULT_SUMMARY_LIMIT = 300;

    private final ExecutorProfile profile;
    private final HostToolRegistry tools;
    /** 技能专属模型（纯工具步骤技能可为 null）。 */
    private final LlmConnector llm;
    private final ScheduledExecutorService timeoutScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "intent-skill-timeout");
                t.setDaemon(true);
                return t;
            });

    public SkillExecutor(ExecutorProfile profile, HostToolRegistry tools) {
        this.profile = profile;
        this.tools = tools;
        this.llm = profile.model() == null ? null : new LlmConnector(profile.model());
    }

    @Override
    public String id() {
        return profile.id();
    }

    @Override
    public ExecutionOutcome run(ExecutionRequest request) {
        List<StepTrace> steps = new ArrayList<>();
        AtomicLong inputTokens = new AtomicLong();
        AtomicLong outputTokens = new AtomicLong();
        AbortSignal signal = new AbortSignal();
        ScheduledFuture<?> timeoutFuture = timeoutScheduler.schedule(signal::abort,
                Math.max(5, request.spec().getPolicy().getTimeoutSeconds()), TimeUnit.SECONDS);
        try {
            return doRun(request, steps, inputTokens, outputTokens, signal);
        } finally {
            timeoutFuture.cancel(false);
        }
    }

    private ExecutionOutcome doRun(ExecutionRequest request, List<StepTrace> steps,
            AtomicLong inputTokens, AtomicLong outputTokens, AbortSignal signal) {
        Map<String, Object> stepOutputs = new LinkedHashMap<>();
        Map<String, Object> vars = Map.of(
                "params", request.params(),
                "context", request.context(),
                "steps", stepOutputs);
        try {
            for (ExecutorProfile.SkillStep step : profile.steps()) {
                if (signal.isAborted()) {
                    return ExecutionOutcome.failure(IntentErrorCodes.TIMEOUT,
                            "技能执行超时熔断（policy.timeoutSeconds）", steps, usage(inputTokens, outputTokens));
                }
                stepOutputs.put(step.name(), step.tool() != null
                        ? runToolStep(step, vars, signal, steps)
                        : runLlmStep(step, vars, signal, steps, inputTokens, outputTokens));
            }
            Map<String, Object> output = renderOutput(request.spec(), stepOutputs, vars);
            List<String> errors = JsonSchemaValidator.validate(request.spec().getOutputSchema(), output);
            if (!errors.isEmpty()) {
                return ExecutionOutcome.failure(IntentErrorCodes.OUTPUT_INVALID,
                        "技能输出未通过出参校验: " + String.join("；", errors), steps, usage(inputTokens, outputTokens));
            }
            return ExecutionOutcome.success(output, steps, usage(inputTokens, outputTokens));
        } catch (SkillFailure e) {
            return ExecutionOutcome.failure(e.code, e.getMessage(), steps, usage(inputTokens, outputTokens));
        } catch (Exception e) {
            return ExecutionOutcome.failure(
                    signal.isAborted() ? IntentErrorCodes.TIMEOUT : IntentErrorCodes.LLM_ERROR,
                    "技能执行异常: " + e.getMessage(), steps, usage(inputTokens, outputTokens));
        }
    }

    // ------------------------------------------------------------ 步骤执行

    private Map<String, Object> runToolStep(ExecutorProfile.SkillStep step, Map<String, Object> vars,
            AbortSignal signal, List<StepTrace> steps) {
        IntentTool tool = tools.get(step.tool());
        if (tool == null) {
            throw new SkillFailure(IntentErrorCodes.TOOL_ERROR,
                    "技能步骤引用的工具未注册: " + step.tool() + "（执行器 " + profile.id() + "）");
        }
        Map<String, Object> args = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : step.args().entrySet()) {
            args.put(entry.getKey(), entry.getValue() == null ? null : render(entry.getValue(), vars));
        }
        long started = System.currentTimeMillis();
        try {
            IntentToolResult result = tool.execute("skill-" + step.name(), args, PiToolAdapter.context(signal));
            String text = PiToolAdapter.textOf(result);
            Map<String, Object> details = new LinkedHashMap<>(result.data());
            steps.add(StepTrace.tool(step.tool(), AgentExecutor.truncate(AgentExecutor.json(args), 160),
                    AgentExecutor.truncate(text, RESULT_SUMMARY_LIMIT), true, null,
                    System.currentTimeMillis() - started));
            return Map.of("text", text, "details", details);
        } catch (SkillFailure e) {
            throw e;
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            steps.add(StepTrace.tool(step.tool(), AgentExecutor.truncate(AgentExecutor.json(args), 160),
                    null, false, message, System.currentTimeMillis() - started));
            throw new SkillFailure(IntentErrorCodes.TOOL_ERROR,
                    "工具步骤执行失败: " + step.tool() + ": " + message, e);
        }
    }

    private Map<String, Object> runLlmStep(ExecutorProfile.SkillStep step, Map<String, Object> vars,
            AbortSignal signal, List<StepTrace> steps, AtomicLong inputTokens, AtomicLong outputTokens) {
        if (llm == null) {
            throw new SkillFailure(IntentErrorCodes.LLM_ERROR, "技能含 LLM 步骤但档案未配置 model");
        }
        String prompt = render(step.prompt(), vars);
        long started = System.currentTimeMillis();
        AgentContext context = new AgentContext("");
        AgentLoopConfig config = new AgentLoopConfig();
        config.model = llm.model();
        config.apiKey = llm.config().apiKey();
        AtomicReference<String> lastText = new AtomicReference<>("");
        try {
            EventStream<AgentEvent, List<AgentMessage>> run = AgentLoop.agentLoop(
                    List.of(AgentMessage.user(prompt)), context, config, signal, llm.streamFn());
            for (AgentEvent event : run) {
                if (event instanceof AgentEvent.MessageEnd end
                        && end.message() instanceof LlmMessage lm
                        && lm.message() instanceof AssistantMessage assistant) {
                    if (assistant.usage != null) {
                        inputTokens.addAndGet(assistant.usage.input);
                        outputTokens.addAndGet(assistant.usage.output);
                    }
                    if (assistant.stopReason == dev.pi.ai.StopReason.ERROR) {
                        throw new SkillFailure(IntentErrorCodes.LLM_ERROR,
                                assistant.errorMessage == null ? "模型调用失败" : assistant.errorMessage);
                    }
                    if (assistant.text() != null && !assistant.text().isBlank()) {
                        lastText.set(assistant.text());
                    }
                }
            }
            run.result().join();
        } catch (SkillFailure e) {
            throw e;
        } catch (Exception e) {
            throw new SkillFailure(IntentErrorCodes.LLM_ERROR, "LLM 步骤执行失败: " + e.getMessage(), e);
        }
        steps.add(StepTrace.llm(AgentExecutor.truncate(lastText.get(), 200),
                System.currentTimeMillis() - started));
        return Map.of("text", lastText.get());
    }

    // ------------------------------------------------------------ 模板与出参

    /** 缺省出参：标准卡片（title/summary/blocks，summary=最后一个步骤的文本）；
     *  声明了 output 模板则逐项渲染（渲染结果为合法 JSON 时按结构化值放入，如 blocks）。 */
    private Map<String, Object> renderOutput(IntentSpec spec,
            Map<String, Object> stepOutputs, Map<String, Object> vars) {
        if (profile.output().isEmpty()) {
            String summary = "";
            for (Object value : stepOutputs.values()) {
                if (value instanceof Map<?, ?> step && step.get("text") != null) {
                    summary = String.valueOf(step.get("text"));
                }
            }
            return Map.of(
                    "title", String.valueOf(spec.getName()),
                    "summary", summary,
                    "blocks", List.of(Map.of("kind", "text", "text", summary)));
        }
        Map<String, Object> output = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : profile.output().entrySet()) {
            output.put(entry.getKey(), renderField(entry.getValue(), vars));
        }
        return output;
    }

    /**
     * 渲染出参字段。结构化解析只看"模板本身"：模板以 [ 或 { 开头（作者明确写了
     * JSON 结构）→ 渲染时对变量值做 JSON 转义后解析为结构化值（如 blocks）；
     * 纯变量引用（如 ${nodes.n1.text}）永远返回字符串——即使内容恰好形似 JSON。
     */
    static Object renderField(String template, Map<String, Object> vars) {
        String trimmedTemplate = template.trim();
        if (trimmedTemplate.startsWith("[") || trimmedTemplate.startsWith("{")) {
            String rendered = renderJsonTemplate(template, vars);
            try {
                return MAPPER.readValue(rendered, Object.class);
            } catch (Exception e) {
                // 渲染后不是合法 JSON → 按纯文本处理；结构不符时出参 Schema 校验会明确报错
            }
        }
        return render(template, vars);
    }

    /** JSON 模板渲染：变量值经 JSON 转义后内插，保证整体仍是合法 JSON。 */
    private static String renderJsonTemplate(String template, Map<String, Object> vars) {
        Matcher matcher = VAR_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            Object value = lookup(vars, matcher.group(1).trim());
            if (value == null) {
                throw new SkillFailure(IntentErrorCodes.VALIDATION_ERROR,
                        "模板变量未找到: ${" + matcher.group(1) + "}");
            }
            try {
                String escaped = MAPPER.writeValueAsString(String.valueOf(value));
                matcher.appendReplacement(sb,
                        Matcher.quoteReplacement(escaped.substring(1, escaped.length() - 1)));
            } catch (Exception e) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf(value)));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /** 渲染 ${...} 模板；变量缺失即配置错误（明确报错，不静默置空）。 */
    static String render(String template, Map<String, Object> vars) {
        Matcher matcher = VAR_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            Object value = lookup(vars, matcher.group(1).trim());
            if (value == null) {
                throw new SkillFailure(IntentErrorCodes.VALIDATION_ERROR,
                        "模板变量未找到: ${" + matcher.group(1) + "}");
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf(value)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /** 按 "." 逐级在嵌套 Map 中查找路径（如 steps.lookup.details.level）。 */
    private static Object lookup(Map<String, Object> vars, String path) {
        Object current = vars;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(part);
        }
        return current;
    }

    private static UsageInfo usage(AtomicLong inputTokens, AtomicLong outputTokens) {
        return new UsageInfo(inputTokens.get(), outputTokens.get(),
                inputTokens.get() + outputTokens.get());
    }

    /** 携带错误码的技能失败（运行时据此语义化 IntentResult.error）。 */
    private static final class SkillFailure extends RuntimeException {
        final String code;

        SkillFailure(String code, String message) {
            super(message);
            this.code = code;
        }

        SkillFailure(String code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }
    }
}
