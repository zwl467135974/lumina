package io.lumina.agent.model;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LLM Provider 预设
 *
 * <p>国产 OpenAI 兼容模型的 base-url 映射，用户配置 type=glm/kimi/doubao/minimax
 * 时自动填充对应 base-url（用户显式配置 base-url 时优先用户值）。
 *
 * <p>支持运行时动态注册自定义预设（通过 {@link #register}），
 * 配合 Spring 配置 {@code lumina.agent.llm.presets} 可实现零代码扩展。
 *
 * @author Lumina Team
 * @since 1.3.0
 */
public final class ProviderPresets {

    private ProviderPresets() {
    }

    /**
     * 内置预设（不可变快照，供参考）
     */
    public static final Map<String, String> BUILTIN_PRESETS = Map.of(
            "glm",     "https://open.bigmodel.cn/api/paas/v4",
            "kimi",    "https://api.moonshot.cn/v1",
            "doubao",  "https://ark.cn-beijing.volces.com/api/v3",
            "minimax", "https://api.minimax.chat/v1",
            "deepseek","https://api.deepseek.com/v1",
            "yi",      "https://api.lingyiwanwu.com/v1",
            "qwen",    "https://dashscope.aliyuncs.com/compatible-mode/v1"
    );

    /**
     * 可变预设表（内置 + 动态注册），线程安全
     */
    private static final Map<String, String> PRESETS = new ConcurrentHashMap<>(BUILTIN_PRESETS);

    /**
     * 对外暴露的只读视图
     */
    public static final Map<String, String> OPENAI_COMPATIBLE_PRESETS = Collections.unmodifiableMap(PRESETS);

    /**
     * 动态注册自定义预设
     *
     * @param type    Provider 别名（如 "acme"）
     * @param baseUrl 默认 Base URL
     * @since 3.3.0
     */
    public static void register(String type, String baseUrl) {
        PRESETS.put(type, baseUrl);
    }

    /**
     * 批量注册
     *
     * @since 3.3.0
     */
    public static void registerAll(Map<String, String> presets) {
        if (presets != null) {
            PRESETS.putAll(presets);
        }
    }

    /**
     * 判断是否为 OpenAI 兼容的预设 Provider
     */
    public static boolean isOpenAICompatible(String type) {
        return PRESETS.containsKey(type);
    }

    /**
     * 获取预设 Base URL
     */
    public static String getPresetBaseUrl(String type) {
        return PRESETS.get(type);
    }
}
