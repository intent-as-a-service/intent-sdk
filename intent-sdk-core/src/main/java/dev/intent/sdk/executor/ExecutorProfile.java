package dev.intent.sdk.executor;

import dev.intent.sdk.llm.LlmConfig;

import java.util.List;
import java.util.Map;

/**
 * 执行器档案：执行器的声明式配置——"谁来做"的载体（智能体 / 技能 / 远程服务）。
 *
 * <p>意图契约通过 {@code executor: <id>} 按名引用执行器，不内联执行细节；
 * 本档案独立演进：换模型、加工具白名单、升级执行器，引用它的意图自动受益。</p>
 *
 * <p>YAML 示例（apiKey 留空则回退环境变量解析，避免密钥入库）：</p>
 * <pre>{@code
 * id: sales-analyst-v2
 * type: agent
 * name: 销售分析师
 * model:
 *   baseUrl: https://api.deepseek.com
 *   provider: deepseek
 *   modelId: deepseek-chat
 * tools:                      # 执行器级工具白名单（能力上限；缺省不限）
 *   - crm_get_customer
 * limits:
 *   maxTurns: 8
 *   outputMaxRetries: 1
 * }</pre>
 *
 * <p>skill 型档案用 steps 声明确定性流程（工具步骤 / LLM 步骤），output 声明
 * 出参模板；remote 型规划网关联动（M2.3）。knowledge / skills / memory 为占位
 * 字段——声明即构建失败（明确报错，不静默忽略），待后续里程碑实现。</p>
 */
public record ExecutorProfile(
        String id,
        String type,
        String name,
        String description,
        LlmConfig model,
        List<String> tools,
        List<KnowledgeRef> knowledge,
        List<String> skills,
        String memory,
        Limits limits,
        List<SkillStep> steps,
        Map<String, String> output,
        List<FlowNode> flow) {

    public static final String TYPE_AGENT = "agent";
    public static final String TYPE_SKILL = "skill";
    public static final String TYPE_REMOTE = "remote";

    public ExecutorProfile {
        id = id == null ? null : id.trim();
        type = type == null ? null : type.trim().toLowerCase();
        tools = tools == null ? List.of() : List.copyOf(tools);
        knowledge = knowledge == null ? List.of() : List.copyOf(knowledge);
        skills = skills == null ? List.of() : List.copyOf(skills);
        limits = limits == null ? new Limits(null, null) : limits;
        steps = steps == null ? List.of() : List.copyOf(steps);
        output = output == null ? Map.of() : Map.copyOf(output);
        flow = flow == null ? List.of() : List.copyOf(flow);
    }

    /** 便捷构造：无流程编排（缺省 = agent 推理循环 / skill 步骤执行）。 */
    public ExecutorProfile(String id, String type, String name, String description,
            LlmConfig model, List<String> tools, List<KnowledgeRef> knowledge,
            List<String> skills, String memory, Limits limits,
            List<SkillStep> steps, Map<String, String> output) {
        this(id, type, name, description, model, tools, knowledge, skills, memory,
                limits, steps, output, List.of());
    }

    /** flow 节点类型。 */
    public static final String NODE_TOOL = "tool";
    public static final String NODE_AGENT = "agent";
    public static final String NODE_KNOWLEDGE = "knowledge";

    /**
     * 流程节点：按声明顺序执行（线性编排 + 条件分支），节点输出经
     * {@code nodes.<id>.text / .details} 供后续节点与出参模板引用。
     *
     * @param id       节点标识（唯一）
     * @param type     tool / agent / knowledge
     * @param tool     tool 节点：宿主工具名
     * @param args     tool 节点：参数模板（值支持 ${...}）
     * @param prompt   agent 节点：提示词模板（单次推理）
     * @param kb       knowledge 节点：知识库标识
     * @param query    knowledge 节点：检索词模板（缺省 = 意图参数 JSON）
     * @param topK     knowledge 节点：召回条数（缺省 5）
     * @param when     执行条件模板：渲染为假值（false/否/空）→ 跳过该节点
     * @param stopWhen 中止条件模板：渲染为真值（true/是/1）→ 流程提前结束
     */
    public record FlowNode(
            String id,
            String type,
            String tool,
            Map<String, String> args,
            String prompt,
            String kb,
            String query,
            Integer topK,
            String when,
            String stopWhen) {

        public FlowNode {
            args = args == null ? Map.of() : Map.copyOf(args);
        }
    }

    /** 知识库附件（占位：检索实现未落地）。 */
    public record KnowledgeRef(String kb, int topK) {
        public KnowledgeRef {
            if (topK <= 0) {
                topK = 5;
            }
        }
    }

    /**
     * 技能步骤：tool 与 prompt 二选一。
     *
     * @param name   步骤名（输出经 {@code steps.<name>.text} 供后续步骤/出参模板引用）
     * @param tool   宿主工具名（工具步骤）
     * @param prompt 提示词模板（LLM 步骤，支持 ${...} 变量）
     * @param args   工具步骤参数模板（值为字符串时支持 ${...} 变量）
     */
    public record SkillStep(String name, String tool, String prompt, Map<String, String> args) {
        public SkillStep {
            args = args == null ? Map.of() : Map.copyOf(args);
        }
    }

    /**
     * 执行器级限额（null = 取默认 maxTurns=12 / outputMaxRetries=1；
     * outputMaxRetries 显式配 0 表示不整体重试；仅 agent 型使用）。
     */
    public record Limits(Integer maxTurns, Integer outputMaxRetries) {
        public Limits {
            if (maxTurns == null || maxTurns <= 0) {
                maxTurns = 12;
            }
            if (outputMaxRetries == null || outputMaxRetries < 0) {
                outputMaxRetries = 1;
            }
        }
    }
}
