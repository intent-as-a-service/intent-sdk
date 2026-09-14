package dev.intent.sdk.host.rule;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import dev.intent.protocol.IntentCatalogEntry;
import dev.intent.sdk.schema.JsonSchemaValidator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * 规则加载器：YAML → 规则对象，<b>加载即校验，不合法直接抛</b>。
 *
 * <p>两条校验线：</p>
 * <ol>
 *   <li><b>自校验</b>（不需要意图目录）：必填字段、算子白名单、模板里 ctx 字段名；</li>
 *   <li><b>对目录校验</b>（需要意图目录）：目标意图存在、参数在该意图 Schema 内、
 *       必填参数可映射（规则填不出来且上下文也补不上 = 一点就弹表单，个性化就白做了）。</li>
 * </ol>
 */
public final class IntentRuleLoader {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private IntentRuleLoader() {
    }

    /** 从文件加载（YAML）。 */
    public static List<IntentSuggestionRule> load(Path file) {
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            return parse(content, file.toString());
        } catch (IOException e) {
            throw new UncheckedIOException("读取规则文件失败: " + file, e);
        }
    }

    /** 目录批量加载：所有 *.yaml / *.yml / *.json，按文件名排序保证顺序稳定。 */
    public static List<IntentSuggestionRule> loadDir(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            List<Path> targets = files
                    .filter(Files::isRegularFile)
                    .filter(IntentRuleLoader::isRuleFile)
                    .sorted()
                    .toList();
            List<IntentSuggestionRule> rules = new ArrayList<>();
            for (Path target : targets) {
                rules.addAll(load(target));
            }
            return List.copyOf(rules);
        } catch (IOException e) {
            throw new UncheckedIOException("扫描规则目录失败: " + dir, e);
        }
    }

    /** 解析并做自校验。 */
    public static List<IntentSuggestionRule> parse(String content, String source) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        List<IntentSuggestionRule> rules;
        try {
            rules = YAML.readValue(content, new TypeReference<List<IntentSuggestionRule>>() {
            });
        } catch (IOException e) {
            throw new IntentRuleException(source, List.of("YAML 解析失败: " + e.getMessage()));
        }
        if (rules == null) {
            return List.of();
        }
        List<String> problems = new ArrayList<>();
        Set<String> seenIds = new LinkedHashSet<>();
        for (IntentSuggestionRule rule : rules) {
            if (rule == null) {
                problems.add("存在空规则条目");
                continue;
            }
            String prefix = rule.id() == null ? "<无 id>" : rule.id();
            rule.selfCheck().forEach(p -> problems.add(prefix + ": " + p));
            if (rule.id() != null && !seenIds.add(rule.id())) {
                problems.add(prefix + ": 规则 id 重复");
            }
            checkCondition(rule.filter(), prefix, problems);
            checkTemplates(rule, prefix, problems);
        }
        if (!problems.isEmpty()) {
            throw new IntentRuleException(source, problems);
        }
        return List.copyOf(rules);
    }

    /**
     * 对意图目录做深度校验：目标意图存在、参数合法、必填参数可映射。
     *
     * @param lookup 意图编号 → 目录项（含 paramsSchema 与 context 声明）
     */
    public static void validate(List<IntentSuggestionRule> rules,
            Function<String, IntentCatalogEntry> lookup) {
        if (rules == null || rules.isEmpty() || lookup == null) {
            return;
        }
        List<String> problems = new ArrayList<>();
        for (IntentSuggestionRule rule : rules) {
            if (rule == null || rule.intentId() == null) {
                continue;
            }
            IntentCatalogEntry entry = lookup.apply(rule.intentId());
            if (entry == null) {
                problems.add(rule.id() + ": 引用了未登记（或对当前用户不可见）的意图 " + rule.intentId());
                continue;
            }
            if (!rule.hasItems()) {
                continue;
            }
            Map<String, Object> schema = entry.paramsSchema() == null ? Map.of() : entry.paramsSchema();
            Set<String> declared = declaredProperties(schema);
            for (String key : rule.items().params().keySet()) {
                if (!declared.contains(key)) {
                    problems.add(rule.id() + ": 参数 " + key + " 未在意图 " + rule.intentId() + " 的入参 Schema 中声明");
                }
            }
            Set<String> contextKeys = contextKeysOf(entry);
            for (String required : JsonSchemaValidator.requiredFields(schema)) {
                boolean mapped = rule.items().params().containsKey(required)
                        && rule.items().params().get(required) != null;
                if (!mapped && !contextKeys.contains(required)) {
                    problems.add(rule.id() + ": 必填参数 " + required
                            + " 既没有映射，上下文也补不上——点击会弹补参表单，个性化就白做了");
                }
            }
        }
        if (!problems.isEmpty()) {
            throw new IntentRuleException("规则-目录契约校验", problems);
        }
    }

    private static void checkCondition(RuleCondition condition, String prefix, List<String> problems) {
        if (condition == null) {
            return;
        }
        if (condition.all() != null) {
            condition.all().forEach(child -> checkCondition(child, prefix, problems));
        }
        if (condition.any() != null) {
            condition.any().forEach(child -> checkCondition(child, prefix, problems));
        }
        if (condition.not() != null) {
            checkCondition(condition.not(), prefix, problems);
        }
        if (condition.isLeaf() && !RuleCondition.OPS.contains(condition.op())) {
            problems.add(prefix + ": 不支持的算子 " + condition.op() + "，可用算子 " + RuleCondition.OPS);
        }
        if (condition.isLeaf() && !condition.isBranch()) {
            if (condition.left() == null) {
                problems.add(prefix + ": 条件缺少 left");
            }
            if (!"isNull".equals(condition.op()) && !"notNull".equals(condition.op())
                    && condition.right() == null) {
                problems.add(prefix + ": 条件 " + condition.op() + " 缺少 right");
            }
        }
        if (!condition.isLeaf() && !condition.isBranch()) {
            problems.add(prefix + ": 条件节点既不是分支也没有算子");
        }
    }

    private static void checkTemplates(IntentSuggestionRule rule, String prefix, List<String> problems) {
        if (!rule.hasItems()) {
            return;
        }
        List<String> templates = new ArrayList<>();
        templates.add(rule.items().title());
        if (rule.items().subtitle() != null) {
            templates.add(rule.items().subtitle());
        }
        if (rule.items().reason() != null) {
            templates.add(rule.items().reason());
        }
        if (rule.hasBadge()) {
            templates.add(rule.badge().text());
        }
        for (String template : templates) {
            for (String key : TemplateRenderer.referencedContextKeys(template)) {
                if (!TemplateRenderer.CONTEXT_KEYS.contains(key)) {
                    problems.add(prefix + ": 模板引用了未知上下文字段 ctx." + key
                            + "，可用字段 " + TemplateRenderer.CONTEXT_KEYS);
                }
            }
        }
    }

    private static Set<String> declaredProperties(Map<String, Object> schema) {
        Object properties = schema.get("properties");
        if (properties instanceof Map<?, ?> map) {
            Set<String> keys = new LinkedHashSet<>();
            map.keySet().forEach(key -> keys.add(String.valueOf(key)));
            return keys;
        }
        return Set.of();
    }

    private static Set<String> contextKeysOf(IntentCatalogEntry entry) {
        Set<String> keys = new LinkedHashSet<>();
        if (entry.context() != null) {
            entry.context().forEach(field -> {
                if (field != null && field.key() != null) {
                    keys.add(field.key());
                }
            });
        }
        return keys;
    }

    private static boolean isRuleFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".yaml") || name.endsWith(".yml") || name.endsWith(".json");
    }
}