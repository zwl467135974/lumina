package io.lumina.agent.rag;

import io.agentscope.core.rag.model.Document;

import java.util.List;

/**
 * 重排序接口（Reranker）
 *
 * <p>对检索结果重新打分排序，提升 Top-K 精度。
 * 三种实现：SiliconFlow（免费 API）、Local（本地模型）、None（不重排）。
 *
 * @author Lumina Team
 * @since 3.3.0
 */
public interface RerankProvider {

    /**
     * 对文档列表重排序
     *
     * @param query  查询文本
     * @param docs   候选文档列表
     * @param topK   返回条数上限
     * @return 重排序后的文档列表（score 已更新）
     */
    List<Document> rerank(String query, List<Document> docs, int topK);

    /**
     * 提供商名称
     */
    String getName();
}
