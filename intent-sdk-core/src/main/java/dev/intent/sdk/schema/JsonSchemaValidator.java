package dev.intent.sdk.schema;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 最小 JSON Schema 校验器：覆盖意图入参/出参所需的子集。
 *
 * <p>支持：type（string/number/integer/boolean/object/array）、enum、required、
 * properties、items、additionalProperties=false。错误信息带字段路径，直接用于
 * 表单提示与执行失败原因。</p>
 */
public final class JsonSchemaValidator {

    private JsonSchemaValidator() {
    }

    /** 校验 value 是否符合 schema，返回错误列表（空列表 = 通过）。 */
    public static List<String> validate(Map<String, Object> schema, Object value) {
        List<String> errors = new java.util.ArrayList<>();
        validateNode(schema, value, "$", errors);
        return List.copyOf(errors);
    }

    /** 从 object schema 中提取 required 字段名列表。 */
    @SuppressWarnings("unchecked")
    public static List<String> requiredFields(Map<String, Object> schema) {
        if (schema == null) {
            return List.of();
        }
        Object required = schema.get("required");
        if (required instanceof Collection<?> col) {
            return col.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static void validateNode(Map<String, Object> schema, Object value, String path, List<String> errors) {
        if (schema == null) {
            return;
        }
        Object typeObj = schema.get("type");
        if (typeObj != null && value != null) {
            String type = String.valueOf(typeObj);
            switch (type) {
                case "string" -> {
                    if (!(value instanceof String)) {
                        errors.add(path + ": 应为字符串");
                        return;
                    }
                }
                case "number" -> {
                    if (!(value instanceof Number)) {
                        errors.add(path + ": 应为数字");
                        return;
                    }
                }
                case "integer" -> {
                    if (!(value instanceof Long || value instanceof Integer || value instanceof Short)) {
                        if (value instanceof Double d && d == Math.floor(d)) {
                            // 整数值的浮点数放行（LLM 常见输出形态）
                        } else {
                            errors.add(path + ": 应为整数");
                            return;
                        }
                    }
                }
                case "boolean" -> {
                    if (!(value instanceof Boolean)) {
                        errors.add(path + ": 应为布尔值");
                        return;
                    }
                }
                case "array" -> {
                    if (!(value instanceof List)) {
                        errors.add(path + ": 应为数组");
                        return;
                    }
                }
                case "object" -> {
                    if (!(value instanceof Map)) {
                        errors.add(path + ": 应为对象");
                        return;
                    }
                }
                default -> {
                    // 未知类型不校验
                }
            }
        }

        Object enumObj = schema.get("enum");
        if (enumObj instanceof Collection<?> allowed && value != null) {
            boolean hit = allowed.stream().anyMatch(a -> String.valueOf(a).equals(String.valueOf(value)));
            if (!hit) {
                errors.add(path + ": 取值必须为 " + allowed + " 之一");
                return;
            }
        }

        if (value instanceof Map<?, ?> map) {
            Map<String, Object> props = (Map<String, Object>) schema.get("properties");
            Object required = schema.get("required");
            if (required instanceof Collection<?> reqCol && props != null) {
                for (Object r : reqCol) {
                    String key = String.valueOf(r);
                    if (!map.containsKey(key) || map.get(key) == null
                            || (map.get(key) instanceof String s && s.isBlank())) {
                        errors.add(path + "." + key + ": 必填");
                    }
                }
            }
            if (Boolean.FALSE.equals(schema.get("additionalProperties")) && props != null) {
                for (Object k : map.keySet()) {
                    if (!props.containsKey(String.valueOf(k))) {
                        errors.add(path + "." + k + ": 不允许的额外字段");
                    }
                }
            }
            if (props != null) {
                Map<String, Object> valueMap = (Map<String, Object>) map;
                for (Map.Entry<String, Object> en : valueMap.entrySet()) {
                    Object propSchema = props.get(en.getKey());
                    if (propSchema instanceof Map<?, ?> propMap) {
                        validateNode((Map<String, Object>) propMap, en.getValue(),
                                path + "." + en.getKey(), errors);
                    }
                }
            }
        }

        if (value instanceof List<?> list) {
            Object items = schema.get("items");
            if (items instanceof Map<?, ?> itemMap) {
                for (int i = 0; i < list.size(); i++) {
                    validateNode((Map<String, Object>) itemMap, list.get(i), path + "[" + i + "]", errors);
                }
            }
        }
    }
}
