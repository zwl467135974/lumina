-- 知识库文档元数据表
CREATE TABLE IF NOT EXISTS `lumina_knowledge_document` (
    `document_id` BIGINT NOT NULL AUTO_INCREMENT,
    `document_uuid` VARCHAR(64) NOT NULL,
    `tenant_id` BIGINT NOT NULL,
    `agent_id` BIGINT DEFAULT NULL,
    `title` VARCHAR(200),
    `format` VARCHAR(20) NOT NULL,
    `chunk_count` INT DEFAULT 0,
    `file_size` BIGINT,
    `status` TINYINT NOT NULL DEFAULT 1,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`document_id`),
    UNIQUE KEY `uk_document_uuid` (`document_uuid`),
    KEY `idx_tenant_agent` (`tenant_id`, `agent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库文档表';
