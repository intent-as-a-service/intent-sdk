package dev.intent.sdk.host.spring;

import dev.intent.sdk.host.IntentConfigRepository;
import dev.intent.sdk.host.IntentContextBridge;
import dev.intent.sdk.host.IntentPermissionPolicy;
import dev.intent.sdk.host.IntentPrincipal;
import dev.intent.sdk.host.IntentPrincipalProvider;
import dev.intent.sdk.host.IntentSpecRepository;
import dev.intent.sdk.host.rule.IntentFactProvider;
import dev.intent.sdk.host.rule.IntentRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Starter 装配行为：什么都不配能用（默认实现），宿主声明了就以宿主为准，
 * 规则写错则启动失败。
 */
class IntentHostAutoConfigurationTest {

    @Configuration(proxyBeanMethods = false)
    static class HostConfig {

        static final IntentPrincipalProvider PRINCIPAL =
                () -> IntentPrincipal.of("7", "宿主用户", "1", List.of("admin"));

        @Bean
        public IntentPrincipalProvider hostPrincipalProvider() {
            return PRINCIPAL;
        }
    }

    private static AnnotationConfigApplicationContext context(Map<String, Object> properties,
            Class<?>... extraConfig) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        if (!properties.isEmpty()) {
            ctx.getEnvironment().getPropertySources()
                    .addFirst(new MapPropertySource("test", properties));
        }
        // 宿主 Bean 先注册、自动装配后注册：与 Spring Boot 的 DeferredImportSelector
        // 语义一致（自动配置永远最后生效），@ConditionalOnMissingBean 才能看到宿主 Bean。
        if (extraConfig != null && extraConfig.length > 0) {
            ctx.register(extraConfig);
        }
        ctx.register(IntentHostAutoConfiguration.class);
        ctx.refresh();
        return ctx;
    }

    @Test
    void providesZeroConfigDefaults() {
        try (AnnotationConfigApplicationContext ctx = context(Map.of())) {
            assertNotNull(ctx.getBean(IntentPrincipalProvider.class));
            assertNotNull(ctx.getBean(IntentPermissionPolicy.class));
            assertNotNull(ctx.getBean(IntentContextBridge.class));
            assertNotNull(ctx.getBean(IntentSpecRepository.class));
            assertNotNull(ctx.getBean(IntentConfigRepository.class));
            assertNotNull(ctx.getBean(IntentFactProvider.class));
            assertTrue(ctx.getBean(IntentSpecRepository.class).findAll().isEmpty());
            assertTrue(ctx.getBean(IntentPermissionPolicy.class)
                    .canUse(IntentPrincipal.anonymous(), List.of("admin")) == false);
        }
    }

    @Test
    void hostBeansWinOverDefaults() {
        try (AnnotationConfigApplicationContext ctx = context(Map.of(), HostConfig.class)) {
            assertSame(HostConfig.PRINCIPAL, ctx.getBean(IntentPrincipalProvider.class));
            assertEquals("宿主用户", ctx.getBean(IntentPrincipalProvider.class).current().userName());
        }
    }

    @Test
    void rulesLoadFromConfiguredDirectory(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("crm.yaml"), """
                - id: demo.rule
                  intent: demo.intent
                  dataset: demo.ds
                  badge:
                    text: "{{count}} 条待办"
                  items:
                    title: "{{row.name}}"
                    reason: "演示"
                """, StandardCharsets.UTF_8);
        try (AnnotationConfigApplicationContext ctx = context(Map.of(
                "intent.host.rule-dir", dir.toString(),
                "intent.host.validate-rules", "false"))) {
            assertNotNull(ctx.getBean(dev.intent.sdk.host.rule.RuleBasedIntentCatalogEnricher.class));
            assertEquals(1, ctx.getBean(dev.intent.sdk.host.rule.RuleBasedIntentCatalogEnricher.class)
                    .rules().size());
        }
    }

    @Test
    void ruleReferencingUnknownIntentFailsStartup(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("broken.yaml"), """
                - id: broken.rule
                  intent: not.registered
                  dataset: demo.ds
                  items:
                    title: "t"
                    reason: "why"
                """, StandardCharsets.UTF_8);
        Exception error = assertThrows(Exception.class, () -> context(Map.of(
                "intent.host.rule-dir", dir.toString())).close());
        assertTrue(rootCause(error) instanceof IntentRuleException,
                "规则契约校验失败必须让启动失败，而不是静默降级");
    }

    private static Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
