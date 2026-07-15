package io.lumina.agent.rag;

import io.agentscope.core.message.TextBlock;
import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.model.DocumentMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RerankProvider 三模式单元测试
 *
 * @author Lumina Team
 * @since 3.3.0
 */
class RerankProviderTest {

    @Test
    void noopRerankerReturnsTruncatedList() {
        NoopReranker reranker = new NoopReranker();
        List<Document> docs = List.of(doc("a"), doc("b"), doc("c"));

        List<Document> result = reranker.rerank("query", docs, 2);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getMetadata().getChunkId()).isEqualTo("a");
        assertThat(reranker.getName()).isEqualTo("none");
    }

    @Test
    void noopRerankerHandlesEmptyList() {
        NoopReranker reranker = new NoopReranker();
        List<Document> result = reranker.rerank("query", List.of(), 5);
        assertThat(result).isEmpty();
    }

    @Test
    void noopRerankerHandlesNullInput() {
        NoopReranker reranker = new NoopReranker();
        List<Document> result = reranker.rerank("query", null, 5);
        assertThat(result).isNull();
    }

    @Test
    void noopRerankerReturnsAllWhenFewerThanTopK() {
        NoopReranker reranker = new NoopReranker();
        List<Document> docs = List.of(doc("a"));
        List<Document> result = reranker.rerank("query", docs, 5);
        assertThat(result).hasSize(1);
    }

    @Test
    void siliconFlowRerankerDegradesOnInvalidApiKey() {
        // 不传 apiKey 或无效 baseUrl → 应降级返回原顺序截断
        SiliconFlowReranker reranker = new SiliconFlowReranker("invalid-key", "http://localhost:1", null);
        List<Document> docs = List.of(doc("a"), doc("b"), doc("c"));

        List<Document> result = reranker.rerank("query", docs, 2);

        // 降级不崩溃，返回 topK 条
        assertThat(result).hasSize(2);
        assertThat(reranker.getName()).isEqualTo("siliconflow");
    }

    @Test
    void localRerankerDegradesOnConnectionFailure() {
        LocalReranker reranker = new LocalReranker("http://localhost:1", null);
        List<Document> docs = List.of(doc("a"), doc("b"));

        List<Document> result = reranker.rerank("query", docs, 2);

        assertThat(result).hasSize(2);
        assertThat(reranker.getName()).isEqualTo("local");
    }

    @Test
    void siliconFlowRerankerHandlesEmptyDocs() {
        SiliconFlowReranker reranker = new SiliconFlowReranker("key", null, null);
        List<Document> result = reranker.rerank("query", List.of(), 5);
        assertThat(result).isEmpty();
    }

    @Test
    void siliconFlowDefaultModelApplied() {
        SiliconFlowReranker reranker = new SiliconFlowReranker("key", null, null);
        // 默认 model 和 baseUrl 应被填充
        assertThat(reranker.getName()).isEqualTo("siliconflow");
    }

    // ==================== 辅助方法 ====================

    private Document doc(String chunkId) {
        DocumentMetadata meta = DocumentMetadata.builder()
                .content(TextBlock.builder().text("content for " + chunkId).build())
                .docId("doc-" + chunkId)
                .chunkId(chunkId)
                .build();
        return new Document(meta);
    }
}
