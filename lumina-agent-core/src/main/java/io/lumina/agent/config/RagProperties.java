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
        private String model = "text-embedding-v3";
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
    }
}
