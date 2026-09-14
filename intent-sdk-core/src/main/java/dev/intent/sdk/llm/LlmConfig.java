package dev.intent.sdk.llm;

/**
 * 模型接入配置。SDK 通过 pi-ai 统一流式 API 接入任意供应商；
 * OpenAI 兼容端点（DeepSeek / SiliconFlow / vLLM / OneAPI 等）均走
 * {@code api=openai-completions} + baseUrl。
 *
 * @param baseUrl    端点根地址（如 https://api.deepseek.com）
 * @param api        pi-ai 协议适配器标识（openai-completions / anthropic-messages / ...）
 * @param provider   供应商标识（用于环境变量 API Key 兜底解析，如 DEEPSEEK_API_KEY）
 * @param modelId    模型 ID（如 deepseek-chat）
 * @param modelName  展示名
 * @param apiKey     API Key；为空时回退到环境变量解析
 * @param contextWindow 上下文窗口
 * @param maxTokens      单次最大输出
 */
public record LlmConfig(
        String baseUrl,
        String api,
        String provider,
        String modelId,
        String modelName,
        String apiKey,
        long contextWindow,
        int maxTokens) {

    public LlmConfig {
        if (api == null || api.isBlank()) {
            api = "openai-completions";
        }
        if (provider == null || provider.isBlank()) {
            provider = "custom";
        }
        if (contextWindow <= 0) {
            contextWindow = 128_000;
        }
        if (maxTokens <= 0) {
            maxTokens = 8_192;
        }
    }

    /** 预置：DeepSeek（OpenAI 兼容）。 */
    public static LlmConfig deepseek(String apiKey) {
        return new LlmConfig("https://api.deepseek.com", "openai-completions", "deepseek",
                "deepseek-chat", "DeepSeek Chat", apiKey, 131_072, 8_192);
    }

    /** 预置：OpenAI。 */
    public static LlmConfig openai(String apiKey, String modelId) {
        return new LlmConfig("https://api.openai.com/v1", "openai-completions", "openai",
                modelId, modelId, apiKey, 128_000, 8_192);
    }

    /** 预置：Anthropic。 */
    public static LlmConfig anthropic(String apiKey, String modelId) {
        return new LlmConfig("https://api.anthropic.com", "anthropic-messages", "anthropic",
                modelId, modelId, apiKey, 200_000, 8_192);
    }

    /** 预置：任意 OpenAI 兼容端点（vLLM / OneAPI / SiliconFlow ...）。 */
    public static LlmConfig openAiCompatible(String baseUrl, String apiKey, String modelId) {
        return new LlmConfig(baseUrl, "openai-completions", "custom", modelId, modelId, apiKey, 128_000, 8_192);
    }
}
