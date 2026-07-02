package io.lumina.agent.rag;

import io.agentscope.core.embedding.EmbeddingModel;
import io.agentscope.core.embedding.dashscope.DashScopeTextEmbedding;
import io.agentscope.core.rag.Knowledge;
import io.agentscope.core.rag.knowledge.SimpleKnowledge;
import io.agentscope.core.rag.store.InMemoryStore;
import io.agentscope.core.rag.store.VDBStoreBase;
import io.lumina.agent.config.LuminaAgentProperties;
import io.lumina.agent.config.RagProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RAG 知识库自动配置
 *
 * <p>当 lumina.rag.enabled=true 时，自动创建 EmbeddingModel + VDBStoreBase + Knowledge。
 * 支持双 Store（InMemoryStore 开发 / QdrantStore 生产）配置切换。
 *
 * @author Lumina Team
 * @since 1.2.0
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "lumina.rag", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(RagProperties.class)
public class RagKnowledgeFactory {

    private final LuminaAgentProperties agentProperties;

    public RagKnowledgeFactory(LuminaAgentProperties agentProperties) {
        this.agentProperties = agentProperties;
    }

    @Bean
    public EmbeddingModel embeddingModel(RagProperties props) {
        String provider = props.getEmbedding().getProvider() != null
                ? props.getEmbedding().getProvider() : "dashscope";
        log.info("RAG Embedding 提供商: {}, 模型: {}, 维度: {}",
                provider, props.getEmbedding().getModel(), props.getEmbedding().getDimensions());

        switch (provider) {
            case "openai":
                return createOpenAIEmbedding(props);
            case "ollama":
                return createOllamaEmbedding(props);
            case "dashscope":
            default:
                return createDashScopeEmbedding(props);
        }
    }

    /**
     * DashScope Embedding（通义千问 text-embedding-v3）
     */
    private EmbeddingModel createDashScopeEmbedding(RagProperties props) {
        String apiKey = resolveEmbeddingApiKey(props);
        return DashScopeTextEmbedding.builder()
                .apiKey(apiKey)
                .modelName(props.getEmbedding().getModel())
                .dimensions(props.getEmbedding().getDimensions())
                .build();
    }

    /**
     * OpenAI 兼容 Embedding（适用于硅基流动 SiliconFlow、OpenAI、Azure OpenAI 等）
     *
     * <p>使用自定义 HTTP 实现而非 OpenAI Java SDK，避免强制发送 dimensions 参数
     * 导致 SiliconFlow 等 API 返回 400 错误。
     */
    private EmbeddingModel createOpenAIEmbedding(RagProperties props) {
        String apiKey = resolveEmbeddingApiKey(props);
        String baseUrl = props.getEmbedding().getBaseUrl() != null
                ? props.getEmbedding().getBaseUrl() : "https://api.openai.com/v1";
        return new OpenAICompatibleEmbeddingModel(
                apiKey,
                props.getEmbedding().getModel(),
                baseUrl,
                props.getEmbedding().getDimensions());
    }

    /**
     * Ollama 本地 Embedding（nomic-embed-text 等，无需 API Key）
     */
    private EmbeddingModel createOllamaEmbedding(RagProperties props) {
        String baseUrl = props.getEmbedding().getBaseUrl() != null
                ? props.getEmbedding().getBaseUrl() : "http://localhost:11434";
        return io.agentscope.core.embedding.ollama.OllamaTextEmbedding.builder()
                .baseUrl(baseUrl)
                .modelName(props.getEmbedding().getModel())
                .dimensions(props.getEmbedding().getDimensions())
                .build();
    }

    /**
     * 解析 Embedding API Key（优先 embedding 专用 Key，其次复用 LLM Key）
     */
    private String resolveEmbeddingApiKey(RagProperties props) {
        String key = props.getEmbedding().getApiKey();
        if (key != null && !key.isBlank()) {
            return key;
        }
        return getApiKey();
    }

    @Bean
    public VDBStoreBase embeddingStore(RagProperties props) {
        int dims = props.getEmbedding().getDimensions();
        try {
            if ("memory".equals(props.getStoreType())) {
                log.info("RAG 使用内存向量存储（开发模式，重启丢失）");
                return InMemoryStore.builder()
                        .dimensions(dims)
                        .build();
            } else {
                String qdrantHost = props.getQdrant().getHost();
                if (qdrantHost.contains(":6334")) {
                    qdrantHost = qdrantHost.replace(":6334", ":6333");
                }
                log.info("RAG 使用 Qdrant 向量存储(REST): host={}, collection={}",
                        qdrantHost, props.getQdrant().getCollection());
                return new QdrantRestStore(qdrantHost, props.getQdrant().getCollection(), dims);
            }
        } catch (Exception e) {
            throw new RuntimeException("向量存储初始化失败: " + props.getStoreType(), e);
        }
    }

    @Bean
    public Knowledge knowledge(EmbeddingModel embeddingModel, VDBStoreBase store) {
        log.info("RAG SimpleKnowledge 初始化完成");
        return SimpleKnowledge.builder()
                .embeddingModel(embeddingModel)
                .embeddingStore(store)
                .build();
    }

    private String getApiKey() {
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = agentProperties.getLlm().getApiKey();
        }
        return apiKey;
    }
}
