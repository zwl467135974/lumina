package io.lumina.agent.rag;

import io.agentscope.core.rag.model.Document;

import java.util.List;

/**
 * 关键词检索接口（混合检索的关键词路）
 *
 * <p>实现可对接 MySQL FULLTEXT、Elasticsearch 等。
 * 在 {@link HybridKnowledge} 中与向量检索并行执行，通过 RRF 融合。
 *
 * @author Lumina Team
 * @since 3.3.0
 */
public interface KeywordSearcher {

    /**
     * 关键词检索
     *
     * @param query    搜索关键词原文
     * @param tenantId 租户 ID（隔离）
     * @param limit    返回条数上限
     * @return 匹配的文档列表（score 字段为相关度分数，已归一化到 0-1）
     */
    List<Document> search(String query, Long tenantId, int limit);
}
