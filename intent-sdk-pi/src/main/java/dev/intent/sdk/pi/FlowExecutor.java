package dev.intent.sdk.pi;

import dev.intent.sdk.executor.ExecutionOutcome;
import dev.intent.sdk.executor.ExecutionRequest;
import dev.intent.sdk.executor.ExecutorNotFoundException;
import dev.intent.sdk.executor.ExecutorProfile;
import dev.intent.sdk.executor.ExecutorProfileException;
import dev.intent.sdk.executor.ExecutorProfileValidator;
import dev.intent.sdk.executor.IntentExecutor;
import dev.intent.sdk.executor.KnowledgeRetriever;


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
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 流程型执行器（ExecutorProfile.flow 非空）：按能力编排完成意图执行。
 *
 * <p>执行模型：节点按声明顺序执行（线性编排 + 条件分支——{@code when} 跳过节点、
 * {@code stopWhen} 提前结束），节点输出进入上下文 {@code nodes.<id>.text / .details}，
 * 供后续节点与出参模板引用。三类节点：</p>
 * <ul>
 *   <li>tool：直调宿主工具（参数模板渲染）——组合"工具集"能力；</li>
 *   <li>agent：单次 LLM 推理（提示词模板）——组合"智能体"能力（需配置模型）；</li>
 *   <li>knowledge：知识检索（经 {@link KnowledgeRetriever} SPI）——组合"知识库"能力；
 *       结果放入 {@code nodes.<id>.details.chunks} 并拼为 {@code .text}。</li>
 * </ul>
 *
 * <p>与 agent 型（模型自主规划）互补：流程型把"怎么做"固化为可配置的编排图，
 * 换模型、换工具、加知识库都在执行器档案上完成，引用它的意图零改动。</p>
 */
public final class FlowExecutor implements IntentExecutor {

    private static final int RESULT_SUMMARY_LIMIT = 300;
    /** 条件模板渲染结果的假值集合（不区分大小写）。 */
    private static final Set<String> FALSY = Set.of("false", "no", "0", "否", "假", "");

    private final ExecutorProfile profile;
    private final HostToolRegistry tools;
    private final List<KnowledgeRetriever> retrievers;
    private final LlmConnector llm;
    private final ScheduledExecutorService timeoutScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "intent-flow-timeout");
                t.setDaemon(true);
                return t;
            });

    public FlowExecutor(ExecutorProfile profile, HostToolRegistry tools,
            List<KnowledgeRetriever> retrievers) {
        this.profile = profile;
        this.tools = tools;
        this.retrievers = retrievers == null ? List.of() : List.copyOf(retrievers);
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
        Map<String, Object> nodeOutputs = new LinkedHashMap<>();
        Map<String, Object> vars = Map.of(
                "params", request.params(),
                "context", request.context(),
                "nodes", nodeOutputs);
        try {
            for (ExecutorProfile.FlowNode node : profile.flow()) {
                // 中止条件：渲染为真值 → 流程提前结束（已产出的节点输出仍参与出参组装）
                if (node.stopWhen() != null && !node.stopWhen().isBlank()
                        && isTruthy(SkillExecutor.render(node.stopWhen(), vars))) {
                    
                    steps.add(StepTrace.llm("中止条件成立（" + node.id() + "），流程提前结束", 0));
                    break;
                }
                // 执行条件：渲染为假值 → 跳过该节点
                if (node.when() != null && !node.when().isBlank()
                        && !isTruthy(SkillExecutor.render(node.when(), vars))) {
                    nodeOutputs.put(node.id(), Map.of("skipped", true));
                    steps.add(StepTrace.llm("跳过节点 " + node.id() + "（条件不成立）", 0));
                    continue;
                }
                if (signal.isAborted()) {
                    return ExecutionOutcome.failure(IntentErrorCodes.TIMEOUT,
                            "流程执行超时熔断（policy.timeoutSeconds）", steps, usage(inputTokens, outputTokens));
                }
                nodeOutputs.put(node.id(), switch (nodeType(node)) {
                    case ExecutorProfile.NODE_TOOL -> runToolNode(node, vars, signal, steps);
                    case ExecutorProfile.NODE_AGENT -> runAgentNode(node, vars, signal, steps,
                            inputTokens, outputTokens);
                    case ExecutorProfile.NODE_KNOWLEDGE -> runKnowledgeNode(node, vars, steps);
                    default -> throw new SkillFailure(IntentErrorCodes.VALIDATION_ERROR,
                            "未知节点类型: " + node.type() + "（节点 " + node.id() + "）");
                });
            }
            return finish(request.spec(), nodeOutputs, vars, steps, inputTokens, outputTokens);
        } catch (SkillFailure e) {
            return ExecutionOutcome.failure(e.code, e.getMessage(), steps, usage(inputTokens, outputTokens));
        } catch (Exception e) {
            return ExecutionOutcome.failure(
                    signal.isAborted() ? IntentErrorCodes.TIMEOUT : IntentErrorCodes.LLM_ERROR,
                    "流程执行异常: " + e.getMessage(), steps, usage(inputTokens, outputTokens));
        }
    }

    // ------------------------------------------------------------ 节点执行

    private Map<String, Object> runToolNode(ExecutorProfile.FlowNode node, Map<String, Object> vars,
            AbortSignal signal, List<StepTrace> steps) {
        IntentTool tool = tools.get(node.tool());
        if (tool == null) {
            throw new SkillFailure(IntentErrorCodes.TOOL_ERROR,
                    "流程节点引用的工具未注册: " + node.tool() + "（执行器 " + profile.id() + "）");
        }
        Map<String, Object> args = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : node.args().entrySet()) {
            args.put(entry.getKey(), entry.getValue() == null ? null : SkillExecutor.render(entry.getValue(), vars));
        }
        long started = System.currentTimeMillis();
        try {
            IntentToolResult result = tool.execute("flow-" + node.id(), args, PiToolAdapter.context(signal));
            String text = PiToolAdapter.textOf(result);
            Map<String, Object> details = new LinkedHashMap<>(result.data());
            steps.add(StepTrace.tool(node.tool(), AgentExecutor.truncate(AgentExecutor.json(args), 160),
                    AgentExecutor.truncate(text, RESULT_SUMMARY_LIMIT), true, null,
                    System.currentTimeMillis() - started));
            return Map.of("text", text, "details", details);
        } catch (SkillFailure e) {
            throw e;
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            steps.add(StepTrace.tool(node.tool(), AgentExecutor.truncate(AgentExecutor.json(args), 160),
                    null, false, message, System.currentTimeMillis() - started));
            throw new SkillFailure(IntentErrorCodes.TOOL_ERROR,
                    "工具节点执行失败: " + node.tool() + ": " + message, e);
        }
    }

    private Map<String, Object> runAgentNode(ExecutorProfile.FlowNode node, Map<String, Object> vars,
            AbortSignal signal, List<StepTrace> steps, AtomicLong inputTokens, AtomicLong outputTokens) {
        if (llm == null) {
            throw new SkillFailure(IntentErrorCodes.VALIDATION_ERROR,
                    "流程含 agent 节点但执行器未配置 model（节点 " + node.id() + "）");
        }
        String prompt = SkillExecutor.render(node.prompt(), vars);
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
            throw new SkillFailure(IntentErrorCodes.LLM_ERROR,
                    "agent 节点执行失败: " + e.getMessage(), e);
        }
        steps.add(StepTrace.llm(AgentExecutor.truncate(lastText.get(), 200),
                System.currentTimeMillis() - started));
        return Map.of("text", lastText.get());
    }

    private Map<String, Object> runKnowledgeNode(ExecutorProfile.FlowNode node,
            Map<String, Object> vars, List<StepTrace> steps) {
        String query = node.query() == null || node.query().isBlank()
                ? AgentExecutor.json(vars.get("params"))
                : SkillExecutor.render(node.query(), vars);
        int topK = node.topK() == null || node.topK() <= 0 ? 5 : node.topK();
        KnowledgeRetriever retriever = retrievers.stream()
                .filter(r -> r.supports(node.kb())).findFirst().orElse(null);
        if (retriever == null) {
            throw new SkillFailure(IntentErrorCodes.VALIDATION_ERROR,
                    "知识库「" + node.kb() + "」未装配检索器（宿主需实现 KnowledgeRetriever 并注册）");
        }
        long started = System.currentTimeMillis();
        List<KnowledgeRetriever.KnowledgeChunk> chunks;
        try {
            chunks = retriever.retrieve(node.kb(), query, topK);
        } catch (Exception e) {
            throw new SkillFailure(IntentErrorCodes.TOOL_ERROR,
                    "知识检索失败: " + node.kb() + ": " + e.getMessage(), e);
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            if (i > 0) {
                text.append('\n');
            }
            text.append('[').append(i + 1).append("] ").append(chunks.get(i).content());
        }
        steps.add(StepTrace.tool("knowledge:" + node.kb(),
                AgentExecutor.truncate(query, 160),
                AgentExecutor.truncate(text.toString(), RESULT_SUMMARY_LIMIT), true, null,
                System.currentTimeMillis() - started));
        return Map.of("text", text.toString(),
                "details", Map.of("chunks", chunks.stream()
                        .map(c -> Map.of("content", c.content() == null ? "" : c.content(),
                                "score", c.score() == null ? 0 : c.score()))
                        .toList()));
    }

    // ------------------------------------------------------------ 出参与条件

    /** 出参组装：output 模板（nodes 上下文）缺省 = 标准卡片（最后一个节点文本）。 */
    private ExecutionOutcome finish(IntentSpec spec, Map<String, Object> nodeOutputs,
            Map<String, Object> vars, List<StepTrace> steps, AtomicLong inputTokens,
            AtomicLong outputTokens) {
        Map<String, Object> output = renderOutput(spec, nodeOutputs, vars);
        List<String> errors = JsonSchemaValidator.validate(spec.getOutputSchema(), output);
        if (!errors.isEmpty()) {
            return ExecutionOutcome.failure(IntentErrorCodes.OUTPUT_INVALID,
                    "流程输出未通过出参校验: " + String.join("；", errors), steps, usage(inputTokens, outputTokens));
        }
        return ExecutionOutcome.success(output, steps, usage(inputTokens, outputTokens));
    }

    private Map<String, Object> renderOutput(IntentSpec spec,
            Map<String, Object> nodeOutputs, Map<String, Object> vars) {
        if (profile.output().isEmpty()) {
            String summary = "";
            for (Object value : nodeOutputs.values()) {
                if (value instanceof Map<?, ?> node && node.get("text") != null) {
                    summary = String.valueOf(node.get("text"));
                }
            }
            return Map.of(
                    "title", String.valueOf(spec.getName()),
                    "summary", summary,
                    "blocks", List.of(Map.of("kind", "text", "text", summary)));
        }
        Map<String, Object> output = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : profile.output().entrySet()) {
            output.put(entry.getKey(), SkillExecutor.renderField(entry.getValue(), vars));
        }
        return output;
    }

    private static String nodeType(ExecutorProfile.FlowNode node) {
        return node.type() == null ? "" : node.type().trim().toLowerCase();
    }

    /** 条件模板渲染结果的真假判定：只有明确假值（false/no/0/否/假/空）为假，其余非空文本为真。 */
    static boolean isTruthy(String rendered) {
        String value = rendered == null ? "" : rendered.trim().toLowerCase();
        return !FALSY.contains(value);
    }

    private static UsageInfo usage(AtomicLong inputTokens, AtomicLong outputTokens) {
        return new UsageInfo(inputTokens.get(), outputTokens.get(),
                inputTokens.get() + outputTokens.get());
    }

    /** 携带错误码的流程失败（运行时据此语义化 IntentResult.error）。 */
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
