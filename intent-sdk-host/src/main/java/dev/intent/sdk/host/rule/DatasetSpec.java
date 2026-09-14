package dev.intent.sdk.host.rule;

import java.util.List;
import java.util.Map;

/**
 * 数据集自描述：一个 {@code dataset} 可供规则使用哪些列。
 *
 * <p>为什么事实源要自报家门：规则可以手写，也可以由 AI 按自然语言生成。
 * 生成侧如果不知道"有哪些数据集、每行有哪些列"，就只能猜列名——猜出来的规则
 * 语法合法、语义错误，而且错得很隐蔽（条件永远为假 = 永远不出待办）。
 * 有了这份声明，生成与校验都变成"在已知集合里挑"，而不是凭想象写。</p>
 *
 * @param id          数据集标识（规则里 dataset 字段用它）
 * @param description 这个数据集代表什么业务事实（写给生成侧看）
 * @param fields      列名 → 说明（模板与条件只能引用这里声明过的列）
 */
public record DatasetSpec(String id, String description, Map<String, String> fields) {

    public DatasetSpec {
        fields = fields == null ? Map.of() : Map.copyOf(fields);
    }

    public static DatasetSpec of(String id, String description, Map<String, String> fields) {
        return new DatasetSpec(id, description, fields);
    }

    /** 生成提示词用的紧凑描述：数据集 + 说明 + 列名。 */
    public String describe() {
        StringBuilder sb = new StringBuilder("- ").append(id).append("：").append(description)
                .append("\n  可用列：");
        if (fields.isEmpty()) {
            sb.append("（无）");
        } else {
            List<String> parts = fields.entrySet().stream()
                    .map(e -> e.getKey() + "（" + e.getValue() + "）")
                    .toList();
            sb.append(String.join("、", parts));
        }
        return sb.toString();
    }
}
