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
import dev.intent.protocol.IntentSpec;
import dev.intent.protocol.StepTrace;
import dev.intent.protocol.UsageInfo;
import dev.intent.sdk.llm.LlmConfig;
import dev.intent.sdk.tool.SubmitResultTool;
import dev.intent.sdk.tool.HostToolRegistry;
import dev.intent.sdk.tool.IntentTool;
import dev.pi.ai.AbortSignal;
import dev.pi.ai.AssistantMessage;
import dev.pi.ai.EventStream;
import dev.pi.ai.TextContent;
import dev.pi.agent.AgentContext;
import dev.pi.agent.AgentEvent;
import dev.pi.agent.AgentLoop;
import dev.pi.agent.AgentLoopConfig;
import dev.pi.agent.AgentMessage;
import dev.pi.agent.AgentTool;
import dev.pi.agent.LlmMessage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * 基于 pi-agent AgentLoop 的推理循环执行器。
 *
 * <p>执行内核：系统提示词组装（意图模板 + 工作规则 + 工具清单 + 下一步意图目录）
 * → AgentLoop 驱动推理与工具调用 → submit_result 输出闸门（出参 Schema 校验，
 * 不合规回传模型自纠）→ 校验失败整体重试、超时熔断。</p>
 *
 * <p>两种形态：内置单例（id = {@link #BUILTIN_ID}，由 IntentRuntime 装配，工具不限）；
 * 档案化实例（经 {@link ExecutorProfiles#build} 构建，id 与工具白名单来自
 * {@link ExecutorProfile}——执行器白名单是能力上限，意图白名单在其内进一步收窄）。</p>
 *
 * <p>无状态可复用：同一实例可并发执行多个意图（每次运行独立会话上下文）。
 * 宿主工具在独立线程执行，通过 ExecutionRequest.toolDecorator 恢复宿主上下文
 * （登录用户 / 数据权限 / 租户），权限随宿主调用栈自然生效。</p>
 */
public final class AgentExecutor implements IntentExecutor {

    /** 内置执行器标识（IntentSpec.executor 缺省值）。 */
    public static final String BUILTIN_ID = IntentExecutor.BUILTIN_ID;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final System.Logger LOG = System.getLogger(AgentExecutor.class.getName());
    /** 单次工具调用结果回显给轨迹的最大长度。 */
    private static final int RESULT_SUMMARY_LIMIT = 300;

    private final String executorId;
    private final LlmConnector llm;
    private final HostToolRegistry tools;
    private final Supplier<List<IntentSpec>> specCatalog;
    /** 执行器级工具白名单（能力上限；空 = 不限，工具集合由意图白名单决定）。 */
    private final List<String> executorTools;
    private final int maxTurns;
    private final int outputMaxRetries;
    private final ScheduledExecutorService timeoutScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "intent-executor-timeout");
                t.setDaemon(true);
                return t;
            });

    public AgentExecutor(LlmConfig llmConfig, HostToolRegistry tools,
            Supplier<List<IntentSpec>> specCatalog, int maxTurns, int outputMaxRetries) {
        this(BUILTIN_ID, llmConfig, tools, specCatalog, null, maxTurns, outputMaxRetries);
    }

    /** 档案化构造：执行器自带 id、模型与工具白名单上限（见 ExecutorProfile）。 */
    public AgentExecutor(String executorId, LlmConfig llmConfig, HostToolRegistry tools,
            Supplier<List<IntentSpec>> specCatalog, List<String> executorTools,
            int maxTurns, int outputMaxRetries) {
        this.executorId = executorId;
        this.llm = new LlmConnector(llmConfig);
        this.tools = tools;
        this.specCatalog = specCatalog == null ? List::of : specCatalog;
        this.executorTools = executorTools == null ? List.of() : List.copyOf(executorTools);
        this.maxTurns = Math.max(1, maxTurns);
        this.outputMaxRetries = Math.max(0, outputMaxRetries);
    }

    @Override
    public String id() {
        return executorId;
    }

    @Override
    public ExecutionOutcome run(ExecutionRequest request) {
        IntentSpec spec = request.spec();
        List<StepTrace> steps = new ArrayList<>();
        AtomicLong inputTokens = new AtomicLong();
        AtomicLong outputTokens = new AtomicLong();

        SubmitResultTool gate = new SubmitResultTool(spec);
        List<IntentTool> hostTools = resolveTools(spec, request.toolDecorator());
        hostTools.add(gate);
        List<AgentTool> loopTools = hostTools.stream()
                .map(tool -> (AgentTool) PiToolAdapter.of(tool))
                .collect(java.util.stream.Collectors.toList());

        String systemPrompt = buildSystemPrompt(spec, request.user(), loopTools);
        String userPrompt = buildUserPrompt(spec, request.params(), request.context());

        RunOutcome outcome = runLoop(systemPrompt, userPrompt, loopTools, gate,
                steps, inputTokens, outputTokens, spec.getPolicy().getTimeoutSeconds());

        // 输出闸门：模型未提交合规结果 → 整体重试（把上次回复作为纠偏上下文）
        int retries = 0;
        while (!gate.isSubmitted() && retries < outputMaxRetries) {
            retries++;
            steps.add(StepTrace.llm("输出未通过出参校验，整体重试第 " + retries + " 次", 0));
            outcome = runLoop(systemPrompt, nudgePrompt(outcome.finalText()), loopTools, gate,
                    steps, inputTokens, outputTokens, spec.getPolicy().getTimeoutSeconds());
        }

        UsageInfo usage = new UsageInfo(inputTokens.get(), outputTokens.get(),
                inputTokens.get() + outputTokens.get());
        if (gate.isSubmitted()) {
            return ExecutionOutcome.success(gate.captured(), steps, usage);
        }
        if (outcome.errorCode != null) {
            return ExecutionOutcome.failure(outcome.errorCode, outcome.errorMessage, steps, usage);
        }
        return ExecutionOutcome.failure(dev.intent.protocol.IntentErrorCodes.OUTPUT_INVALID,
                "输出未通过出参校验（已重试 " + retries + " 次），请稍后重试或联系管理员检查意图配置",
                steps, usage);
    }

    // ------------------------------------------------------------ 推理循环

    private record RunOutcome(String finalText, String errorCode, String errorMessage) {
    }

    private RunOutcome runLoop(String systemPrompt, String userPrompt, List<AgentTool> loopTools,
            SubmitResultTool gate, List<StepTrace> steps, AtomicLong inputTokens, AtomicLong outputTokens,
            int timeoutSeconds) {
        AgentContext context = new AgentContext(systemPrompt);
        context.tools.addAll(loopTools);
        AgentLoopConfig config = new AgentLoopConfig();
        config.model = llm.model();
        config.apiKey = llm.config().apiKey();
        config.toolExecution = AgentLoopConfig.ToolExecution.SEQUENTIAL;

        AbortSignal signal = new AbortSignal();
        ScheduledFuture<?> timeoutFuture = timeoutScheduler.schedule(signal::abort,
                Math.max(5, timeoutSeconds), TimeUnit.SECONDS);

        Map<String, Long> stepStarts = new LinkedHashMap<>();
        Map<String, String> stepArgs = new HashMap<>();
        AtomicReference<String> lastText = new AtomicReference<>("");
        AtomicReference<String> errorCode = new AtomicReference<>();
        AtomicReference<String> errorMessage = new AtomicReference<>("");
        AtomicReference<Throwable> loopFailure = new AtomicReference<>();

        // 推理循环在独立线程执行：必须把调用方的线程上下文类加载器带过去，
        // 否则宿主工具的类加载敏感逻辑（如从 Spring Boot 可执行 Jar 内读取资源）
        // 会因拿不到宿主的类加载器而失败。
        EventStream<AgentEvent, List<AgentMessage>> run = new EventStream<>(
                event -> event instanceof AgentEvent.AgentEnd,
                event -> ((AgentEvent.AgentEnd) event).messages());
        ClassLoader hostClassLoader = Thread.currentThread().getContextClassLoader();
        CompletableFuture.runAsync(() -> {
            Thread thread = Thread.currentThread();
            ClassLoader previous = thread.getContextClassLoader();
            thread.setContextClassLoader(hostClassLoader);
            try {
                AgentLoop.runAgentLoop(List.of(AgentMessage.user(userPrompt)), context, config,
                        run::push, signal, llm.streamFn());
            } catch (Throwable failure) {
                // 循环线程上的异常必须捕获后显式上报：静默中断会让上层只能看到
                // 一个含糊的"未提交合规结果"，把真正的故障藏在日志之外。
                loopFailure.set(failure);
            } finally {
                thread.setContextClassLoader(previous);
                run.end(List.of());
            }
        });

        try {
            for (AgentEvent event : run) {
                switch (event) {
                    case AgentEvent.ToolExecutionStart start -> {
                        stepStarts.put(start.toolCallId(), System.currentTimeMillis());
                        stepArgs.put(start.toolCallId(), truncate(json(start.args()), 160));
                    }
                    case AgentEvent.ToolExecutionEnd end -> {
                        Long begin = stepStarts.remove(end.toolCallId());
                        long duration = begin == null ? 0 : System.currentTimeMillis() - begin;
                        String resultSummary = end.result() == null ? null
                                : truncate(textOf(end.result().content()), RESULT_SUMMARY_LIMIT);
                        steps.add(StepTrace.tool(end.toolName(), stepArgs.remove(end.toolCallId()),
                                resultSummary, !end.isError(),
                                end.isError() ? resultSummary : null, duration));
                    }
                    case AgentEvent.MessageEnd end -> {
                        if (end.message() instanceof LlmMessage lm
                                && lm.message() instanceof AssistantMessage assistant) {
                            if (assistant.usage != null) {
                                inputTokens.addAndGet(assistant.usage.input);
                                outputTokens.addAndGet(assistant.usage.output);
                            }
                            if (assistant.stopReason == dev.pi.ai.StopReason.ERROR) {
                                errorCode.set(dev.intent.protocol.IntentErrorCodes.LLM_ERROR);
                                errorMessage.set(assistant.errorMessage == null
                                        ? "模型调用失败" : assistant.errorMessage);
                            }
                            if (assistant.text() != null && !assistant.text().isBlank()) {
                                lastText.set(assistant.text());
                                steps.add(StepTrace.llm(truncate(assistant.text(), 200), 0));
                            }
                        }
                    }
                    default -> {
                    }
                }
            }
            run.result().join();
            Throwable failure = loopFailure.get();
            if (failure != null && errorCode.get() == null) {
                LOG.log(System.Logger.Level.ERROR, "[intent] 推理循环异常中断", failure);
                errorCode.set(dev.intent.protocol.IntentErrorCodes.LLM_ERROR);
                errorMessage.set("推理循环异常: " + failure);
            }
        } catch (Exception e) {
            LOG.log(System.Logger.Level.ERROR, "[intent] 执行循环异常", e);
            if (errorCode.get() == null) {
                errorCode.set(dev.intent.protocol.IntentErrorCodes.LLM_ERROR);
                errorMessage.set("执行循环异常: " + e.getMessage());
            }
        } finally {
            timeoutFuture.cancel(false);
            stepStarts.clear();
            stepArgs.clear();
        }
        return new RunOutcome(lastText.get(), errorCode.get(), errorMessage.get());
    }

    /** 超时熔断后的重试提示词：不携带历史（全新会话），仅附上轮要点。 */
    private static String nudgePrompt(String lastText) {
        String hint = lastText == null || lastText.isBlank() ? "（无文本输出）" : truncate(lastText, 500);
        return "上一次执行未提交合规结果，请重新完成任务，并务必调用 submit_result 提交符合 Schema 的结构化结果。\n"
                + "上一次的文本输出（仅供参照）：\n" + hint;
    }

    // ------------------------------------------------------------ 工具与提示词

    /**
     * 工具白名单解析：执行器白名单是能力上限，意图白名单在其内进一步收窄。
     * <ul>
     *   <li>执行器未声明白名单：意图白名单为空 = 全部宿主工具；否则按意图白名单逐一校验；</li>
     *   <li>执行器声明白名单：意图未声明时取执行器白名单；意图声明了越界工具即配置错误。</li>
     * </ul>
     */
    private List<IntentTool> resolveTools(IntentSpec spec,
            java.util.function.UnaryOperator<IntentTool> decorator) {
        Map<String, IntentTool> all = tools.all();
        List<String> intentWhitelist = spec.getTools();
        boolean intentNarrowed = intentWhitelist != null && !intentWhitelist.isEmpty();
        boolean executorNarrowed = !executorTools.isEmpty();

        if (executorNarrowed && intentNarrowed) {
            for (String name : intentWhitelist) {
                if (!executorTools.contains(name)) {
                    throw new IllegalStateException("意图声明的工具超出执行器白名单: " + name
                            + "（执行器 " + executorId + "，意图 " + spec.getId() + "）");
                }
            }
        }
        List<String> source = intentNarrowed ? intentWhitelist
                : executorNarrowed ? executorTools : List.of();
        List<IntentTool> resolved = new ArrayList<>();
        if (source.isEmpty()) {
            all.values().stream().sorted(Comparator.comparing(IntentTool::name)).forEach(resolved::add);
        } else {
            for (String name : source) {
                IntentTool tool = all.get(name);
                if (tool == null) {
                    throw new IllegalStateException((intentNarrowed ? "意图声明的" : "执行器声明的")
                            + "宿主工具未注册: " + name
                            + "（执行器 " + executorId + "，意图 " + spec.getId() + "）");
                }
                resolved.add(tool);
            }
        }
        if (decorator != null) {
            resolved = resolved.stream().map(decorator).collect(java.util.stream.Collectors.toList());
        }
        return resolved;
    }

    private String buildSystemPrompt(IntentSpec spec, dev.intent.protocol.UserInfo user,
            List<AgentTool> loopTools) {
        StringBuilder sb = new StringBuilder();
        sb.append(spec.getPromptTemplate() == null ? "" : spec.getPromptTemplate().strip()).append("\n\n");
        sb.append("# 工作规则\n");
        sb.append("- 需要业务数据时必须调用提供的工具获取，禁止编造数据。\n");
        sb.append("- 完成任务后，必须调用 submit_result 工具提交结构化结果，禁止用文本回复代替。\n");
        sb.append("- 结果必须严格符合 submit_result 的参数 Schema。\n");
        sb.append("- nextIntents：需要换一个意图继续办时给出，intentId 必须来自下方「可推荐的下一步意图目录」。\n");
        sb.append("- followups：本次结果里还需要用户跟进/核实的具体事项，给 3~5 条、每条一句话且可直接执行"
                + "（不要复述 summary 的结论）；确实没有则留空数组，不要凑数。\n");
        if (user != null && user.name() != null) {
            sb.append("- 当前操作用户：").append(user.name()).append("。\n");
        }
        sb.append("\n# 可用工具\n");
        for (AgentTool tool : loopTools) {
            if (SubmitResultTool.NAME.equals(tool.name())) {
                continue;
            }
            sb.append("- ").append(tool.name()).append("：").append(tool.description()).append("\n");
        }
        List<IntentSpec> catalog = specCatalog.get();
        if (catalog != null && !catalog.isEmpty()) {
            sb.append("\n# 可推荐的下一步意图目录\n");
            for (IntentSpec s : catalog) {
                if (s.getId().equals(spec.getId())) {
                    continue;
                }
                sb.append("- ").append(s.getId()).append("：").append(s.getName())
                        .append(" —— ").append(s.getDescription() == null ? "" : s.getDescription()).append("\n");
            }
        }
        return sb.toString();
    }

    private String buildUserPrompt(IntentSpec spec, Map<String, Object> params, Map<String, Object> context) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 任务参数\n").append(json(params)).append("\n\n");
        if (context != null && !context.isEmpty()) {
            sb.append("## 页面上下文\n").append(json(context)).append("\n\n");
        }
        sb.append("请开始执行「").append(spec.getName()).append("」。");
        return sb.toString();
    }

    // ------------------------------------------------------------ 小工具

    static String json(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    static String truncate(String value, int limit) {
        if (value == null) {
            return null;
        }
        return value.length() <= limit ? value : value.substring(0, limit) + "…";
    }

    /** pi 消息内容块中的文本（用于执行轨迹摘要）。 */
    static String textOf(List<dev.pi.ai.ContentPart> content) {
        if (content == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (dev.pi.ai.ContentPart part : content) {
            if (part instanceof TextContent text) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(text.text());
            }
        }
        return sb.toString();
    }

}
