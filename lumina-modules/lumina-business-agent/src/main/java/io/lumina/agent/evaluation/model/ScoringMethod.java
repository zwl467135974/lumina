package io.lumina.agent.evaluation.model;

/**
 * 评分方法枚举
 *
 * @author Lumina Team
 * @since 2.0.0
 */
public enum ScoringMethod {
    /** 精确匹配 */
    EXACT_MATCH,
    /** 关键词包含 */
    CONTAINS,
    /** 语义相似度（Embedding cosine） */
    SEMANTIC_SIMILARITY,
    /** LLM-as-Judge（1-5 分制） */
    LLM_JUDGE
}
