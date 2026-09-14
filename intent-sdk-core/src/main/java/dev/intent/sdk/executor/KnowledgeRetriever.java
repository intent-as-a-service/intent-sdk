package dev.intent.sdk.executor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 知识检索 SPI：流程编排中 knowledge 节点的检索能力来源。
 *
 * <p>宿主实现本接口并注册（如接企业文档服务 / 向量库 / 检索工具），
 * 一个实现可负责多个知识库标识（{@link #supports}）；未装配或无实现负责时，
 * knowledge 节点明确报错而非静默跳过。</p>
 */
public interface KnowledgeRetriever {

    /** 是否负责该知识库标识。 */
    boolean supports(String kb);

    /** 检索与查询语义最相关的至多 topK 条内容。 */
    List<KnowledgeChunk> retrieve(String kb, String query, int topK);

    /** 检索结果片段：内容 + 可选相关度与元数据。 */
    record KnowledgeChunk(String content, Double score, Map<String, Object> meta)
            implements Serializable {

        public KnowledgeChunk {
            meta = meta == null ? Map.of() : Map.copyOf(meta);
        }

        public static KnowledgeChunk of(String content) {
            return new KnowledgeChunk(content, null, Map.of());
        }
    }
}
