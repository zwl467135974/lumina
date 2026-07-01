package io.lumina.agent.rag;

import io.agentscope.core.embedding.EmbeddingModel;
import io.agentscope.core.embedding.dashscope.DashScopeTextEmbedding;
import io.agentscope.core.rag.Knowledge;
import io.agentscope.core.rag.knowledge.SimpleKnowledge;
import io.agentscope.core.rag.store.InMemoryStore;
import io.agentscope.core.rag.store.QdrantStore;
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
        String apiKey = getApiKey();
        log.info("RAG Embedding 模型: {}, 维度: {}", props.getEmbedding().getModel(), props.getEmbedding().getDimensions());
        return DashScopeTextEmbedding.builder()
                .apiKey(apiKey)
                .modelName(props.getEmbedding().getModel())
                .dimensions(props.getEmbedding().getDimensions())
                .build();
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
                log.info("RAG 使用 Qdrant 向量存储: host={}, collection={}",
                        props.getQdrant().getHost(), props.getQdrant().getCollection());
                return QdrantStore.builder()
                        .location(props.getQdrant().getHost())
                        .collectionName(props.getQdrant().getCollection())
                        .dimensions(dims)
                        .build();
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
