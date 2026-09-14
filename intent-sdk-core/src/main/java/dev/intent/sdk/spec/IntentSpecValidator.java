package dev.intent.sdk.spec;

import dev.intent.protocol.ContextField;
import dev.intent.protocol.IntentSpec;
import dev.intent.protocol.IntentScope;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * IntentSpec 规范校验：五要素齐备才允许注册发布。
 *
 * <p>规则：id 命名空间为 {@code 系统.域.动作}（三段小写）；scope 必填；
 * promptTemplate 必填；paramsSchema/outputSchema 必须是 object 型 JSON Schema。</p>
 *
 * <p>执行器引用校验需提供已注册执行器白名单（{@code runtime.executorIds()}），
 * 供管理端保存意图时与槽位校验同级拦截执行器错配。</p>
 */
public final class IntentSpecValidator {

    private static final Pattern ID_PATTERN = Pattern.compile("^[a-z][a-z0-9-]*(\\.[a-z][a-z0-9-]*){2}$");
    private static final Pattern TOOL_NAME_PATTERN = Pattern.compile("^[a-z][a-z0-9_]*$");
    /** 口语别名的规模上限：别名是"命中入口"，不是文案仓库；过多会拖慢匹配并冲淡语义。 */
    private static final int MAX_ALIASES = 8;
    private static final int MAX_ALIAS_LENGTH = 24;

    private IntentSpecValidator() {
    }

    public static List<String> validate(IntentSpec spec) {
        return validate(spec, null);
    }

    /**
     * 校验意图规范。
     *
     * @param registeredExecutorIds 已注册执行器 id 白名单（null/空 = 跳过执行器存在性校验，
     *                              供执行器注册前加载 classpath 种子规范的场景）
     */
    public static List<String> validate(IntentSpec spec, Set<String> registeredExecutorIds) {
        List<String> errors = new ArrayList<>();
        if (spec.getId() == null || !ID_PATTERN.matcher(spec.getId()).matches()) {
            errors.add("id 必须符合 `系统.域.动作` 三段小写命名（如 crm.customer.analyze）");
        }
        if (spec.getName() == null || spec.getName().isBlank()) {
            errors.add("name 必填");
        }
        if (spec.getPromptTemplate() == null || spec.getPromptTemplate().isBlank()) {
            errors.add("promptTemplate 必填");
        }
        if (spec.getScope() == null) {
            errors.add("scope 必填（local/remote/composite）");
        }
        if (spec.getScope() == IntentScope.REMOTE && blank(spec.getTargetSystem())) {
            errors.add("scope=remote 时 targetSystem 必填");
        }
        String executor = spec.getExecutor();
        if (!blank(executor) && registeredExecutorIds != null && !registeredExecutorIds.isEmpty()
                && !registeredExecutorIds.contains(executor.trim())) {
            errors.add("executor 引用的执行器未注册: " + executor
                    + "（已注册: " + registeredExecutorIds + "）");
        }
        checkObjectSchema("paramsSchema", spec.getParamsSchema(), errors);
        checkObjectSchema("outputSchema", spec.getOutputSchema(), errors);
        for (String tool : spec.getTools()) {
            if (!TOOL_NAME_PATTERN.matcher(tool).matches()) {
                errors.add("工具名不合法: " + tool);
            }
        }
        for (ContextField field : spec.getContext()) {
            if (field.key() == null || field.key().isBlank()) {
                errors.add("context 字段缺少 key");
            }
        }
        checkAliases(spec.getAliases(), errors);
        return errors;
    }

    /**
     * 口语别名校验：长度与条数设上限，防止把别名当描述写成长句（长句该进 description）。
     * 只做"能拦住明显失控"的约束，不强制必填——别名是增强项，缺省不影响意图可用。
     */
    private static void checkAliases(List<String> aliases, List<String> errors) {
        if (aliases == null || aliases.isEmpty()) {
            return;
        }
        if (aliases.size() > MAX_ALIASES) {
            errors.add("aliases 最多 " + MAX_ALIASES + " 条，当前 " + aliases.size() + " 条");
        }
        Set<String> seen = new java.util.LinkedHashSet<>();
        for (String alias : aliases) {
            if (alias == null || alias.isBlank()) {
                errors.add("aliases 存在空白条目");
                continue;
            }
            String trimmed = alias.trim();
            if (trimmed.length() > MAX_ALIAS_LENGTH) {
                errors.add("aliases 条目过长（> " + MAX_ALIAS_LENGTH + " 字）: " + trimmed);
            }
            if (!seen.add(trimmed)) {
                errors.add("aliases 条目重复: " + trimmed);
            }
        }
    }

    private static void checkObjectSchema(String name, Map<String, Object> schema, List<String> errors) {
        if (schema == null) {
            return;
        }
        if (!"object".equals(schema.get("type"))) {
            errors.add(name + " 必须是 type=object 的 JSON Schema");
        }
        if (schema.containsKey("properties") && !(schema.get("properties") instanceof Map)) {
            errors.add(name + ".properties 必须是对象");
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
