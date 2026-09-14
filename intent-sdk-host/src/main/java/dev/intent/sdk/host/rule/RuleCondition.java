package dev.intent.sdk.host.rule;

import java.util.List;

/**
 * 受限条件树：只支持可枚举、可静态校验的算子，刻意不做通用表达式引擎
 * （通用表达式等于把任意代码执行权交给配置文件）。
 *
 * <p>两种节点：</p>
 * <ul>
 *   <li><b>分支</b>：{@code all} / {@code any} / {@code not}，可任意嵌套；</li>
 *   <li><b>叶子</b>：{@code op} + {@code left} + {@code right}，算子见 {@link #OPS}。</li>
 * </ul>
 *
 * @param all   全部成立（隐式 AND）
 * @param any   任一成立（OR）
 * @param not   取反
 * @param op    叶子算子：eq / ne / gt / gte / lt / lte / in / contains / isNull / notNull
 * @param left  左侧取值引用（row.x / ctx.y / 常量 / 函数）
 * @param right 右侧取值引用
 */
public record RuleCondition(
        List<RuleCondition> all,
        List<RuleCondition> any,
        RuleCondition not,
        String op,
        Object left,
        Object right) {

    /** 支持的算子白名单。 */
    public static final List<String> OPS = List.of(
            "eq", "ne", "gt", "gte", "lt", "lte", "in", "contains", "isNull", "notNull");

    public boolean isBranch() {
        return all != null || any != null || not != null;
    }

    public boolean isLeaf() {
        return op != null && !op.isBlank();
    }
}
