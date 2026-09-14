package dev.intent.sdk.host;

/**
 * 身份提供者 SPI：SDK 由此获知"当前是谁在操作"，不再自己解析宿主安全框架。
 *
 * <p>宿主实现通常是读取自己的登录态（Spring Security / Shiro / Sa-Token / 自研 session），
 * 拆成接口后，SDK 侧再没有任何一行代码依赖具体安全框架。</p>
 */
@FunctionalInterface
public interface IntentPrincipalProvider {

    /** 当前身份；无登录态时返回 {@link IntentPrincipal#anonymous()} 而不是 null。 */
    IntentPrincipal current();

    static IntentPrincipalProvider anonymous() {
        return IntentPrincipal::anonymous;
    }
}
