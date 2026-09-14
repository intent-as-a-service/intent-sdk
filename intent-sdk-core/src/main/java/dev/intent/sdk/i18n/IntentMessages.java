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

    private static String pattern(String key) {
        if (key == null) {
            return "";
        }
        try {
            return ResourceBundle.getBundle(BUNDLE, locale).getString(key);
        } catch (MissingResourceException ignored) {
            // 落到兜底语言
        }
        try {
            return ResourceBundle.getBundle(BUNDLE, FALLBACK).getString(key);
        } catch (MissingResourceException ignored) {
            return key;
        }
    }
}
