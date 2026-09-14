package dev.intent.sdk.pi;

import dev.intent.sdk.executor.ExecutionOutcome;
import dev.intent.sdk.executor.ExecutionRequest;
import dev.intent.sdk.executor.ExecutorNotFoundException;
import dev.intent.sdk.executor.ExecutorProfile;
import dev.intent.sdk.executor.ExecutorProfileException;
import dev.intent.sdk.executor.ExecutorProfileValidator;
import dev.intent.sdk.executor.IntentExecutor;
import dev.intent.sdk.executor.KnowledgeRetriever;

import dev.intent.protocol.IntentSpec;
import dev.intent.sdk.tool.HostToolRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 执行器档案工厂：把声明式 {@link ExecutorProfile} 构建为可运行的
 * {@link IntentExecutor} 实例，交给 {@code IntentRuntimeConfig.customExecutors} 注册。
 *
 * <p>装配示例：</p>
 * <pre>{@code
 * List<ExecutorProfile> profiles = ExecutorProfileLoader.loadDir(Path.of("config/executors"));
 * IntentRuntime runtime = IntentRuntime.create(IntentRuntimeConfig.builder(llmConfig)
 *         .customExecutors(ExecutorProfiles.build(profiles, hostTools, catalogSupplier))
 *         .build());
 * }</pre>
 *
 * <p>构建即把关：id 冲突 / 保留 id / 尚未实现的附件声明（knowledge、skills、memory）
 * / 不支持的类型（remote 网关联动规划 M2.3）一律明确报错，不静默降级。</p>
 */
public final class ExecutorProfiles {

    private ExecutorProfiles() {
    }

    /**
     * 将档案批量构建为执行器实例（无知识检索需求）。
     *
     * @param profiles     执行器档案（须先经 ExecutorProfileLoader 加载校验）
     * @param tools        宿主工具注册表（执行器级白名单 / 技能与流程的工具步骤在此范围内解析）
     * @param specCatalog  意图目录供给者（供提示词注入"可推荐的下一步意图"）
     */
    public static List<IntentExecutor> build(List<ExecutorProfile> profiles,
            HostToolRegistry tools, Supplier<List<IntentSpec>> specCatalog) {
        return build(profiles, tools, specCatalog, List.of());
    }

    /**
     * 将档案批量构建为执行器实例。
     *
     * @param profiles     执行器档案（须先经 ExecutorProfileLoader 加载校验）
     * @param tools        宿主工具注册表（执行器级白名单 / 技能与流程的工具节点在此范围内解析）
     * @param specCatalog  意图目录供给者（供提示词注入"可推荐的下一步意图"）
     * @param retrievers   知识检索器（流程 knowledge 节点使用；可空）
     */
    public static List<IntentExecutor> build(List<ExecutorProfile> profiles,
            HostToolRegistry tools, Supplier<List<IntentSpec>> specCatalog,
            List<KnowledgeRetriever> retrievers) {
        Map<String, ExecutorProfile> seen = new LinkedHashMap<>();
        List<IntentExecutor> built = new ArrayList<>();
        for (ExecutorProfile profile : profiles == null ? List.<ExecutorProfile>of() : profiles) {
            List<String> errors = ExecutorProfileValidator.validate(profile);
            String id = profile.id() == null ? "(unknown)" : profile.id();
            if (errors.isEmpty() && seen.containsKey(profile.id())) {
                errors = List.of("执行器 id 重复: " + profile.id());
            }
            if (errors.isEmpty() && !profile.knowledge().isEmpty()) {
                errors = List.of("knowledge 附件暂不可用：知识检索请通过 flow 的 knowledge 节点使用");
            }
            if (errors.isEmpty() && !profile.skills().isEmpty()) {
                errors = List.of("技能组合附件尚未实现，skills 声明暂不可用（规划中）");
            }
            if (errors.isEmpty() && profile.memory() != null && !profile.memory().isBlank()) {
                errors = List.of("会话记忆尚未实现，memory 声明暂不可用（规划中）");
            }
            if (errors.isEmpty() && ExecutorProfile.TYPE_REMOTE.equals(profile.type())) {
                errors = List.of("暂不支持构建 type=remote 的执行器（网关联动规划 M2.3）");
            }
            if (!errors.isEmpty()) {
                throw new ExecutorProfileException(id, errors);
            }
            seen.put(profile.id(), profile);
            built.add(switch (profile.type()) {
                case ExecutorProfile.TYPE_SKILL -> new SkillExecutor(profile, tools);
                default -> !profile.flow().isEmpty()
                        ? new FlowExecutor(profile, tools, retrievers)
                        : new AgentExecutor(profile.id(), profile.model(), tools, specCatalog,
                                profile.tools(), profile.limits().maxTurns(),
                                profile.limits().outputMaxRetries());
            });
        }
        return built;
    }
}
