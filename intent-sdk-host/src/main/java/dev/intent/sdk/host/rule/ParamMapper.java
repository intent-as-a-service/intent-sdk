package dev.intent.sdk.host.rule;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 参数映射器：把行字段 / 上下文 / 常量映射成目标意图的入参。
 *
 * <p>类型转换必须显式声明（type: string）——主键在库里是 bigint，
 * 而意图 Schema 里常声明为 string，不转就会出现"点击后参数校验不通过"这种低级故障。</p>
 */
public final class ParamMapper {

    private ParamMapper() {
    }

    public static Map<String, Object> map(IntentSuggestionRule.Items items, RuleContext context) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (items == null || items.params() == null) {
            return result;
        }
        for (Map.Entry<String, IntentSuggestionRule.ParamMapping> entry : items.params().entrySet()) {
            IntentSuggestionRule.ParamMapping mapping = entry.getValue();
            if (mapping == null) {
                continue;
            }
            Object raw = mapping.from() != null && !mapping.from().isBlank()
                    ? context.resolve(RuleValue.parse(mapping.from()))
                    : mapping.value();
            if (raw == null) {
                continue;
            }
            result.put(entry.getKey(), coerce(raw, mapping.type()));
        }
        return result;
    }

    /** 按声明类型收敛；未声明类型时保留原值（交给目标意图的 Schema 校验）。 */
    public static Object coerce(Object value, String type) {
        if (type == null || type.isBlank()) {
            return value;
        }
        return switch (type.trim().toLowerCase(Locale.ROOT)) {
            case "string", "str" -> asString(value);
            case "number", "int", "integer", "long" -> asNumber(value);
            case "boolean", "bool" -> asBoolean(value);
            default -> value;
        };
    }

    private static String asString(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        return String.valueOf(value);
    }

    private static Object asNumber(Object value) {
        BigDecimal number = RuleContext.toNumber(value);
        if (number == null) {
            return value;
        }
        BigDecimal stripped = number.stripTrailingZeros();
        return stripped.scale() <= 0 ? stripped.longValueExact() : stripped;
    }

    private static Object asBoolean(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        String text = String.valueOf(value).trim();
        return "true".equalsIgnoreCase(text) || "1".equals(text) || "y".equalsIgnoreCase(text);
    }
}