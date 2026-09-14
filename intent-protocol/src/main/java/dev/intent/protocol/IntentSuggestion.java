package dev.intent.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.util.Map;

/**
 * 动态意图建议：宿主按当前用户的事实「推」到用户眼前的一条可执行入口。
 *
 * <p>与静态意图目录的区别：文案与参数都是用户相关的——「合同「XX」还有 3 天到期」
 * 点击即带 contractId 执行对应意图，不需要用户再补参。</p>
 *
 * <ul>
 *   <li>{@link #KIND_ITEM}：条目型，params 已齐备，点击即执行；</li>
 *   <li>{@link #KIND_AGGREGATE}：聚合型（如「3 个客户本月未跟进」），
 *       前端应展开对象列表逐条办理，不做隐式批量。</li>
 * </ul>
 *
 * @param id       建议唯一标识（前端渲染 key 与去重用）
 * @param intentId 点击后执行的意图（必须对当前用户可见）
 * @param title    个性化文案（宿主渲染后的结果）
 * @param subtitle 补充说明（可空）
 * @param kind     item / aggregate
 * @param count    聚合型对象数（可空）
 * @param params   预置参数（必须符合目标意图的入参 Schema）
 * @param reason   为什么提示（可解释性，可空）
 * @param dedupKey 去重键（后续做打扰控制/已办回执，可空）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IntentSuggestion(
        String id,
        String intentId,
        String title,
        String subtitle,
        String kind,
        Integer count,
        Map<String, Object> params,
        String reason,
        String dedupKey) implements Serializable {

    public static final String KIND_ITEM = "item";
    public static final String KIND_AGGREGATE = "aggregate";

    /** 条目型建议：params 齐备、点击直达。 */
    public static IntentSuggestion item(String id, String intentId, String title,
            String subtitle, Map<String, Object> params, String reason) {
        return new IntentSuggestion(id, intentId, title, subtitle, KIND_ITEM, null, params, reason, id);
    }
}
