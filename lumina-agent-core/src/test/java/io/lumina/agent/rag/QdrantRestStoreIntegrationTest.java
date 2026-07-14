package io.lumina.agent.rag;

import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.model.DocumentMetadata;
import io.agentscope.core.rag.store.dto.SearchDocumentDto;
import io.agentscope.core.message.TextBlock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QdrantRestStore 真实 Qdrant 集成测试
 *
 * <p>验证向量存储的完整 CRUD 链路：collection 自动创建 → 写入 → 检索 → 删除。
 * 使用预制向量避免 Embedding API 依赖，聚焦验证 Qdrant REST 交互正确性。
 *
 * <p>每个测试方法使用独立 collection，避免向量数据跨测试污染。
 *
 * <p>仅在环境变量 {@code RAG_IT_ENABLED=true} 且 Qdrant 可达时运行。
 * 启动方式：{@code RAG_IT_ENABLED=true mvn test -Dtest=QdrantRestStoreIntegrationTest}
 *
 * @author Lumina Team
 * @since 3.2.0
 */
@EnabledIfEnvironmentVariable(named = "RAG_IT_ENABLED", matches = "true")
class QdrantRestStoreIntegrationTest {

    private static final String QDRANT_HOST = "localhost:6333";
    private static final int DIMENSIONS = 4;
    private static final HttpClient httpClient = HttpClient.newHttpClient();

    private QdrantRestStore store;
    private String collectionName;

    @BeforeEach
    void setUp() {
        // 每个测试用独立 collection，避免向量污染
        collectionName = "test_it_" + System.nanoTime();
        store = new QdrantRestStore(QDRANT_HOST, collectionName, DIMENSIONS);
    }

    @AfterEach
    void tearDown() throws Exception {
        // 每个测试后清理 collection
        deleteCollection(collectionName);
    }

    @Test
    void addAndSearchByVector() {
        // given: 写入两个文档，向量方向不同（用唯一 docId 避免跨测试干扰）
        Document doc1 = createDocument("it-search-1", "chunk-1", "文档一内容",
                new double[]{1.0, 0.0, 0.0, 0.0});
        Document doc2 = createDocument("it-search-2", "chunk-2", "文档二内容",
                new double[]{0.0, 1.0, 0.0, 0.0});

        store.add(List.of(doc1, doc2)).block();

        // when: 用接近 doc1 的向量搜索
        SearchDocumentDto query = SearchDocumentDto.builder()
                .queryEmbedding(new double[]{0.9, 0.1, 0.0, 0.0})
                .limit(2)
                .build();
        List<Document> results = store.search(query).block();

        // then: 返回结果，doc1 排在前面（相似度更高）
        assertThat(results).isNotNull();
        assertThat(results).hasSizeGreaterThanOrEqualTo(2);
        // 最匹配的应该是 it-search-1
        Document top = results.get(0);
        assertThat(top.getMetadata().getDocId()).isEqualTo("it-search-1");
    }

    @Test
    void searchReturnsEmptyForUnknownVector() {
        // given: 写入一个文档
        Document doc = createDocument("it-solo", "chunk-solo", "唯一文档",
                new double[]{1.0, 0.0, 0.0, 0.0});
        store.add(List.of(doc)).block();

        // when: 用正交方向搜索 + 极高阈值
        SearchDocumentDto query = SearchDocumentDto.builder()
                .queryEmbedding(new double[]{0.0, 0.0, 0.0, 1.0})
                .limit(1)
                .scoreThreshold(0.99)
                .build();
        List<Document> results = store.search(query).block();

        // then: 该文档不应出现在结果中（cosine 相似度≈0，低于 0.99 阈值）
        assertThat(results).isNotNull();
        assertThat(results).noneMatch(d -> "it-solo".equals(d.getMetadata().getDocId()));
    }

    @Test
    void deleteRemovesDocument() {
        // given: 写入文档
        Document doc = createDocument("it-del", "chunk-del", "待删除文档",
                new double[]{0.0, 0.0, 1.0, 0.0});
        store.add(List.of(doc)).block();

        // 验证写入成功
        SearchDocumentDto query = SearchDocumentDto.builder()
                .queryEmbedding(new double[]{0.0, 0.0, 1.0, 0.0})
                .limit(5)
                .build();
        List<Document> before = store.search(query).block();
        assertThat(before).anyMatch(d -> "it-del".equals(d.getMetadata().getDocId()));

        // when: 用 doc.getId() 删除（QdrantRestStore.delete 期望 point id = doc.getId()）
        Boolean deleted = store.delete(doc.getId()).block();

        // then
        assertThat(deleted).isTrue();

        // then: 该文档搜不到了
        List<Document> after = store.search(query).block();
        assertThat(after).noneMatch(d -> "it-del".equals(d.getMetadata().getDocId()));
    }

    @Test
    void addEmptyListDoesNothing() {
        // when: 写入空列表
        store.add(List.of()).block();

        // then: 不崩溃（无异常即通过）
    }

    @Test
    void deleteEmptyIdReturnsFalse() {
        // when
        Boolean result = store.delete("").block();

        // then
        assertThat(result).isFalse();
    }

    @Test
    void payloadExtraFieldsStoredAndRetrieved() {
        // given: 带额外 payload 的文档
        Document doc = createDocumentWithPayload("it-payload", "chunk-p",
                "带元数据的文档", new double[]{0.5, 0.5, 0.0, 0.0},
                java.util.Map.of("source", "test", "category", "unit-test"));

        store.add(List.of(doc)).block();

        // when: 搜索
        SearchDocumentDto query = SearchDocumentDto.builder()
                .queryEmbedding(new double[]{0.5, 0.5, 0.0, 0.0})
                .limit(1)
                .build();
        List<Document> results = store.search(query).block();

        // then: payload 被保留
        assertThat(results).isNotEmpty();
        Document found = results.get(0);
        assertThat(found.getMetadata().getDocId()).isEqualTo("it-payload");
        assertThat(found.getMetadata().getPayload()).containsEntry("source", "test");
        assertThat(found.getMetadata().getPayload()).containsEntry("category", "unit-test");
    }

    // ==================== 辅助方法 ====================

    private Document createDocument(String docId, String chunkId, String content, double[] vector) {
        DocumentMetadata metadata = new DocumentMetadata(
                TextBlock.builder().text(content).build(), docId, chunkId);
        Document doc = new Document(metadata);
        doc.setEmbedding(vector);
        return doc;
    }

    private Document createDocumentWithPayload(String docId, String chunkId, String content,
                                                double[] vector, java.util.Map<String, Object> payload) {
        DocumentMetadata metadata = new DocumentMetadata(
                TextBlock.builder().text(content).build(), docId, chunkId, payload);
        Document doc = new Document(metadata);
        doc.setEmbedding(vector);
        return doc;
    }

    /**
     * 删除 Qdrant collection（测试清理）
     */
    private static void deleteCollection(String collectionName) throws Exception {
        httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://" + QDRANT_HOST + "/collections/" + collectionName))
                        .DELETE()
                        .timeout(java.time.Duration.ofSeconds(10))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
