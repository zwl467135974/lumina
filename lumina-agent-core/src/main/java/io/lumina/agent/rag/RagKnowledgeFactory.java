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

import java.util.Map;

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
        // E4: 多 Embedding 模型路由
        if (props.getRouter().isEnabled() && props.getRouter().getModels() != null
                && !props.getRouter().getModels().isEmpty()) {
            return createEmbeddingRouter(props);
        }

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
     * 创建多模型路由器（E4）
     */
    private EmbeddingModel createEmbeddingRouter(RagProperties props) {
        RagProperties.RouterConfig routerConfig = props.getRouter();
        log.info("启用多 Embedding 模型路由: strategy={}, models={}", routerConfig.getStrategy(), routerConfig.getModels().keySet());

        Map<String, EmbeddingModel> models = EmbeddingRouter.buildModels(
                routerConfig,
                props.getEmbedding(),
                config -> createEmbeddingFromConfig(config, props));

        return new EmbeddingRouter(models, routerConfig.getDefaultModel(), routerConfig.getStrategy());
    }

    /**
     * 从单个 EmbeddingConfig 创建模型
     */
    private EmbeddingModel createEmbeddingFromConfig(RagProperties.EmbeddingConfig config, RagProperties props) {
        String provider = config.getProvider() != null ? config.getProvider() : "dashscope";
        switch (provider) {
            case "openai":
                return new OpenAICompatibleEmbeddingModel(
                        resolveEmbeddingApiKey(config, props),
                        config.getModel(),
                        config.getBaseUrl() != null ? config.getBaseUrl() : "https://api.openai.com/v1",
                        config.getDimensions());
            case "ollama":
                return io.agentscope.core.embedding.ollama.OllamaTextEmbedding.builder()
                        .baseUrl(config.getBaseUrl() != null ? config.getBaseUrl() : "http://localhost:11434")
                        .modelName(config.getModel())
                        .dimensions(config.getDimensions())
                        .build();
            case "dashscope":
            default:
                return DashScopeTextEmbedding.builder()
                        .apiKey(resolveEmbeddingApiKey(config, props))
                        .modelName(config.getModel())
                        .dimensions(config.getDimensions())
                        .build();
        }
    }

    /**
     * 解析 Embedding API Key（优先 config 专用 Key，其次复用 LLM Key）
     */
    private String resolveEmbeddingApiKey(RagProperties.EmbeddingConfig config, RagProperties props) {
        String key = config.getApiKey();
        if (key != null && !key.isBlank()) {
            return key;
        }
        key = props.getEmbedding().getApiKey();
        if (key != null && !key.isBlank()) {
            return key;
        }
        return getApiKey();
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
        String apiKey = System.getenv("RAG_EMBEDDING_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = agentProperties.getLlm().getApiKey();
        }
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = System.getenv("DASHSCOPE_API_KEY");
        }
        return apiKey;
    }
}
