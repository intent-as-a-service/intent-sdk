package dev.intent.sdk.context;

import dev.intent.protocol.UserInfo;

/**
 * 意图执行线程上下文：执行期间把宿主登录用户挂到当前线程，
 * 宿主工具直接读取——权限与事务随宿主调用栈自然生效。
 */
public final class IntentThreadContext {

    private static final ThreadLocal<UserInfo> USER = new ThreadLocal<>();

    private IntentThreadContext() {
    }

    public static void setUser(UserInfo user) {
        USER.set(user);
    }

    public static UserInfo currentUser() {
        return USER.get();
    }

    public static void clear() {
        USER.remove();
    }
}
