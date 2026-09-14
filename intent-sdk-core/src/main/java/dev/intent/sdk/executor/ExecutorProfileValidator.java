package dev.intent.sdk.executor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 执行器档案校验：id / type / model / 工具名 / 技能步骤合法性。
 *
 * <p>结构校验在此完成；"该类型当前是否可构建"由 {@code ExecutorProfiles#build} 把关
 * （如 type=remote 结构合法但构建暂不支持）。</p>
 */
public final class ExecutorProfileValidator {

    private static final Pattern ID_PATTERN = Pattern.compile("^[a-z][a-z0-9-]*$");
    private static final Pattern TOOL_NAME_PATTERN = Pattern.compile("^[a-z][a-z0-9_]*$");
    private static final Set<String> KNOWN_TYPES = Set.of(
            ExecutorProfile.TYPE_AGENT, ExecutorProfile.TYPE_SKILL, ExecutorProfile.TYPE_REMOTE);

    private ExecutorProfileValidator() {
    }

    public static List<String> validate(ExecutorProfile profile) {
        List<String> errors = new ArrayList<>();
        if (profile.id() == null || !ID_PATTERN.matcher(profile.id()).matches()) {
            errors.add("id 必填且只能为小写字母/数字/连字符（如 sales-analyst-v2）");
        }
        if (IntentExecutor.BUILTIN_ID.equals(profile.id())) {
            errors.add("id 保留: " + IntentExecutor.BUILTIN_ID + "（不可覆盖内置推理循环）");
        }
        if (profile.type() == null || !KNOWN_TYPES.contains(profile.type())) {
            errors.add("type 必须是 " + KNOWN_TYPES + " 之一");
        }
        if (ExecutorProfile.TYPE_AGENT.equals(profile.type()) && profile.flow().isEmpty()) {
            // 纯 agent 型（无流程编排）必配模型；带流程时由 checkFlow 按是否含 agent 节点判定
            checkModel(profile, errors);
        }
        if (ExecutorProfile.TYPE_SKILL.equals(profile.type())) {
            checkSkill(profile, errors);
        }
        checkFlow(profile, errors);
        for (String tool : profile.tools()) {
            if (tool == null || !TOOL_NAME_PATTERN.matcher(tool).matches()) {
                errors.add("工具名不合法: " + tool);
            }
        }
        for (ExecutorProfile.KnowledgeRef ref : profile.knowledge()) {
            if (ref.kb() == null || ref.kb().isBlank()) {
                errors.add("knowledge 条目缺少 kb 名称");
            }
        }
        for (Map.Entry<String, String> entry : profile.output().entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
                errors.add("output 出参模板不合法: " + entry.getKey());
            }
        }
        return errors;
    }

    private static void checkModel(ExecutorProfile profile, List<String> errors) {
        if (profile.model() == null) {
            errors.add("type=agent 必须配置 model");
            return;
        }
        checkModelFields(profile, errors);
    }

    /** skill 型仅在有 LLM 步骤时要求 model（纯工具步骤不触网即可运行）。 */
    private static void checkSkill(ExecutorProfile profile, List<String> errors) {
        if (profile.steps().isEmpty()) {
            errors.add("type=skill 必须声明 steps（确定性流程）");
        }
        boolean hasLlmStep = false;
        for (ExecutorProfile.SkillStep step : profile.steps()) {
            String label = "步骤[" + step.name() + "]";
            if (step.name() == null || step.name().isBlank()) {
                errors.add("技能步骤缺少 name");
                label = "技能步骤";
            }
            boolean hasTool = step.tool() != null && !step.tool().isBlank();
            boolean hasPrompt = step.prompt() != null && !step.prompt().isBlank();
            if (hasTool == hasPrompt) {
                errors.add(label + " 必须且只能声明 tool 或 prompt 之一");
            } else if (hasTool && !TOOL_NAME_PATTERN.matcher(step.tool()).matches()) {
                errors.add(label + " 工具名不合法: " + step.tool());
            }
            if (hasPrompt) {
                hasLlmStep = true;
            }
        }
        if (hasLlmStep) {
            if (profile.model() == null) {
                errors.add("skill 含 LLM 步骤时必须配置 model");
            } else {
                checkModelFields(profile, errors);
            }
        }
    }

    private static void checkModelFields(ExecutorProfile profile, List<String> errors) {
        if (profile.model().baseUrl() == null || profile.model().baseUrl().isBlank()) {
            errors.add("model.baseUrl 必填");
        }
        if (profile.model().modelId() == null || profile.model().modelId().isBlank()) {
            errors.add("model.modelId 必填");
        }
    }

    /** 流程编排校验：节点 id 唯一、类型合法、各类型必填项、agent 节点要求模型。 */
    private static void checkFlow(ExecutorProfile profile, List<String> errors) {
        if (profile.flow().isEmpty()) {
            return;
        }
        Set<String> seen = new java.util.HashSet<>();
        boolean hasAgentNode = false;
        for (ExecutorProfile.FlowNode node : profile.flow()) {
            String label = "节点[" + (node.id() == null ? "?" : node.id()) + "]";
            if (node.id() == null || node.id().isBlank()) {
                errors.add("流程节点缺少 id");
            } else if (!seen.add(node.id())) {
                errors.add("流程节点 id 重复: " + node.id());
            }
            String type = node.type() == null ? "" : node.type().trim().toLowerCase();
            switch (type) {
                case ExecutorProfile.NODE_TOOL -> {
                    if (node.tool() == null || node.tool().isBlank()) {
                        errors.add(label + " 工具节点缺少 tool");
                    } else if (!TOOL_NAME_PATTERN.matcher(node.tool()).matches()) {
                        errors.add(label + " 工具名不合法: " + node.tool());
                    }
                }
                case ExecutorProfile.NODE_AGENT -> {
                    hasAgentNode = true;
                    if (node.prompt() == null || node.prompt().isBlank()) {
                        errors.add(label + " agent 节点缺少 prompt");
                    }
                }
                case ExecutorProfile.NODE_KNOWLEDGE -> {
                    if (node.kb() == null || node.kb().isBlank()) {
                        errors.add(label + " knowledge 节点缺少 kb");
                    }
                }
                default -> errors.add(label + " 类型必须是 tool/agent/knowledge 之一");
            }
        }
        if (hasAgentNode) {
            if (profile.model() == null) {
                errors.add("流程含 agent 节点时必须配置 model");
            } else {
                checkModelFields(profile, errors);
            }
        }
    }
}
