package dev.intent.sdk.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具入参 JSON Schema 构造器：宿主声明工具签名时使用。
 *
 * <p>SDK 自有实现（不依赖任何 agent 框架），产物就是标准的 JSON Schema Map——
 * 既进入模型提示词，也用于执行引擎的入参校验。</p>
 */
public final class Schemas {

    private Schemas() {
    }

    /** 对象 Schema：properties + required，禁止额外字段。 */
    public static Map<String, Object> object(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties == null ? Map.of() : properties);
        schema.put("required", required == null ? List.of() : required);
        schema.put("additionalProperties", false);
        return schema;
    }

    public static Map<String, Object> string(String description) {
        return typed("string", description);
    }

    public static Map<String, Object> number(String description) {
        return typed("number", description);
    }

    public static Map<String, Object> bool(String description) {
        return typed("boolean", description);
    }

    public static Map<String, Object> array(Map<String, Object> items, String description) {
        Map<String, Object> schema = typed("array", description);
        schema.put("items", items);
        return schema;
    }

    public static Map<String, Object> enumeration(List<String> values, String description) {
        Map<String, Object> schema = typed("string", description);
        schema.put("enum", values);
        return schema;
    }

    private static Map<String, Object> typed(String type, String description) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", type);
        if (description != null) {
            schema.put("description", description);
        }
        return schema;
    }
}