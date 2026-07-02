package io.lumina.agent.model;

import java.util.Map;

/**
 * LLM Provider 预设
 *
 * <p>国产 OpenAI 兼容模型的 base-url 映射，用户配置 type=glm/kimi/doubao/minimax
 * 时自动填充对应 base-url（用户显式配置 base-url 时优先用户值）。
 *
 * @author Lumina Team
 * @since 1.3.0
 */
public final class ProviderPresets {

    private ProviderPresets() {
    }

    /**
     * Provider 别名 → 默认 Base URL
     */
    public static final Map<String, String> OPENAI_COMPATIBLE_PRESETS = Map.of(
            "glm",     "https://open.bigmodel.cn/api/paas/v4",
            "kimi",    "https://api.moonshot.cn/v1",
            "doubao",  "https://ark.cn-beijing.volces.com/api/v3",
            "minimax", "https://api.minimax.chat/v1",
            "deepseek","https://api.deepseek.com/v1",
            "yi",      "https://api.lingyiwanwu.com/v1",
            "qwen",    "https://dashscope.aliyuncs.com/compatible-mode/v1"
    );

    /**
     * 判断是否为 OpenAI 兼容的预设 Provider
     */
    public static boolean isOpenAICompatible(String type) {
        return OPENAI_COMPATIBLE_PRESETS.containsKey(type);
    }

    /**
     * 获取预设 Base URL
     */
    public static String getPresetBaseUrl(String type) {
        return OPENAI_COMPATIBLE_PRESETS.get(type);
    }
}
