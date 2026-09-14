package dev.intent.sdk.host;

import dev.intent.protocol.IntentSpec;

import java.util.List;
import java.util.Optional;

/**
 * 意图规范仓储 SPI：意图定义的来源出口。
 *
 * <p>三种典型实现，宿主按改造深度选一个：</p>
 * <ul>
 *   <li>{@code InMemoryIntentSpecRepository}：种子 YAML 加载进内存，重启还原（演示/最小接入）；</li>
 *   <li>{@code FileIntentSpecRepository}：直接以磁盘目录为准（无 DB 依赖，规格即文件）；</li>
 *   <li>宿主自研 DB 实现：后台可视化增删改（完整产品形态）。</li>
 * </ul>
 *
 * <p>「来源标记」用于判断某条规范可否删除：{@code builtin} 为 classpath 种子、{@code custom} 为后台创建。</p>
 */
public interface IntentSpecRepository {

    List<IntentSpec> findAll();

    /**
     * 新增或覆盖一条意图规范。
     *
     * @param spec   意图规范
     * @param source 来源标记（builtin = classpath 种子 / custom = 后台创建），用于判断可否删除
     */
    void upsert(IntentSpec spec, String source);

    boolean delete(String intentId);

    default Optional<IntentSpec> findById(String intentId) {
        if (intentId == null) {
            return Optional.empty();
        }
        return findAll().stream().filter(s -> intentId.equals(s.getId())).findFirst();
    }

    /** 来源标记：builtin 种子不可删。 */
    default String sourceOf(String intentId) {
        return "custom";
    }
}
