package io.lumina.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * RAG 知识库配置
 *
 * @author Lumina Team
 * @since 1.2.0
 */
@Data
@ConfigurationProperties(prefix = "lumina.rag")
public class RagProperties {

    private boolean enabled = false;
    private String storeType = "qdrant";
    private String mode = "generic";
    private EmbeddingConfig embedding = new EmbeddingConfig();
    private RetrieveConfig retrieve = new RetrieveConfig();
    private ReaderConfig reader = new ReaderConfig();
    private QdrantConfig qdrant = new QdrantConfig();
    private RouterConfig router = new RouterConfig();
    private HybridConfig hybrid = new HybridConfig();
    private RerankConfig rerank = new RerankConfig();

    @Data
    public static class EmbeddingConfig {
        /**
         * Embedding 提供商（dashscope/openai/ollama，默认 dashscope）
         *
         * <p>openai 适用于所有 OpenAI 兼容服务（如硅基流动 SiliconFlow），需配合 baseUrl + apiKey。
         */
        private String provider = "dashscope";

        /**
         * Embedding API Key（openai 提供商必填）
         */
        private String apiKey;

        /**
         * Embedding 服务 Base URL（openai 兼容服务，如硅基流动 https://api.siliconflow.cn/v1）
         */
        private String baseUrl;

        /**
         * Embedding 模型名称
         */
        private String model = "text-embedding-v3";

        /**
         * 向量维度（需与模型一致）
         */
        private int dimensions = 1024;
    }

    @Data
    public static class RetrieveConfig {
        private int limit = 3;
        private double scoreThreshold = 0.3;
    }

    @Data
    public static class ReaderConfig {
        private int chunkSize = 512;
        private int overlap = 50;
        private String splitStrategy = "PARAGRAPH";
    }

    @Data
    public static class QdrantConfig {
        private String host = "localhost:6334";
        private String collection = "lumina_knowledge";
        private boolean tls = false;
    }

    /**
     * 多 Embedding 模型路由配置（E4）
     */
    @Data
    public static class RouterConfig {
        /** 是否启用多模型路由 */
        private boolean enabled = false;
        /** 路由策略：language（按语言检测）/ manual（手动指定） */
        private String strategy = "language";
        /** 默认模型名称（当路由无法决定时使用） */
        private String defaultModel = "default";
        /** 命名模型列表，每个都有完整的 EmbeddingConfig */
        private Map<String, EmbeddingConfig> models;
    }

    /**
     * 混合检索配置（向量 + 关键词 RRF 融合）
     *
     * @since 3.3.0
     */
    @Data
    public static class HybridConfig {
        /** 是否启用混合检索（false 则纯向量检索） */
        private boolean enabled = false;
        /** 向量路权重（0-1，默认 0.7） */
        private double vectorWeight = 0.7;
        /** 关键词路权重（0-1，默认 0.3） */
        private double keywordWeight = 0.3;
    }

    /**
     * Reranker 重排序配置
     *
     * <p>三模式可选：siliconflow（免费 API）、local（本地模型）、none（不重排）。
     *
     * @since 3.3.0
     */
    @Data
    public static class RerankConfig {
        /** 提供商：none / siliconflow / local */
        private String provider = "none";
        /** 模型名称（siliconflow 默认 BAAI/bge-reranker-v2-m3） */
        private String model;
        /** API Key（siliconflow 必填，local 不需要） */
        private String apiKey;
        /** Base URL（siliconflow 默认 https://api.siliconflow.cn/v1，local 为本地服务地址） */
        private String baseUrl;
        /** 重排序候选 Top-K（默认 10） */
        private int topK = 10;
    }
}
