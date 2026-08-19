-- V51: 工具执行结果存档表（v3.11 工具结果外存化 spill）

CREATE TABLE IF NOT EXISTS lumina_tool_artifact (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    artifact_id     VARCHAR(64) NOT NULL COMMENT '存档 ID（模型侧引用）',
    conversation_id VARCHAR(64) NULL COMMENT '关联会话 UUID（可空）',
    tool_name       VARCHAR(128) NOT NULL COMMENT '来源工具名',
    content         LONGTEXT NOT NULL COMMENT '结果全文',
    content_chars   INT NOT NULL DEFAULT 0 COMMENT '全文长度（字符）',
    tenant_id       BIGINT NOT NULL DEFAULT 0 COMMENT '租户 ID',
    create_by       BIGINT NULL COMMENT '创建用户',
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted      TINYINT NOT NULL DEFAULT 0,
    UNIQUE INDEX uk_artifact_id (artifact_id),
    INDEX idx_conversation (conversation_id),
    INDEX idx_tenant_time (tenant_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工具执行结果存档（超大结果外存化）';
