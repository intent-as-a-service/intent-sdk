package dev.intent.sdk.host.rule;

import java.util.List;

/**
 * 规则不合法异常：加载期抛出，<b>让服务起不来</b>而不是静默降级。
 *
 * <p>静默降级是这类系统的头号故障源——规则写错了、意图 id 拼错了、
 * 参数没在 Schema 里声明，运行时只会"什么都不显示"，没人知道是配置错了还是真没待办。</p>
 */
public final class IntentRuleException extends RuntimeException {

    private final List<String> problems;

    public IntentRuleException(String source, List<String> problems) {
        super("意图规则不合法 [" + source + "]: " + String.join("; ", problems));
        this.problems = problems == null ? List.of() : List.copyOf(problems);
    }

    public List<String> problems() {
        return problems;
    }
}
