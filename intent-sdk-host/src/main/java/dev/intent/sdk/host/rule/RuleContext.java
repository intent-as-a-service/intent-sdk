package dev.intent.sdk.host.rule;

import dev.intent.sdk.catalog.IntentCatalogContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Map;

/**
 * 规则求值环境：一行事实 + 当前上下文 + 聚合计数。
 *
 * <p>时间取值统一走 {@link IntentCatalogContext#now()}（租户时区），
 * 避免跨时区部署时把"还有几天到期"算错。</p>
 */
public final class RuleContext {

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final IntentCatalogContext context;
    private final Map<String, Object> row;
    private final int count;
    private final int matched;

    private RuleContext(IntentCatalogContext context, Map<String, Object> row, int count, int matched) {
        this.context = context;
        this.row = row;
        this.count = count;
        this.matched = matched;
    }

    public static RuleContext of(IntentCatalogContext context, Map<String, Object> row, int count) {
        return of(context, row, count, count);
    }

    public static RuleContext of(IntentCatalogContext context, Map<String, Object> row, int count, int matched) {
        return new RuleContext(context == null ? IntentCatalogContext.EMPTY : context, row, count, matched);
    }

    public IntentCatalogContext context() {
        return context;
    }

    public Map<String, Object> row() {
        return row;
    }

    public int count() {
        return count;
    }

    public int matched() {
        return matched;
    }

    /** 解析一个取值引用。 */
    public Object resolve(RuleValue value) {
        if (value == null) {
            return null;
        }
        return switch (value.kind()) {
            case LITERAL -> value.literal();
            case ROW -> row == null ? null : row.get(value.key());
            case CTX -> contextValue(value.key());
            case NOW -> context.localNow();
            case TODAY -> context.today();
            case DAYS_UNTIL -> {
                LocalDateTime target = toDateTime(resolve(value.inner()));
                yield target == null ? null
                        : java.time.temporal.ChronoUnit.DAYS.between(context.today(), target.toLocalDate());
            }
        };
    }

    /** 上下文字段取值：没有的字段返回 null（不抛错，让规则条件自然为假）。 */
    public Object contextValue(String key) {
        if (key == null) {
            return null;
        }
        return switch (key) {
            case "userId" -> context.userId();
            case "userName" -> context.userName();
            case "tenantId" -> context.tenantId();
            case "page" -> context.page();
            case "objectType" -> context.objectType();
            case "objectId" -> context.objectId();
            case "objectName" -> context.objectName();
            case "today" -> context.today();
            case "now" -> context.localNow();
            case "count" -> count;
            case "matched" -> matched;
            default -> null;
        };
    }

    /** 把任意时间表示折算成 {@link LocalDateTime}（用租户时区）。 */
    public static LocalDateTime toDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof LocalDate date) {
            return date.atStartOfDay();
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof Date date) {
            return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
        }
        if (value instanceof Instant instant) {
            return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        }
        if (value instanceof Number number) {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(number.longValue()), ZoneId.systemDefault());
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text.replace(' ', 'T'));
        } catch (Exception ignored) {
            // 继续尝试纯日期
        }
        try {
            return LocalDate.parse(text, ISO_DATE).atStartOfDay();
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 把任意数值表示折算成 {@link BigDecimal}；非数值返回 null。 */
    public static BigDecimal toNumber(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        if (value instanceof Boolean flag) {
            return flag ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
