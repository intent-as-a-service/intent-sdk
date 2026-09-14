package dev.intent.sdk.host.rule;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Objects;

/**
 * 条件求值器：只认白名单算子，分支节点递归求值。
 *
 * <p>比较语义（避免"数字比字符串"这类静默错误）：</p>
 * <ol>
 *   <li>两侧都能解释成时间 → 按时间比（ISO 日期字符串也走这条路）；</li>
 *   <li>两侧都能解释成数字 → 按数值比（10 与 "10" 视为相等）；</li>
 *   <li>其余 → 按字符串比（ISO 日期字典序即时间序，所以日期比较也安全）。</li>
 * </ol>
 */
public final class RuleEvaluator {

    private RuleEvaluator() {
    }

    /** 条件为空 = 不限制（返回 true）。 */
    public static boolean test(RuleCondition condition, RuleContext context) {
        if (condition == null) {
            return true;
        }
        if (condition.all() != null) {
            for (RuleCondition child : condition.all()) {
                if (!test(child, context)) {
                    return false;
                }
            }
            return true;
        }
        if (condition.any() != null) {
            for (RuleCondition child : condition.any()) {
                if (test(child, context)) {
                    return true;
                }
            }
            return false;
        }
        if (condition.not() != null) {
            return !test(condition.not(), context);
        }
        return leaf(condition, context);
    }

    private static boolean leaf(RuleCondition condition, RuleContext context) {
        String op = condition.op();
        Object left = context.resolve(RuleValue.parse(condition.left()));
        if ("isNull".equals(op)) {
            return left == null;
        }
        if ("notNull".equals(op)) {
            return left != null;
        }
        Object right = context.resolve(RuleValue.parse(condition.right()));
        if ("in".equals(op)) {
            return inList(left, right);
        }
        if ("contains".equals(op)) {
            return left != null && right != null
                    && String.valueOf(left).contains(String.valueOf(right));
        }
        Integer compared = compare(left, right);
        if (compared == null) {
            // 不可比较时：eq/ne 退化为等值判断，大小比较一律为假（宁可不出，也不给错的结果）
            return switch (op) {
                case "eq" -> Objects.equals(left, right);
                case "ne" -> !Objects.equals(left, right);
                default -> false;
            };
        }
        return switch (op) {
            case "eq" -> compared == 0;
            case "ne" -> compared != 0;
            case "gt" -> compared > 0;
            case "gte" -> compared >= 0;
            case "lt" -> compared < 0;
            case "lte" -> compared <= 0;
            default -> false;
        };
    }

    private static boolean inList(Object left, Object right) {
        if (right instanceof Collection<?> collection) {
            for (Object candidate : collection) {
                Integer compared = compare(left, candidate);
                if (compared != null && compared == 0) {
                    return true;
                }
                if (compared == null && Objects.equals(left, candidate)) {
                    return true;
                }
            }
            return false;
        }
        Integer compared = compare(left, right);
        return compared != null && compared == 0;
    }

    /** 返回比较结果；无法比较返回 null。 */
    public static Integer compare(Object left, Object right) {
        if (left == null || right == null) {
            return null;
        }
        LocalDateTime leftTime = RuleContext.toDateTime(left);
        LocalDateTime rightTime = RuleContext.toDateTime(right);
        if (leftTime != null && rightTime != null) {
            return leftTime.compareTo(rightTime);
        }
        BigDecimal leftNumber = RuleContext.toNumber(left);
        BigDecimal rightNumber = RuleContext.toNumber(right);
        if (leftNumber != null && rightNumber != null) {
            return leftNumber.compareTo(rightNumber);
        }
        if (left instanceof Boolean || right instanceof Boolean) {
            return null;
        }
        return String.valueOf(left).compareTo(String.valueOf(right));
    }
}