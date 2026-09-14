package dev.intent.sdk.spec;

import dev.intent.protocol.IntentSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 执行器引用校验：保存意图时与槽位校验同级拦截执行器错配。 */
class IntentSpecValidatorTest {

    private static IntentSpec spec(String executor) {
        IntentSpec.Builder builder = new IntentSpec.Builder()
                .id("crm.customer.analyze")
                .name("测试意图")
                .promptTemplate("测试模板");
        if (executor != null) {
            builder.executor(executor);
        }
        return builder.build();
    }

    @Test
    void 引用已注册执行器通过() {
        List<String> errors = IntentSpecValidator.validate(
                spec("sales-analyst"), Set.of("builtin-agent", "sales-analyst"));
        assertTrue(errors.isEmpty());
    }

    @Test
    void 引用未注册执行器报错() {
        List<String> errors = IntentSpecValidator.validate(spec("ghost"), Set.of("builtin-agent"));
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("ghost"));
    }

    @Test
    void 缺省执行器不做白名单校验() {
        assertTrue(IntentSpecValidator.validate(spec(null), Set.of("builtin-agent")).isEmpty());
    }

    @Test
    void 白名单为空或未提供时跳过执行器校验() {
        assertTrue(IntentSpecValidator.validate(spec("ghost"), null).isEmpty());
        assertTrue(IntentSpecValidator.validate(spec("ghost"), Set.of()).isEmpty());
    }

    // ------------------------------------------------------------ 口语别名

    @Test
    void 口语别名合法时通过() {
        IntentSpec spec = new IntentSpec.Builder().id("crm.customer.analyze").name("分析此客户")
                .promptTemplate("模板")
                .aliases(List.of("这客户咋样了", "看一下客户情况", "客户最近怎么样"))
                .build();
        assertTrue(IntentSpecValidator.validate(spec, null).isEmpty());
    }

    @Test
    void 口语别名超长与重复被拦截() {
        IntentSpec spec = new IntentSpec.Builder().id("crm.customer.analyze").name("分析此客户")
                .promptTemplate("模板")
                .aliases(List.of("这是一条特别特别长的别名用来模拟把描述写进别名的场景导致匹配被冲淡",
                        "重复别名", "重复别名"))
                .build();
        List<String> errors = IntentSpecValidator.validate(spec, null);
        assertEquals(2, errors.size());
        assertTrue(errors.get(0).contains("过长"));
        assertTrue(errors.get(1).contains("重复"));
    }

    @Test
    void 口语别名条数超限被拦截() {
        IntentSpec spec = new IntentSpec.Builder().id("crm.customer.analyze").name("分析此客户")
                .promptTemplate("模板")
                .aliases(List.of("a", "b", "c", "d", "e", "f", "g", "h", "i"))
                .build();
        List<String> errors = IntentSpecValidator.validate(spec, null);
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("最多 8 条"));
    }

    @Test
    void 别名缺省不影响意图可用() {
        assertTrue(IntentSpecValidator.validate(spec(null), null).isEmpty());
    }
}
