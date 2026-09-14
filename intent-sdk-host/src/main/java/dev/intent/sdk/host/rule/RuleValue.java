package dev.intent.sdk.host.rule;

import java.math.BigDecimal;

/**
 * 取值引用：规则里写 {@code row.endTime} / {@code ctx.objectId} / {@code now()} / 字面量，
 * 由这里统一解析成"怎么取值"，避免各处自己解析字符串。
 */
public final class RuleValue {

    public enum Kind {
        /** 字面量。 */
        LITERAL,
        /** 查询结果行字段：row.xxx */
        ROW,
        /** 上下文：ctx.xxx（userId / page / objectId / objectName / today / now） */
        CTX,
        /** 当前时刻（带租户时区）。 */
        NOW,
        /** 今天（带租户时区）。 */
        TODAY,
        /** 距离今天还有几天：daysUntil(x)，负数表示已过期。 */
        DAYS_UNTIL
    }

    private final Kind kind;
    private final String key;
    private final Object literal;
    private final RuleValue inner;

    private RuleValue(Kind kind, String key, Object literal, RuleValue inner) {
        this.kind = kind;
        this.key = key;
        this.literal = literal;
        this.inner = inner;
    }

    public static RuleValue literal(Object value) {
        return new RuleValue(Kind.LITERAL, null, value, null);
    }

    public static RuleValue row(String field) {
        return new RuleValue(Kind.ROW, field, null, null);
    }

    public static RuleValue ctx(String field) {
        return new RuleValue(Kind.CTX, field, null, null);
    }

    /** 解析 YAML 里的取值写法。无法识别的一律当字面量，绝不抛错。 */
    public static RuleValue parse(Object raw) {
        if (raw == null) {
            return literal(null);
        }
        if (raw instanceof Number || raw instanceof Boolean) {
            return literal(raw);
        }
        if (raw instanceof java.util.Collection<?> || raw instanceof java.util.Map<?, ?>) {
            return literal(raw);
        }
        String text = String.valueOf(raw).trim();
        if (text.startsWith("row.")) {
            return row(text.substring(4));
        }
        if (text.startsWith("ctx.")) {
            return ctx(text.substring(4));
        }
        if ("now()".equals(text)) {
            return new RuleValue(Kind.NOW, null, null, null);
        }
        if ("today()".equals(text)) {
            return new RuleValue(Kind.TODAY, null, null, null);
        }
        if (text.startsWith("daysUntil(") && text.endsWith(")")) {
            RuleValue inner = parse(text.substring("daysUntil(".length(), text.length() - 1));
            return new RuleValue(Kind.DAYS_UNTIL, null, null, inner);
        }
        BigDecimal number = RuleContext.toNumber(text);
        return number != null ? literal(number) : literal(text);
    }

    public Kind kind() {
        return kind;
    }

    public String key() {
        return key;
    }

    public Object literal() {
        return literal;
    }

    public RuleValue inner() {
        return inner;
    }

    /** 供报错信息使用的可读描述。 */
    public String describe() {
        return switch (kind) {
            case LITERAL -> String.valueOf(literal);
            case ROW -> "row." + key;
            case CTX -> "ctx." + key;
            case NOW -> "now()";
            case TODAY -> "today()";
            case DAYS_UNTIL -> "daysUntil(" + (inner == null ? "?" : inner.describe()) + ")";
        };
    }
}
