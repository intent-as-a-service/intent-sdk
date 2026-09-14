package dev.intent.sdk.host;

import dev.intent.sdk.host.repository.InMemoryIntentConfigRepository;
import dev.intent.sdk.host.repository.InMemoryIntentSpecRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 一致性套件自身要能跑通——否则宿主照着用会得到假的安全感。 */
class HostConformanceTest {

    private static IntentHostConformance.Host memoryHost() {
        return new IntentHostConformance.Host() {
            private final IntentPermissionPolicy policy = IntentPermissionPolicy.roleBased();
            private final IntentSpecRepository specs = new InMemoryIntentSpecRepository();
            private final IntentConfigRepository configs = new InMemoryIntentConfigRepository();

            @Override
            public IntentPermissionPolicy permissionPolicy() {
                return policy;
            }

            @Override
            public IntentSpecRepository specRepository() {
                return specs;
            }

            @Override
            public IntentConfigRepository configRepository() {
                return configs;
            }
        };
    }

    @Test
    void defaultImplementationsPassConformance() {
        assertTrue(IntentHostConformance.check(memoryHost()).isEmpty(),
                () -> String.join("; ", IntentHostConformance.check(memoryHost())));
    }

    @Test
    void brokenPolicyIsReported() {
        IntentHostConformance.Host broken = new IntentHostConformance.Host() {
            @Override
            public IntentPermissionPolicy permissionPolicy() {
                return (principal, roles) -> false;
            }

            @Override
            public IntentSpecRepository specRepository() {
                return new InMemoryIntentSpecRepository();
            }

            @Override
            public IntentConfigRepository configRepository() {
                return new InMemoryIntentConfigRepository();
            }
        };
        assertTrue(IntentHostConformance.check(broken).size() >= 3, "全 False 的策略应被套件拦住");
    }
}
