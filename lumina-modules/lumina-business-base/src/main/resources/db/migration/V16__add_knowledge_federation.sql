-- V16: 知识库联邦（E5）— 知识库实体 + Agent 挂载 + 文档归属

CREATE TABLE IF NOT EXISTS lumina_knowledge_base (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    name            VARCHAR(200) NOT NULL COMMENT '知识库名称',
    description     VARCHAR(500) COMMENT '描述',
    visibility      VARCHAR(20) NOT NULL DEFAULT 'PRIVATE' COMMENT '可见性：PRIVATE / TEAM / PUBLIC',
    tenant_id       BIGINT NOT NULL,
    create_by       BIGINT,
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted      TINYINT NOT NULL DEFAULT 0,
    INDEX idx_tenant (tenant_id),
    INDEX idx_visibility (visibility)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库';

CREATE TABLE IF NOT EXISTS lumina_agent_knowledge_base (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    agent_id        BIGINT NOT NULL COMMENT 'Agent ID',
    kb_id           BIGINT NOT NULL COMMENT '知识库 ID',
    tenant_id       BIGINT NOT NULL,
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_agent_kb (agent_id, kb_id),
    INDEX idx_agent (agent_id),
    INDEX idx_kb (kb_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent-知识库挂载关系';

ALTER TABLE lumina_knowledge_document
    ADD COLUMN kb_id BIGINT DEFAULT NULL COMMENT '所属知识库 ID' AFTER agent_id,
    ADD INDEX idx_kb (kb_id);
