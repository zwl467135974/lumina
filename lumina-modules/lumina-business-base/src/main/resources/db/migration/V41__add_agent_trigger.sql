-- V41: Agent 定时触发器表 + 菜单种子（v3.5 Cron Trigger）

CREATE TABLE IF NOT EXISTS lumina_agent_trigger (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    name                VARCHAR(64) NOT NULL COMMENT '触发器名称',
    agent_id            BIGINT NOT NULL COMMENT '目标 Agent ID',
    workflow_id         BIGINT NULL COMMENT '目标工作流 ID（预留，首版仅支持 agent_id）',
    cron_expr           VARCHAR(64) NOT NULL COMMENT 'Spring 6 字段 cron：秒 分 时 日 月 周（如 0 0 9 * * * = 每天 9 点）',
    input_text          TEXT NOT NULL COMMENT '触发时提交给 Agent 的任务输入',
    misfire_policy      VARCHAR(16) NOT NULL DEFAULT 'FIRE_ONCE' COMMENT '错过策略：FIRE_ONCE=补触发一次 / SKIP=跳过',
    enabled             TINYINT NOT NULL DEFAULT 1 COMMENT '0=禁用 1=启用',
    next_fire_at        DATETIME NULL COMMENT '下次触发时间',
    last_fire_at        DATETIME NULL COMMENT '最近触发时间',
    last_status         VARCHAR(16) NULL COMMENT '最近触发结果 SUCCESS/FAILED',
    last_error          VARCHAR(512) NULL COMMENT '最近失败原因',
    fail_count          INT NOT NULL DEFAULT 0 COMMENT '连续失败次数，达 5 自动禁用',
    tenant_id           BIGINT NOT NULL DEFAULT 0 COMMENT '租户 ID',
    create_by           BIGINT NULL,
    create_time         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted          TINYINT NOT NULL DEFAULT 0,
    INDEX idx_enabled_next (enabled, next_fire_at),
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 定时触发器';

-- 菜单：定时触发器（挂在 Agent 管理下，参考 V17 agent:tasks 模式）
INSERT INTO `lumina_permission` (`parent_id`, `permission_code`, `permission_name`, `permission_type`, `path`, `sort_order`)
SELECT p.`permission_id`, 'agent:trigger', '定时触发器', 1, 'triggers', 7 FROM `lumina_permission` p WHERE p.`permission_code` = 'agent'
ON DUPLICATE KEY UPDATE `permission_name` = VALUES(`permission_name`);

-- 给 SUPER_ADMIN (role_id=1) 分配权限
INSERT IGNORE INTO `lumina_role_permission` (`role_id`, `permission_id`)
SELECT 1, `permission_id` FROM `lumina_permission`
WHERE `permission_code` = 'agent:trigger';
