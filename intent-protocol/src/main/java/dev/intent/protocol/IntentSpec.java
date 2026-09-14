package dev.intent.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 意图规范（IntentSpec）：一个意图的结构化定义，五要素齐备才可发布。
 *
 * <ul>
 *   <li>① 标识与元数据：id（系统.域.动作）、name、version、scope</li>
 *   <li>② 输入规范：paramsSchema（JSON Schema）+ context（页面上下文声明）</li>
 *   <li>③ 执行编排：scope + promptTemplate + tools 白名单</li>
 *   <li>④ 输出规范：outputSchema（缺省为标准结果信封）+ cardType</li>
 *   <li>⑤ 治理策略：policy（角色/超时/重试）</li>
 * </ul>
 */
@JsonDeserialize(builder = IntentSpec.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class IntentSpec {

    /** 标准输出信封 Schema：UI 依此渲染，所有意图输出结构同构。 */
    public static final Map<String, Object> STANDARD_OUTPUT_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "title", Map.of("type", "string"),
                    "summary", Map.of("type", "string"),
                    "blocks", Map.of(
                            "type", "array",
                            "items", Map.of(
                                    "type", "object",
                                    "properties", Map.of(
                                            "kind", Map.of("type", "string",
                                                    "enum", List.of("text", "kv", "table", "list", "badges")),
                                            "title", Map.of("type", "string"),
                                            "text", Map.of("type", "string"),
                                            "items", Map.of(
                                                    "type", "array",
                                                    "items", Map.of(
                                                            "type", "object",
                                                            "properties", Map.of(
                                                                    "label", Map.of("type", "string"),
                                                                    "value", Map.of("type", "string")),
                                                            "required", List.of("label", "value"))),
                                            "columns", Map.of("type", "array", "items", Map.of("type", "string")),
                                            "rows", Map.of("type", "array", "items", Map.of("type", "array",
                                                    "items", Map.of("type", "string"))),
                                            "level", Map.of("type", "string",
                                                    "enum", List.of("info", "warning", "danger"))),
                                    "required", List.of("kind"))),
                    "followups", Map.of("type", "array",
                            "description", "本次结果里还需要用户跟进/核实的具体事项（3~5 条，每条一句话；无则留空）",
                            "items", Map.of("type", "string")),
                    "nextIntents", Map.of(
                            "type", "array",
                            "description", "推荐的下一步意图（intentId 必须来自意图目录）",
                            "items", Map.of(
                                    "type", "object",
                                    "properties", Map.of(
                                            "intentId", Map.of("type", "string"),
                                            "title", Map.of("type", "string"),
                                            "reason", Map.of("type", "string"),
                                            "params", Map.of("type", "object")),
                                    "required", List.of("intentId", "title")))),
            "required", List.of("title", "summary", "blocks"));

    private final String id;
    private final String name;
    private final String description;
    /** 口语别名：用户可能怎么说这件事（如"这客户咋样了"），供前端匹配框做自然语言命中。 */
    private final List<String> aliases;
    private final int version;
    private final IntentScope scope;
    private final String targetSystem;
    private final Map<String, Object> paramsSchema;
    private final List<ContextField> context;
    private final Map<String, Object> outputSchema;
    private final String cardType;
    private final List<String> tools;
    private final List<String> pages;
    private final String promptTemplate;
    private final String executor;
    private final Policy policy;

    private IntentSpec(Builder b) {
        this.id = b.id;
        this.name = b.name;
        this.description = b.description;
        this.aliases = b.aliases == null ? List.of() : List.copyOf(b.aliases);
        this.version = b.version;
        this.scope = b.scope == null ? IntentScope.LOCAL : b.scope;
        this.targetSystem = b.targetSystem;
        this.paramsSchema = b.paramsSchema == null ? Map.of("type", "object", "properties", Map.of()) : b.paramsSchema;
        this.context = b.context == null ? List.of() : List.copyOf(b.context);
        this.outputSchema = b.outputSchema == null ? STANDARD_OUTPUT_SCHEMA : b.outputSchema;
        this.cardType = b.cardType == null ? "generic" : b.cardType;
        this.tools = b.tools == null ? List.of() : List.copyOf(b.tools);
        this.pages = b.pages == null ? List.of() : List.copyOf(b.pages);
        this.promptTemplate = b.promptTemplate;
        this.executor = b.executor;
        this.policy = b.policy == null ? new Policy() : b.policy;
    }

    public static Builder builder(String id, String name, String promptTemplate) {
        return new Builder(id, name, promptTemplate);
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {
        private String id;
        private String name;
        private String promptTemplate;
        private String description;
        private List<String> aliases;
        private int version = 1;
        private IntentScope scope;
        private String targetSystem;
        private Map<String, Object> paramsSchema;
        private List<ContextField> context;
        private Map<String, Object> outputSchema;
        private String cardType;
        private List<String> tools;
        private List<String> pages;
        private String executor;
        private Policy policy;

        public Builder() {
        }

        private Builder(String id, String name, String promptTemplate) {
            this.id = id;
            this.name = name;
            this.promptTemplate = promptTemplate;
        }

        public Builder id(String v) { this.id = v; return this; }
        public Builder name(String v) { this.name = v; return this; }
        public Builder description(String v) { this.description = v; return this; }
        public Builder aliases(List<String> v) { this.aliases = v; return this; }
        public Builder promptTemplate(String v) { this.promptTemplate = v; return this; }
        public Builder version(int v) { this.version = v; return this; }
        public Builder scope(IntentScope v) { this.scope = v; return this; }
        public Builder targetSystem(String v) { this.targetSystem = v; return this; }
        public Builder paramsSchema(Map<String, Object> v) { this.paramsSchema = v; return this; }
        public Builder context(List<ContextField> v) { this.context = v; return this; }
        public Builder outputSchema(Map<String, Object> v) { this.outputSchema = v; return this; }
        public Builder cardType(String v) { this.cardType = v; return this; }
        public Builder tools(List<String> v) { this.tools = v; return this; }
        public Builder pages(List<String> v) { this.pages = v; return this; }
        public Builder executor(String v) { this.executor = v; return this; }
        public Builder policy(Policy v) { this.policy = v; return this; }
        public IntentSpec build() { return new IntentSpec(this); }
    }

    /** 治理策略：可用角色、超时、失败重试。 */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public static final class Policy {
        private List<String> roles = List.of("*");
        private int timeoutSeconds = 120;
        private int maxRetries = 1;

        public List<String> getRoles() { return roles; }
        public void setRoles(List<String> roles) { this.roles = roles == null ? List.of("*") : List.copyOf(roles); }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }

        public Policy() {
        }

        public Policy(List<String> roles, int timeoutSeconds, int maxRetries) {
            this.roles = roles == null ? List.of("*") : new ArrayList<>(roles);
            this.timeoutSeconds = timeoutSeconds;
            this.maxRetries = maxRetries;
        }
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    /** 口语别名（空列表 = 只按名称与描述匹配）。 */
    public List<String> getAliases() { return aliases; }
    public int getVersion() { return version; }
    public IntentScope getScope() { return scope; }
    public String getTargetSystem() { return targetSystem; }
    public Map<String, Object> getParamsSchema() { return paramsSchema; }
    public List<ContextField> getContext() { return context; }
    public Map<String, Object> getOutputSchema() { return outputSchema; }
    public String getCardType() { return cardType; }
    public List<String> getTools() { return tools; }
    /** 页面挂载点列表（如 crm/customer/index）；空 = 全局意图，出现在所有页面与意图中心。 */
    public List<String> getPages() { return pages; }
    public String getPromptTemplate() { return promptTemplate; }
    /** 执行器标识：builtin-agent（缺省，内置推理循环）或宿主注册的自定义执行器 id。 */
    public String getExecutor() { return executor; }
    public Policy getPolicy() { return policy; }
}
