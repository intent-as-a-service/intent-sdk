package dev.intent.sdk.catalog;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 上下文的兼容性、缓存键、对象匹配与时区安全。 */
class IntentCatalogContextTest {

    @Test
    void compatConstructorFillsDefaults() {
        IntentCatalogContext context = new IntentCatalogContext("1", "admin", "crm/customer");
        assertEquals("1", context.userId());
        assertEquals("crm/customer", context.page());
        assertTrue(context.tenantId() == null && context.objectId() == null);
        assertFalse(context.hasObject());
    }

    @Test
    void cacheKeyCoversTenantUserPageAndObject() {
        IntentCatalogContext a = new IntentCatalogContext("1", "admin", "1", "crm/customer", "customer", "9", "客户A", null);
        IntentCatalogContext b = new IntentCatalogContext("1", "admin", "1", "crm/customer", "customer", "10", "客户B", null);
        IntentCatalogContext c = new IntentCatalogContext("1", "admin", "2", "crm/customer", "customer", "9", "客户A", null);

        assertNotEquals(a.cacheKey(), b.cacheKey(), "不同对象不能共用缓存");
        assertNotEquals(a.cacheKey(), c.cacheKey(), "不同租户不能共用缓存");
    }

    @Test
    void cacheKeyIsStableForEmptyValues() {
        String key = IntentCatalogContext.EMPTY.cacheKey();
        assertEquals("-|-|-|-|-", key);
    }

    @Test
    void matchesObjectRequiresSameTypeAndId() {
        IntentCatalogContext context = new IntentCatalogContext("1", "admin", "1", "crm/customer", "customer", "9", "客户A", null);
        assertTrue(context.matchesObject("customer", "9"));
        assertTrue(context.matchesObject("customer", 9));
        assertFalse(context.matchesObject("contract", "9"));
        assertFalse(context.matchesObject("customer", "10"));
    }

    @Test
    void matchesObjectIsFalseWithoutObject() {
        IntentCatalogContext context = new IntentCatalogContext("1", "admin", "crm/customer");
        assertFalse(context.matchesObject("customer", "9"));
    }

    @Test
    void todayUsesConfiguredTimeZone() {
        IntentCatalogContext context = new IntentCatalogContext(
                "1", "admin", "1", "crm/customer", null, null, null, "Asia/Shanghai");
        assertEquals(ZoneId.of("Asia/Shanghai"), context.zone());
        assertEquals(LocalDate.now(ZoneId.of("Asia/Shanghai")), context.today());
    }

    @Test
    void invalidTimeZoneFallsBackToSystemDefault() {
        IntentCatalogContext context = new IntentCatalogContext(
                "1", "admin", "1", "crm/customer", null, null, null, "Not/AZone");
        assertEquals(ZoneId.systemDefault(), context.zone());
    }
}