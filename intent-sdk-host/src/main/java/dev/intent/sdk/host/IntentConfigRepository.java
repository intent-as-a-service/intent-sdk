package dev.intent.sdk.host;

import java.util.List;
import java.util.Optional;

/**
 * 意图配置仓储 SPI：上架开关与角色配置的持久化出口。
 *
 * <p>SDK 只认这个接口，因此宿主可以用 DB（MyBatis / JPA）、文件、配置中心，
 * 或者干脆用内存实现跑演示——<b>零建表模式</b>就靠内存/文件两个实现撑起来。</p>
 */
public interface IntentConfigRepository {

    List<IntentConfigState> findAll();

    void save(IntentConfigState state);

    default Optional<IntentConfigState> find(String intentId) {
        if (intentId == null) {
            return Optional.empty();
        }
        return findAll().stream().filter(s -> intentId.equals(s.intentId())).findFirst();
    }
}
