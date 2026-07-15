package io.lumina.agent.rag;

import io.agentscope.core.rag.model.Document;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 空重排序器（不调模型，仅按原顺序截断）
 *
 * @author Lumina Team
 * @since 3.3.0
 */
@Slf4j
public class NoopReranker implements RerankProvider {

    @Override
    public List<Document> rerank(String query, List<Document> docs, int topK) {
        // 不调外部模型，直接截断到 topK
        return docs.size() > topK ? docs.subList(0, topK) : docs;
    }

    @Override
    public String getName() {
        return "none";
    }
}
