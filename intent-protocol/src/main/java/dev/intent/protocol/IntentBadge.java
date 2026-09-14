package dev.intent.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;

/**
 * 目录项的动态提示（徽标）：宿主按当前用户的业务事实求值，
 * 如「2 份合同即将到期」——文案与数字都来自宿主规则/查询，不经过 LLM。
 *
 * @param text  展示文案（宿主渲染后直接显示）
 * @param level 语义等级：info / warning / danger（前端据此着色，可空）
 * @param count 事实数量（可空；无待办时宿主不应返回该提示）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IntentBadge(String text, String level, Integer count) implements Serializable {

    public static final String LEVEL_INFO = "info";
    public static final String LEVEL_WARNING = "warning";
    public static final String LEVEL_DANGER = "danger";

    public static IntentBadge warning(String text, Integer count) {
        return new IntentBadge(text, LEVEL_WARNING, count);
    }
}
