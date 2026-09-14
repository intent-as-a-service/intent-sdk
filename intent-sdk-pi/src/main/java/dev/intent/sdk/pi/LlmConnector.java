package dev.intent.sdk.pi;

import dev.intent.sdk.executor.ExecutionOutcome;
import dev.intent.sdk.executor.ExecutionRequest;
import dev.intent.sdk.executor.ExecutorNotFoundException;
import dev.intent.sdk.executor.ExecutorProfile;
import dev.intent.sdk.executor.ExecutorProfileException;
import dev.intent.sdk.executor.ExecutorProfileValidator;
import dev.intent.sdk.executor.IntentExecutor;
import dev.intent.sdk.executor.KnowledgeRetriever;
import dev.intent.sdk.llm.LlmConfig;

import dev.pi.ai.Model;
import dev.pi.ai.Models;
import dev.pi.ai.StreamOptions;
import dev.pi.agent.StreamFn;

/**
 * LLM 连接器：把 {@link LlmConfig} 装配成 pi-ai 的 {@link Model} 与 {@link StreamFn}。
 *
 * <p>StreamFn 是模型供应商唯一注入点——更换供应商/接入企业网关只改配置，不改 SDK 逻辑。</p>
 */
public final class LlmConnector {

    private final LlmConfig config;
    private final Model model;

    public LlmConnector(LlmConfig config) {
        this.config = config;
        this.model = Model.builder(config.modelId(), config.modelName(), config.api(), config.provider(),
                        config.baseUrl())
                .contextWindow(config.contextWindow())
                .maxTokens(config.maxTokens())
                .build();
        Models.register(this.model);
    }

    public LlmConfig config() {
        return config;
    }

    public Model model() {
        return model;
    }

    /** 供 AgentLoop 使用的流式函数：注入配置中的 API Key（为空则回退环境变量解析）。 */
    public StreamFn streamFn() {
        return (model, context, options) -> {
            if (options.apiKey() == null && config.apiKey() != null && !config.apiKey().isBlank()) {
                options.apiKey(config.apiKey());
            }
            return Models.streamSimple(model, context, options);
        };
    }
}
