package dev.intent.sdk.catalog;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * 目录增强求值上下文：宿主在<b>当前登录用户 + 当前对象</b>视角下求值事实
 * （数据权限随宿主调用栈生效，增强器无需自己解析身份）。
 *
 * <p>三个维度缺一不可，缺一个个性化就只能停在页面级：</p>
 * <ul>
 *   <li><b>身份</b>：userId / userName / tenantId；</li>
 *   <li><b>位置</b>：page，取值同 {@code IntentSpec.pages}；</li>
 *   <li><b>对象</b>：objectType / objectId，如 {@code customer} / {@code 123}。</li>
 * </ul>
 *
 * <p>时间维度显式带租户时区：跨时区部署时"还有几天到期"必须按用户所在时区算，
 * 不能直接取服务器时区。</p>
 *
 * @param userId     当前登录用户编号（可空 = 匿名/系统求值）
 * @param userName   当前登录用户昵称（可空）
 * @param tenantId   租户编号（可空 = 单租户/不区分）
 * @param page       当前页面标识（可空 = 意图中心/全局）
 * @param objectType 当前对象类型（可空，如 customer / contract）
 * @param objectId   当前对象编号（可空）
 * @param objectName 当前对象展示名（可空，用于理由文案）
 * @param timeZone   时区 ID（可空 = 系统默认），如 Asia/Shanghai
 */
public record IntentCatalogContext(
        String userId, String userName, String tenantId,
        String page, String objectType, String objectId, String objectName,
        String timeZone) {

    /** 全空上下文（匿名 + 无页面 + 无对象）。 */
    public static final IntentCatalogContext EMPTY =
            new IntentCatalogContext(null, null, null, null, null, null, null, null);

    /**
     * 兼容构造：只有身份与页面。
     * 保留它是为了让既有宿主与增强器不改代码即可升级。
     */
    public IntentCatalogContext(String userId, String userName, String page) {
        this(userId, userName, null, page, null, null, null, null);
    }

    /** 是否指向某个具体对象。 */
    public boolean hasObject() {
        return !isBlank(objectType) && !isBlank(objectId);
    }

    /**
     * 条目所指对象是否就是当前上下文对象（排序时对象匹配拿最高权重）。
     * id 允许传任意类型：宿主主键常是 Long，这里统一按字符串比对。
     */
    public boolean matchesObject(String type, Object id) {
        return hasObject() && objectType.equals(type) && objectId.equals(String.valueOf(id));
    }

    /**
     * 目录缓存键：必须覆盖<b>租户 + 用户 + 页面 + 对象</b>。
     * 少了任何一维，多租户或多对象之间就会串数据（这是最容易踩的坑）。
     */
    public String cacheKey() {
        return blankToDash(tenantId) + '|' + blankToDash(userId) + '|'
                + blankToDash(page) + '|' + blankToDash(objectType) + '|' + blankToDash(objectId);
    }

    /** 求值用的时区：配了就用配置，配错或没配退化为服务器时区。 */
    public ZoneId zone() {
        if (isBlank(timeZone)) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(timeZone);
        } catch (Exception e) {
            return ZoneId.systemDefault();
        }
    }

    /** 当前时刻（带时区），规则与增强器统一用它，避免各处 {@code LocalDateTime.now()}。 */
    public ZonedDateTime now() {
        return ZonedDateTime.now(zone());
    }

    /** 当前日期（租户时区）。 */
    public LocalDate today() {
        return now().toLocalDate();
    }

    /** 当前时间（租户时区，去时区信息）。 */
    public LocalDateTime localNow() {
        return now().toLocalDateTime();
    }

    private static String blankToDash(String value) {
        return isBlank(value) ? "-" : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
