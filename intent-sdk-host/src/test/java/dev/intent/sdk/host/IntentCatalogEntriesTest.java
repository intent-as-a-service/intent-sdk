package dev.intent.sdk.host;

import dev.intent.protocol.IntentBadge;
import dev.intent.protocol.IntentCatalogEntry;
import dev.intent.protocol.IntentScope;
import dev.intent.protocol.IntentSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 口语别名在「规范 → 目录项」链路上的透传：别名是前端匹配框的输入，
 * 一旦在这一层掉了，前端只能拿到名称与描述，口语化入口就形同虚设。
 */
class IntentCatalogEntriesTest {

    private static IntentSpec spec(List<String> aliases) {
        IntentSpec.Builder builder = new IntentSpec.Builder()
                .id("crm.customer.analyze")
                .name("分析此客户")
                .promptTemplate("模板")
                .scope(IntentScope.LOCAL)
                .cardType("analysis")
                .pages(List.of("crm/customer"));
        if (aliases != null) {
            builder.aliases(aliases);
        }
        return builder.build();
    }

    @Test
    void 别名随目录项透出() {
        IntentCatalogEntry entry = IntentCatalogEntries.of(spec(List.of("这客户咋样了", "看看客户情况")));
        assertEquals(List.of("这客户咋样了", "看看客户情况"), entry.aliases());
    }

    @Test
    void 未声明别名时目录项为null不产生空数组噪音() {
        assertNull(IntentCatalogEntries.of(spec(null)).aliases());
        assertNull(IntentCatalogEntries.of(spec(List.of())).aliases());
    }

    @Test
    void 挂徽标不丢别名() {
        IntentCatalogEntry entry = IntentCatalogEntries.of(spec(List.of("这客户咋样了")))
                .withBadge(new IntentBadge("3 个客户待跟进", "warning", 3));
        assertNotNull(entry.badge());
        assertEquals(List.of("这客户咋样了"), entry.aliases());
        assertEquals("crm.customer.analyze", entry.id());
    }
}
