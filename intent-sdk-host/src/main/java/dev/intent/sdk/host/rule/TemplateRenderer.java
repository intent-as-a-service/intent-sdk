package dev.intent.sdk.host.rule;

import dev.intent.sdk.i18n.IntentMessages;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文案模板渲染：{{取值}} 与 {{取值 | 过滤器}}。
 *
 * <pre>
 * "合同「{{row.name}}」{{row.endTime | expiryText}}"
 * "客户「{{ctx.objectName}}」有 {{count}} 份合同即将到期"
 * </pre>
 *
 * <p>过滤器白名单：date（yyyy-MM-dd）、expiryText（到期文案，走 i18n）、
 * money（千分位两位小数）、days（整数天）。</p>
 */
public final class TemplateRenderer {

    /** 可用于模板的上下文字段（加载期据此校验，拼错字段名会被拦住）。 */
    public static final Set<String> CONTEXT_KEYS = Set.of(
            "userId", "userName", "tenantId", "page",
            "objectType", "objectId", "objectName", "today", "now", "count", "matched");

    private static final Pattern TOKEN = Pattern.compile("\\{\\{\\s*([^}]+?)\\s*}}");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private TemplateRenderer() {
    }

    public static String render(String template, RuleContext context) {
        if (template == null || template.isBlank()) {
            return template;
        }
        Matcher matcher = TOKEN.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String expression = matcher.group(1);
            String filter = null;
            int bar = expression.indexOf('|');
            if (bar >= 0) {
                filter = expression.substring(bar + 1).trim();
                expression = expression.substring(0, bar).trim();
            }
            Object value = switch (expression) {
                case "count" -> context.count();
                case "matched" -> context.matched();
                default -> context.resolve(RuleValue.parse(expression));
            };
            matcher.appendReplacement(out, Matcher.quoteReplacement(format(value, filter, context)));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** 抽出模板里引用的 {@code ctx.xxx} 字段名（加载期校验用）。 */
    public static Set<String> referencedContextKeys(String template) {
        Set<String> keys = new java.util.LinkedHashSet<>();
        if (template == null || template.isBlank()) {
            return keys;
        }
        Matcher matcher = TOKEN.matcher(template);
        while (matcher.find()) {
            String expression = matcher.group(1);
            int bar = expression.indexOf('|');
            if (bar >= 0) {
                expression = expression.substring(0, bar);
            }
            expression = expression.trim();
            if (expression.startsWith("ctx.")) {
                keys.add(expression.substring(4).trim());
            }
        }
        return keys;
    }

    private static String format(Object value, String filter, RuleContext context) {
        if (value == null) {
            return "";
        }
        if (filter == null || filter.isBlank()) {
            if (value instanceof LocalDateTime dateTime) {
                return DATE.format(dateTime);
            }
            if (value instanceof LocalDate date) {
                return DATE.format(date);
            }
            if (value instanceof BigDecimal decimal) {
                return decimal.stripTrailingZeros().toPlainString();
            }
            return String.valueOf(value);
        }
        return switch (filter) {
            case "date" -> {
                LocalDateTime dateTime = RuleContext.toDateTime(value);
                yield dateTime == null ? String.valueOf(value) : DATE.format(dateTime);
            }
            case "expiryText" -> expiryText(value, context);
            case "money" -> {
                BigDecimal number = RuleContext.toNumber(value);
                yield number == null ? String.valueOf(value) : String.format("%,.2f", number);
            }
            case "days" -> {
                BigDecimal number = RuleContext.toNumber(value);
                yield number == null ? String.valueOf(value) : String.valueOf(number.longValue());
            }
            default -> String.valueOf(value);
        };
    }

    /** 到期文案：带 i18n，按上下文时区算天数。 */
    private static String expiryText(Object value, RuleContext context) {
        LocalDateTime endTime = RuleContext.toDateTime(value);
        if (endTime == null) {
            return IntentMessages.get("intent.expiry.none");
        }
        long days = ChronoUnit.DAYS.between(context.context().today(), endTime.toLocalDate());
        if (days < 0) {
            return IntentMessages.get("intent.expiry.overdue", DATE.format(endTime));
        }
        if (days == 0) {
            return IntentMessages.get("intent.expiry.today");
        }
        return IntentMessages.get("intent.expiry.days", days, DATE.format(endTime));
    }
}
