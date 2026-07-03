-- V8: 工作流编排引擎表结构

-- 工作流定义表
CREATE TABLE IF NOT EXISTS lumina_workflow_definition (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    name            VARCHAR(100) NOT NULL COMMENT '工作流名称（唯一标识）',
    description     VARCHAR(500) COMMENT '描述',
    definition_yaml TEXT NOT NULL COMMENT '完整 YAML 定义',
    version         INT NOT NULL DEFAULT 1 COMMENT '版本号',
    status          TINYINT NOT NULL DEFAULT 0 COMMENT '0=草稿 1=已发布',
    tenant_id       BIGINT NOT NULL DEFAULT 0 COMMENT '租户 ID',
    create_by       BIGINT COMMENT '创建人',
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted      TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_name_version (name, version),
    INDEX idx_tenant (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流定义';

-- 工作流实例表
CREATE TABLE IF NOT EXISTS lumina_workflow_instance (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    definition_id       BIGINT NOT NULL COMMENT '工作流定义 ID',
    definition_name     VARCHAR(100) NOT NULL COMMENT '工作流名称（冗余，便于查询）',
    definition_version  INT NOT NULL COMMENT '版本号（冗余）',
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/PAUSED/COMPLETED/FAILED/CANCELLED',
    input               TEXT COMMENT 'JSON 输入',
    output              TEXT COMMENT 'JSON 输出',
    error_message       TEXT COMMENT '错误信息',
    current_node_id     VARCHAR(50) COMMENT '当前执行节点',
    tenant_id           BIGINT NOT NULL DEFAULT 0,
    create_by           BIGINT COMMENT '发起人',
    create_time         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_definition (definition_id),
    INDEX idx_status (status, tenant_id),
    INDEX idx_tenant (tenant_id, create_time DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流执行实例';

-- 工作流执行日志表
CREATE TABLE IF NOT EXISTS lumina_workflow_execution_log (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    instance_id         BIGINT NOT NULL COMMENT '工作流实例 ID',
    node_id             VARCHAR(50) NOT NULL COMMENT '节点 ID',
    node_type           VARCHAR(30) NOT NULL COMMENT '节点类型',
    node_name           VARCHAR(100) COMMENT '节点名称',
    status              VARCHAR(20) NOT NULL COMMENT 'RUNNING/COMPLETED/FAILED/SKIPPED',
    input               TEXT COMMENT 'JSON 输入',
    output              TEXT COMMENT 'JSON 输出',
    duration_ms         INT COMMENT '执行耗时（毫秒）',
    error_message       TEXT COMMENT '错误信息',
    create_time         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_instance (instance_id, node_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流节点执行日志';
