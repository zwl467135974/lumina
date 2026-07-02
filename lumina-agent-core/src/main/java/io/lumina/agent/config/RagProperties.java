package io.lumina.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

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
        /**
         * 是否启用 TLS（生产环境 https 推荐 true，本地明文 gRPC 需 false）
         */
        private boolean tls = false;
    }
}
