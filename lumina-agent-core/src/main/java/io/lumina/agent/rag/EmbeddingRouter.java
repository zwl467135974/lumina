package io.lumina.agent.rag;

import io.agentscope.core.embedding.EmbeddingModel;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import io.lumina.agent.config.RagProperties;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 多 Embedding 模型路由器
 *
 * <p>实现 {@link EmbeddingModel} 接口，根据文本内容自动检测语言并路由到对应的 Embedding 模型。
 * 当只配置了一个模型时，退化为直接代理。
 *
 * <p>语言检测策略：统计 CJK 字符占比，超过 30% 判定为中文，否则英文。
 *
 * @author Lumina Team
 * @since 2.1.0
 */
@Slf4j
public class EmbeddingRouter implements EmbeddingModel {

    private static final double CJK_THRESHOLD = 0.30;

    private final Map<String, EmbeddingModel> models;
    private final String defaultModelName;
    private final String strategy;
    private final int dimensions;
    private final String modelName;

    /**
     * @param models          命名模型映射（如 "zh" → DashScopeModel, "en" → OpenAIModel）
     * @param defaultModelName 默认模型名称（路由无法决定时使用）
     * @param strategy        路由策略（"language" 或 "manual"）
     */
    public EmbeddingRouter(Map<String, EmbeddingModel> models, String defaultModelName, String strategy) {
        this.models = models;
        this.defaultModelName = defaultModelName;
        this.strategy = strategy;
        EmbeddingModel defaultModel = models.get(defaultModelName);
        if (defaultModel == null) {
            defaultModel = models.values().iterator().next();
        }
        this.dimensions = defaultModel.getDimensions();
        this.modelName = "router(" + String.join(",", models.keySet()) + ")";

        log.info("EmbeddingRouter 初始化: strategy={}, models={}, default={}, dimensions={}",
                strategy, models.keySet(), defaultModelName, dimensions);
    }

    @Override
    public Mono<double[]> embed(ContentBlock content) {
        if (content == null) {
            return Mono.error(new IllegalArgumentException("ContentBlock cannot be null"));
        }

        String text = extractText(content);
        if (text == null || text.isBlank()) {
            return Mono.error(new IllegalArgumentException("文本内容为空"));
        }

        String routeName = route(text);
        EmbeddingModel target = models.get(routeName);
        if (target == null) {
            target = models.get(defaultModelName);
            if (target == null) {
                target = models.values().iterator().next();
            }
        }

        log.debug("Embedding 路由: route={}, model={}, textLen={}", routeName, target.getModelName(), text.length());
        return target.embed(content);
    }

    /**
     * 根据文本内容路由到对应模型
     */
    private String route(String text) {
        if (!"language".equals(strategy) || models.size() <= 1) {
            return defaultModelName;
        }

        boolean isChinese = isChineseText(text);
        if (isChinese && models.containsKey("zh")) {
            return "zh";
        }
        if (!isChinese && models.containsKey("en")) {
            return "en";
        }

        return defaultModelName;
    }

    /**
     * 检测文本是否主要为中文（CJK 字符占比超过阈值）
     */
    static boolean isChineseText(String text) {
        if (text == null || text.isEmpty()) return false;
        int cjkCount = 0;
        int letterCount = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (isCJK(ch)) {
                cjkCount++;
                letterCount++;
            } else if (Character.isLetter(ch)) {
                letterCount++;
            }
        }
        if (letterCount == 0) return false;
        double cjkRatio = (double) cjkCount / letterCount;
        return cjkRatio >= CJK_THRESHOLD;
    }

    private static boolean isCJK(char ch) {
        return (ch >= '\u4E00' && ch <= '\u9FFF')
                || (ch >= '\u3400' && ch <= '\u4DBF')
                || (ch >= '\uF900' && ch <= '\uFAFF')
                || (ch >= '\u3040' && ch <= '\u309F')
                || (ch >= '\u30A0' && ch <= '\u30FF');
    }

    private String extractText(ContentBlock content) {
        if (content instanceof TextBlock textBlock) {
            return textBlock.getText();
        }
        return content.toString();
    }

    @Override
    public String getModelName() {
        return modelName;
    }

    @Override
    public int getDimensions() {
        return dimensions;
    }

    /**
     * 构建多模型映射
     */
    public static Map<String, EmbeddingModel> buildModels(
            RagProperties.RouterConfig routerConfig,
            RagProperties.EmbeddingConfig defaultConfig,
            java.util.function.Function<RagProperties.EmbeddingConfig, EmbeddingModel> factory) {

        Map<String, EmbeddingModel> result = new LinkedHashMap<>();
        result.put(routerConfig.getDefaultModel(), factory.apply(defaultConfig));

        if (routerConfig.getModels() != null) {
            routerConfig.getModels().forEach((name, config) -> {
                result.put(name, factory.apply(config));
            });
        }
        return result;
    }
}
