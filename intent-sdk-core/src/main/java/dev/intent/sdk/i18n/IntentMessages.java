package dev.intent.sdk.i18n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * SDK 文案出口：所有面向用户的固定文案从这里取，不在代码里写死中文。
 *
 * <p>资源包 {@code dev/intent/sdk/i18n/messages*.properties}，默认兜底中文。
 * 宿主可在启动时调用 {@link #setLocale(Locale)} 切换（如按租户语言）。</p>
 *
 * <p>为什么必须有：开源产品集成的宿主可能面向多语言市场，
 * 写死中文的组件会直接挡住这类集成。</p>
 */
public final class IntentMessages {

    private static final String BUNDLE = "dev.intent.sdk.i18n.messages";
    private static final Locale FALLBACK = Locale.SIMPLIFIED_CHINESE;

    private static volatile Locale locale = FALLBACK;

    private IntentMessages() {
    }

    /** 切换文案语言（null 忽略）。 */
    public static void setLocale(Locale value) {
        if (value != null) {
            locale = value;
        }
    }

    public static Locale locale() {
        return locale;
    }

    /** 取文案并按 {@link MessageFormat} 填充占位符；缺 key 返回 key 本身，不抛异常。 */
    public static String get(String key, Object... args) {
        String pattern = pattern(key);
        if (args == null || args.length == 0) {
            return pattern;
        }
        try {
            return MessageFormat.format(pattern, args);
        } catch (IllegalArgumentException e) {
            return pattern;
        }
    }

    /**
     * 关键：禁用 ResourceBundle 的「默认 locale 兜底」。
     *
     * <p>不禁用的话，{@code getBundle(BUNDLE, zh_CN)} 在 JVM 默认 locale 为英文的机器上
     * （几乎所有 Linux 服务器 / CI / 容器）不会落到 base 包，而是优先命中
     * {@code messages_en.properties} —— 于是 {@link #setLocale(Locale)} 显式锁了中文也不生效，
     * 宿主以为语言已定，实际按宿主机器的 locale 走。这是实测踩出来的：同一份测试在中文
     * Windows 上绿、在 en_US 的 CI runner 上红，断言的中文文案被渲染成了英文。</p>
     */
    private static final ResourceBundle.Control CONTROL =
        ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);

    private static String pattern(String key) {
        if (key == null) {
            return "";
        }
        try {
            return ResourceBundle.getBundle(BUNDLE, locale, CONTROL).getString(key);
        } catch (MissingResourceException ignored) {
            // 落到兜底语言
        }
        try {
            return ResourceBundle.getBundle(BUNDLE, FALLBACK, CONTROL).getString(key);
        } catch (MissingResourceException ignored) {
            return key;
        }
    }
}
