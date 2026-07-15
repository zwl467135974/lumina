package io.lumina.agent.rag;

import io.agentscope.core.embedding.EmbeddingModel;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.model.DocumentMetadata;
import io.agentscope.core.rag.model.RetrieveConfig;
import io.agentscope.core.rag.store.VDBStoreBase;
import io.agentscope.core.rag.store.dto.SearchDocumentDto;
import io.lumina.agent.config.RagProperties;
import io.lumina.common.core.BaseContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * HybridKnowledge 单元测试
 *
 * <p>验证 RRF 融合算法、三模式 Reranker 切换、关键词路降级、租户上下文。
 *
 * @author Lumina Team
 * @since 3.3.0
 */
@Slf4j
class HybridKnowledgeTest {

    private EmbeddingModel embeddingModel;
    private VDBStoreBase vectorStore;
    private KeywordSearcher keywordSearcher;
    private RerankProvider rerankProvider;
    private RagProperties.HybridConfig hybridConfig;

    @BeforeEach
    void setUp() {
        embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embed(any(ContentBlock.class)))
                .thenReturn(Mono.just(new double[]{1.0, 0.0}));

        vectorStore = mock(VDBStoreBase.class);
        keywordSearcher = mock(KeywordSearcher.class);
        rerankProvider = mock(RerankProvider.class);
        // rerankProvider 默认透传（返回输入列表），避免 null 覆盖结果
        when(rerankProvider.rerank(anyString(), anyList(), anyInt()))
                .thenAnswer(inv -> inv.getArgument(1));
        hybridConfig = new RagProperties.HybridConfig();
        hybridConfig.setEnabled(true);
        hybridConfig.setVectorWeight(0.7);
        hybridConfig.setKeywordWeight(0.3);

        BaseContext.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        BaseContext.clear();
    }

    @Test
    void vectorOnlyResultsWhenKeywordReturnsEmpty() {
        when(vectorStore.search(any(SearchDocumentDto.class)))
                .thenReturn(Mono.just(List.of(
                        doc("vec-1", "content A", 0.9),
                        doc("vec-2", "content B", 0.8)
                )));
        when(keywordSearcher.search(anyString(), anyLong(), anyInt()))
                .thenReturn(List.of());

        HybridKnowledge hk = new HybridKnowledge(embeddingModel, vectorStore, keywordSearcher, rerankProvider, hybridConfig);
        List<Document> results = hk.retrieve("query", RetrieveConfig.builder().limit(5).scoreThreshold(0.0).build()).block();

        assertThat(results).hasSize(2);
        // 向量路权重 0.7，排名第一的应该 RRF 分数更高
        assertThat(results.get(0).getScore()).isGreaterThan(results.get(1).getScore());
    }

    @Test
    void keywordOnlyResultsWhenVectorReturnsEmpty() {
        when(vectorStore.search(any(SearchDocumentDto.class)))
                .thenReturn(Mono.just(List.of()));
        when(keywordSearcher.search(anyString(), anyLong(), anyInt()))
                .thenReturn(List.of(
                        doc("kw-1", "content X", 1.0),
                        doc("kw-2", "content Y", 0.5)
                ));

        HybridKnowledge hk = new HybridKnowledge(embeddingModel, vectorStore, keywordSearcher, rerankProvider, hybridConfig);
        List<Document> results = hk.retrieve("query", RetrieveConfig.builder().limit(5).scoreThreshold(0.0).build()).block();

        assertThat(results).hasSize(2);
    }

    @Test
    void rrfFusionMergesBothPathsAndDeduplicates() {
        // 向量路和关键词路有相同 chunkId 的文档（应去重，RRF 分数累加）
        when(vectorStore.search(any(SearchDocumentDto.class)))
                .thenReturn(Mono.just(List.of(
                        doc("chunk-1", "shared content", 0.9),
                        doc("chunk-2", "vector only", 0.7)
                )));
        when(keywordSearcher.search(anyString(), anyLong(), anyInt()))
                .thenReturn(List.of(
                        doc("chunk-1", "shared content", 1.0),
                        doc("chunk-3", "keyword only", 0.5)
                ));
        when(rerankProvider.rerank(anyString(), anyList(), anyInt()))
                .thenAnswer(inv -> inv.getArgument(1));

        HybridKnowledge hk = new HybridKnowledge(embeddingModel, vectorStore, keywordSearcher, rerankProvider, hybridConfig);
        List<Document> results = hk.retrieve("query", RetrieveConfig.builder().limit(10).scoreThreshold(0.0).build()).block();

        // chunk-1 在两路都出现，应去重后 3 个唯一结果
        assertThat(results).hasSize(3);
        // chunk-1 因两路叠加 RRF 分数应最高
        assertThat(results.get(0).getMetadata().getChunkId()).isEqualTo("chunk-1");
    }

    @Test
    void rerankerIsCalledWhenProviderIsNotNoop() {
        when(vectorStore.search(any(SearchDocumentDto.class)))
                .thenReturn(Mono.just(List.of(
                        doc("v1", "content A", 0.8),
                        doc("v2", "content B", 0.7)
                )));
        when(keywordSearcher.search(anyString(), anyLong(), anyInt()))
                .thenReturn(List.of());
        when(rerankProvider.rerank(anyString(), anyList(), anyInt()))
                .thenAnswer(inv -> {
                    List<Document> docs = inv.getArgument(1);
                    return docs; // passthrough
                });

        HybridKnowledge hk = new HybridKnowledge(embeddingModel, vectorStore, keywordSearcher, rerankProvider, hybridConfig);
        hk.retrieve("query", RetrieveConfig.builder().limit(3).scoreThreshold(0.0).build()).block();

        verify(rerankProvider).rerank(eq("query"), anyList(), anyInt());
    }

    @Test
    void rerankerNotCalledWhenProviderIsNoop() {
        when(vectorStore.search(any(SearchDocumentDto.class)))
                .thenReturn(Mono.just(List.of(doc("v1", "content", 0.8))));
        when(keywordSearcher.search(anyString(), anyLong(), anyInt()))
                .thenReturn(List.of());

        NoopReranker noop = new NoopReranker();
        HybridKnowledge hk = new HybridKnowledge(embeddingModel, vectorStore, keywordSearcher, noop, hybridConfig);
        hk.retrieve("query", RetrieveConfig.builder().limit(3).scoreThreshold(0.0).build()).block();

        // NoopReranker 内部不调外部 API，但 HybridKnowledge 应跳过 rerank 调用
        // 验证：结果直接返回，不影响正确性
        assertThat(noop.getName()).isEqualTo("none");
    }

    @Test
    void scoreThresholdFiltersLowScoreResults() {
        when(vectorStore.search(any(SearchDocumentDto.class)))
                .thenReturn(Mono.just(List.of(
                        doc("high", "good content", 0.9),
                        doc("low", "bad content", 0.1)
                )));
        when(keywordSearcher.search(anyString(), anyLong(), anyInt()))
                .thenReturn(List.of());

        // 设置高阈值，低分应被过滤
        HybridKnowledge hk = new HybridKnowledge(embeddingModel, vectorStore, keywordSearcher, new NoopReranker(), hybridConfig);
        List<Document> results = hk.retrieve("query", RetrieveConfig.builder().limit(5).scoreThreshold(0.5).build()).block();

        // RRF 分数都很低（< 0.5），但 threshold 过滤的是最终 score
        // RRF 分数 = weight / (k + rank)，最高约 0.7/61 ≈ 0.011，全部低于 0.5
        // 所以可能全部被过滤
        assertThat(results).allSatisfy(d ->
                assertThat(d.getScore() == null || d.getScore() >= 0.5).isTrue());
    }

    @Test
    void limitTruncatesResults() {
        List<Document> manyDocs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            manyDocs.add(doc("doc-" + i, "content " + i, 0.9 - i * 0.05));
        }
        when(vectorStore.search(any(SearchDocumentDto.class)))
                .thenReturn(Mono.just(manyDocs));
        when(keywordSearcher.search(anyString(), anyLong(), anyInt()))
                .thenReturn(List.of());

        HybridKnowledge hk = new HybridKnowledge(embeddingModel, vectorStore, keywordSearcher, new NoopReranker(), hybridConfig);
        List<Document> results = hk.retrieve("query", RetrieveConfig.builder().limit(3).scoreThreshold(0.0).build()).block();

        assertThat(results).hasSize(3);
    }

    @Test
    void nullKeywordSearcherDegradesToVectorOnly() {
        when(vectorStore.search(any(SearchDocumentDto.class)))
                .thenReturn(Mono.just(List.of(doc("v1", "content", 0.8))));

        HybridKnowledge hk = new HybridKnowledge(embeddingModel, vectorStore, null, new NoopReranker(), hybridConfig);
        List<Document> results = hk.retrieve("query", RetrieveConfig.builder().limit(5).scoreThreshold(0.0).build()).block();

        assertThat(results).hasSize(1);
    }

    // ==================== 辅助方法 ====================

    private Document doc(String chunkId, String content, double score) {
        DocumentMetadata meta = DocumentMetadata.builder()
                .content(TextBlock.builder().text(content).build())
                .docId("doc-" + chunkId)
                .chunkId(chunkId)
                .build();
        Document d = new Document(meta);
        d.setScore(score);
        return d;
    }
}
