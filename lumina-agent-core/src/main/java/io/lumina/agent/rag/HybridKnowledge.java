package io.lumina.agent.rag;

import io.agentscope.core.embedding.EmbeddingModel;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.rag.Knowledge;
import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.model.RetrieveConfig;
import io.agentscope.core.rag.store.dto.SearchDocumentDto;
import io.agentscope.core.rag.store.VDBStoreBase;
import io.lumina.agent.config.RagProperties;
import io.lumina.common.core.BaseContext;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 混合检索 Knowledge 实现
 *
 * <p>并行执行向量检索 + 关键词检索，通过 RRF（Reciprocal Rank Fusion）算法融合，
 * 可选调用 Reranker 重排序，最终截断到 limit 返回。
 *
 * <p>检索流程：
 * <ol>
 *   <li>向量检索：embed(query) → VDBStoreBase.search() → top-N</li>
 *   <li>关键词检索：KeywordSearcher.search(query, tenantId) → top-N</li>
 *   <li>RRF 融合两路结果（k=60，权重可配）</li>
 *   <li>若 Reranker 启用：取融合后 top-K 调 rerank API 重排序</li>
 *   <li>按 scoreThreshold 过滤 + 截断到 limit</li>
 * </ol>
 *
 * @author Lumina Team
 * @since 3.3.0
 */
@Slf4j
public class HybridKnowledge implements Knowledge {

    private static final int RRF_K = 60;
    private static final int CANDIDATE_POOL = 20;

    private final EmbeddingModel embeddingModel;
    private final VDBStoreBase vectorStore;
    private final KeywordSearcher keywordSearcher;
    private final RerankProvider rerankProvider;
    private final RagProperties.HybridConfig hybridConfig;

    public HybridKnowledge(EmbeddingModel embeddingModel,
                           VDBStoreBase vectorStore,
                           KeywordSearcher keywordSearcher,
                           RerankProvider rerankProvider,
                           RagProperties.HybridConfig hybridConfig) {
        this.embeddingModel = embeddingModel;
        this.vectorStore = vectorStore;
        this.keywordSearcher = keywordSearcher;
        this.rerankProvider = rerankProvider;
        this.hybridConfig = hybridConfig;
    }

    @Override
    public Mono<Void> addDocuments(List<Document> documents) {
        // 先对每个文档做 embedding，再写入向量存储
        // （与 SimpleKnowledge 行为一致，不能跳过 embed 步骤）
        return Mono.fromCallable(() -> {
            for (Document doc : documents) {
                if (doc.getMetadata() != null && doc.getMetadata().getContent() != null) {
                    double[] embedding = embeddingModel.embed(doc.getMetadata().getContent()).block();
                    doc.setEmbedding(embedding);
                }
            }
            return documents;
        }).flatMap(vectorStore::add);
    }

    @Override
    public Mono<List<Document>> retrieve(String query, RetrieveConfig config) {
        int limit = config.getLimit();
        int poolSize = Math.max(CANDIDATE_POOL, limit * 3);

        // 1. 向量检索
        double[] queryEmbedding = embeddingModel.embed(TextBlock.builder().text(query).build()).block();

        SearchDocumentDto vectorDto = SearchDocumentDto.builder()
                .vectorName(config.getVectorName())
                .queryEmbedding(queryEmbedding)
                .limit(poolSize)
                .scoreThreshold(config.getScoreThreshold())
                .build();
        List<Document> vectorResults = vectorStore.search(vectorDto).block();
        if (vectorResults == null) {
            vectorResults = Collections.emptyList();
        }
        log.debug("混合检索-向量路: query={}, results={}", query, vectorResults.size());

        // 2. 关键词检索
        Long tenantId = BaseContext.getTenantId();
        List<Document> keywordResults = Collections.emptyList();
        if (keywordSearcher != null && tenantId != null) {
            keywordResults = keywordSearcher.search(query, tenantId, poolSize);
        }
        log.debug("混合检索-关键词路: query={}, results={}", query, keywordResults.size());

        // 3. RRF 融合（按 chunkId 或 content 去重，因为向量路和关键词路的 Document.id 不同）
        double vectorWeight = hybridConfig != null ? hybridConfig.getVectorWeight() : 0.7;
        double keywordWeight = hybridConfig != null ? hybridConfig.getKeywordWeight() : 0.3;

        // 用 chunkId（优先）或 content 前 200 字符作为去重 key
        java.util.function.Function<Document, String> keyExtractor = d -> {
            if (d.getMetadata() != null && d.getMetadata().getChunkId() != null) {
                return d.getMetadata().getChunkId();
            }
            String text = d.getMetadata() != null ? d.getMetadata().getContentText() : "";
            return text != null ? text.substring(0, Math.min(200, text.length())) : String.valueOf(System.identityHashCode(d));
        };

        Map<String, Document> docMap = new LinkedHashMap<>();
        for (Document d : vectorResults) {
            docMap.put(keyExtractor.apply(d), d);
        }
        for (Document d : keywordResults) {
            docMap.putIfAbsent(keyExtractor.apply(d), d);
        }

        // 计算每篇文档的 RRF 分数
        Map<String, Double> rrfScores = new HashMap<>();
        for (int i = 0; i < vectorResults.size(); i++) {
            String key = keyExtractor.apply(vectorResults.get(i));
            rrfScores.merge(key, vectorWeight / (RRF_K + i + 1), Double::sum);
        }
        for (int i = 0; i < keywordResults.size(); i++) {
            String key = keyExtractor.apply(keywordResults.get(i));
            rrfScores.merge(key, keywordWeight / (RRF_K + i + 1), Double::sum);
        }

        // RRF 分数归一化到 0-1 区间（RRF 原始分数在 0.001-0.02 量级，
        // 不归一化会被外部 scoreThreshold=0.3 过滤掉）
        double maxRrf = rrfScores.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
        if (maxRrf > 0) {
            rrfScores.replaceAll((k, v) -> v / maxRrf);
        }

        // 按 RRF 分数排序
        List<String> sortedKeys = rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        List<Document> fused = new ArrayList<>();
        for (String key : sortedKeys) {
            Document doc = docMap.get(key);
            doc.setScore(rrfScores.get(key));
            fused.add(doc);
        }
        log.debug("混合检索-RRF 融合后: query={}, results={}", query, fused.size());

        // 4. Reranker 重排序
        List<Document> finalResults;
        if (rerankProvider != null && !(rerankProvider instanceof NoopReranker) && fused.size() > 1) {
            int rerankTopK = Math.min(Math.max(limit * 2, 10), fused.size());
            finalResults = rerankProvider.rerank(query, fused, rerankTopK);
            log.debug("混合检索-Rerank 后: provider={}, results={}", rerankProvider.getName(), finalResults.size());
        } else {
            finalResults = fused;
        }

        // 5. 过滤 + 截断
        double threshold = config.getScoreThreshold();
        List<Document> filtered = finalResults.stream()
                .filter(d -> d.getScore() == null || d.getScore() >= threshold)
                .limit(limit)
                .collect(Collectors.toList());

        log.info("混合检索完成: query={}, vector={}, keyword={}, final={}",
                query, vectorResults.size(), keywordResults.size(), filtered.size());
        return Mono.just(filtered);
    }
}
