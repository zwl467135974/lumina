-- V28: 知识库 chunk 原文表（混合检索的关键词路数据源）
-- chunk 原文目前只存在 Qdrant payload，MySQL 侧无副本。
-- 此表用于 BM25/全文关键词检索，入库时双写（Qdrant + MySQL）。

CREATE TABLE IF NOT EXISTS `lumina_knowledge_chunk` (
    `id`              BIGINT       AUTO_INCREMENT PRIMARY KEY,
    `chunk_id`        VARCHAR(128) NOT NULL                          COMMENT 'chunk 唯一 ID（与 Qdrant point ID 一致）',
    `doc_uuid`        VARCHAR(128) NOT NULL                          COMMENT '所属文档 UUID',
    `kb_id`           BIGINT       NULL                              COMMENT '所属知识库 ID',
    `tenant_id`       BIGINT       NOT NULL DEFAULT 0                COMMENT '租户 ID',
    `content`         TEXT         NOT NULL                          COMMENT 'chunk 原文',
    `chunk_index`     INT          NOT NULL DEFAULT 0                COMMENT 'chunk 序号',
    `vector_doc_id`   VARCHAR(128) NULL                              COMMENT '向量库文档 ID',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY `uk_chunk_id` (`chunk_id`),
    INDEX `idx_tenant_kb` (`tenant_id`, `kb_id`),
    INDEX `idx_doc_uuid` (`doc_uuid`),
    FULLTEXT INDEX `idx_content_ft` (`content`) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库 chunk 原文（混合检索关键词路）';
