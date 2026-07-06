-- V10: Agent 异步任务表

CREATE TABLE IF NOT EXISTS lumina_agent_task (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_uuid           VARCHAR(36) NOT NULL UNIQUE COMMENT '任务 UUID',
    agent_id            BIGINT NOT NULL COMMENT 'Agent ID',
    conversation_uuid   VARCHAR(36) COMMENT '会话 UUID',
    input_text          TEXT NOT NULL COMMENT '任务输入',
    file_ids            VARCHAR(1000) COMMENT '文件 UUID JSON 列表',
    status              VARCHAR(20) NOT NULL DEFAULT 'QUEUED' COMMENT 'QUEUED/RUNNING/COMPLETED/FAILED/CANCELLED',
    result              TEXT COMMENT '执行结果',
    error_message       TEXT COMMENT '错误信息',
    prompt_tokens       INT DEFAULT 0,
    completion_tokens   INT DEFAULT 0,
    total_tokens        INT DEFAULT 0,
    duration_ms         BIGINT,
    tenant_id           BIGINT NOT NULL DEFAULT 0,
    create_by           BIGINT,
    create_time         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted          TINYINT NOT NULL DEFAULT 0,
    INDEX idx_task_uuid (task_uuid),
    INDEX idx_agent_status (agent_id, status),
    INDEX idx_tenant_status (tenant_id, status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 异步任务';
