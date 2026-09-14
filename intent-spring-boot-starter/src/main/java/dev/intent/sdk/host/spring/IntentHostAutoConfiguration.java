package dev.intent.sdk.host.spring;

import dev.intent.protocol.IntentCatalogEntry;
import dev.intent.sdk.catalog.IntentCatalogEnricher;
import dev.intent.sdk.host.IntentCatalogEntries;
import dev.intent.sdk.host.IntentConfigRepository;
import dev.intent.sdk.host.IntentContextBridge;
import dev.intent.sdk.host.IntentPermissionPolicy;
import dev.intent.sdk.host.IntentPrincipalProvider;
import dev.intent.sdk.host.IntentSpecRepository;
import dev.intent.sdk.host.repository.FileIntentConfigRepository;
import dev.intent.sdk.host.repository.FileIntentSpecRepository;
import dev.intent.sdk.host.repository.InMemoryIntentConfigRepository;
import dev.intent.sdk.host.repository.InMemoryIntentSpecRepository;
import dev.intent.sdk.host.rule.IntentFactProvider;
import dev.intent.sdk.host.rule.IntentRuleLoader;
import dev.intent.sdk.host.rule.IntentSuggestionRule;
import dev.intent.sdk.host.rule.RuleBasedIntentCatalogEnricher;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

/**
 * 宿主接入自动装配：把"复制 300 行样板"压成一个依赖。
 *
 * <p>所有 Bean 都是 {@code @ConditionalOnMissingBean}——宿主只要自己声明同类型 Bean，
 * 就以宿主的为准。所以最小接入是"什么都不做"，深度接入是"实现你想定制的那个接口"。</p>
 *
 * <p>装配顺序原则：先身份与权限（谁在用），再存储（意图与配置从哪来），
 * 最后规则（待办与推荐怎么算）。</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(IntentHostProperties.class)
@ConditionalOnProperty(prefix = "intent.host", name = "enabled", havingValue = "true", matchIfMissing = true)
public class IntentHostAutoConfiguration {

    /** 身份：默认匿名。生产环境宿主应提供实现（一般是读自己的登录态）。 */
    @Bean
    @ConditionalOnMissingBean(IntentPrincipalProvider.class)
    public IntentPrincipalProvider intentPrincipalProvider() {
        return IntentPrincipalProvider.anonymous();
    }

    /** 权限：默认按角色交集判定，覆盖绝大多数场景。 */
    @Bean
    @ConditionalOnMissingBean(IntentPermissionPolicy.class)
    public IntentPermissionPolicy intentPermissionPolicy() {
        return IntentPermissionPolicy.roleBased();
    }

    /** 上下文桥：默认透传。宿主若有线程池/多租户，应提供实现。 */
    @Bean
    @ConditionalOnMissingBean(IntentContextBridge.class)
    public IntentContextBridge intentContextBridge() {
        return IntentContextBridge.passthrough();
    }

    /** 意图仓储：默认内存（零建表），可切文件。 */
    @Bean
    @ConditionalOnMissingBean(IntentSpecRepository.class)
    public IntentSpecRepository intentSpecRepository(IntentHostProperties properties) {
        if (properties.getStorage() == IntentHostProperties.Storage.FILE) {
            return new FileIntentSpecRepository(Path.of(properties.getSpecDir()));
        }
        return new InMemoryIntentSpecRepository();
    }

    /** 配置仓储：默认内存（零建表），可切文件。 */
    @Bean
    @ConditionalOnMissingBean(IntentConfigRepository.class)
    public IntentConfigRepository intentConfigRepository(IntentHostProperties properties) {
        if (properties.getStorage() == IntentHostProperties.Storage.FILE) {
            return new FileIntentConfigRepository(Path.of(properties.getConfigDir()));
        }
        return new InMemoryIntentConfigRepository();
    }

    /** 事实数据源：默认空数据集（宿主用规则时提供实现）。 */
    @Bean
    @ConditionalOnMissingBean(IntentFactProvider.class)
    public IntentFactProvider intentFactProvider() {
        return (datasetId, context, limit) -> List.of();
    }

    /**
     * 声明式规则增强器：加载 {@code intent.host.rule-dir} 下的规则并做契约校验。
     *
     * <p>校验失败直接抛异常让服务起不来——这是刻意的：规则写错却静默不出待办，
     * 是最难排查的一类故障。</p>
     */
    @Bean
    @ConditionalOnMissingBean(IntentCatalogEnricher.class)
    @ConditionalOnProperty(prefix = "intent.host", name = "rules-enabled",
            havingValue = "true", matchIfMissing = true)
    public RuleBasedIntentCatalogEnricher ruleBasedIntentCatalogEnricher(
            IntentHostProperties properties,
            IntentFactProvider facts,
            IntentSpecRepository specs) {
        List<IntentSuggestionRule> rules = IntentRuleLoader.loadDir(Path.of(properties.getRuleDir()));
        if (properties.isValidateRules() && !rules.isEmpty()) {
            Function<String, IntentCatalogEntry> lookup = id -> specs.findById(id)
                    .map(IntentCatalogEntries::of)
                    .orElse(null);
            IntentRuleLoader.validate(rules, lookup);
        }
        return new RuleBasedIntentCatalogEnricher(rules, facts);
    }
}