-- V12: 预算管理规则表

CREATE TABLE IF NOT EXISTS lumina_budget_rule (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    rule_name       VARCHAR(100) NOT NULL COMMENT '规则名称',
    scope_type      VARCHAR(20) NOT NULL COMMENT 'TENANT / AGENT / USER',
    scope_id        BIGINT COMMENT 'TENANT 范围时为 NULL，AGENT 范围时为 agentId，USER 范围时为 userId',
    period_type     VARCHAR(10) NOT NULL COMMENT 'DAILY / MONTHLY',
    limit_amount    DECIMAL(12, 4) NOT NULL COMMENT '预算上限（元）',
    alert_threshold INT NOT NULL DEFAULT 80 COMMENT '告警阈值（百分比，0-100）',
    status          TINYINT NOT NULL DEFAULT 1 COMMENT '0=禁用 1=启用',
    tenant_id       BIGINT NOT NULL,
    create_by       BIGINT,
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted      TINYINT NOT NULL DEFAULT 0,
    INDEX idx_tenant_status (tenant_id, status),
    INDEX idx_scope (scope_type, scope_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预算管理规则';
