-- V58: 模板与分享中心——Agent 模板表（角色包 = 模板 + 技能集合）
-- 统一分享格式：zip{ manifest.json + agent.json + skills/{name}/SKILL.md }
-- 导入全走安全体检；导出自动剥离 LLM 密钥

CREATE TABLE IF NOT EXISTS `lumina_agent_template` (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    name            VARCHAR(100) NOT NULL COMMENT '模板名（实例化时的 Agent 名基础）',
    agent_type      VARCHAR(50) NOT NULL DEFAULT 'REACT',
    description     VARCHAR(500) NULL,
    content         MEDIUMTEXT NOT NULL COMMENT '模板 JSON（agent.json：tools/subAgents/llm 形态，已剥离密钥）',
    skill_names     VARCHAR(2000) NULL COMMENT '随包技能名列表（逗号分隔，导入时已入库）',
    source          VARCHAR(16) NOT NULL DEFAULT 'IMPORT' COMMENT '来源 IMPORT=角色包导入 EXPORT=本租户导出',
    version         INT NOT NULL DEFAULT 1 COMMENT '同模板重复导入自增',
    tenant_id       BIGINT NOT NULL DEFAULT 0,
    create_by       BIGINT NULL,
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted      TINYINT NOT NULL DEFAULT 0,
    UNIQUE INDEX uk_tenant_name (tenant_id, name),
    INDEX idx_tenant_source (tenant_id, source)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 模板（分享中心）';

-- 权限与菜单
INSERT INTO `lumina_permission` (`parent_id`, `permission_code`, `permission_name`, `permission_type`, `path`, `icon`, `sort_order`)
VALUES (0, 'share', '分享中心', 1, '/share', 'Share', 80)
ON DUPLICATE KEY UPDATE `permission_name` = VALUES(`permission_name`);

INSERT INTO `lumina_permission` (`parent_id`, `permission_code`, `permission_name`, `permission_type`, `sort_order`)
SELECT p.`permission_id`, 'share:list', '模板浏览与导出', 2, 1 FROM `lumina_permission` p WHERE p.`permission_code` = 'share'
ON DUPLICATE KEY UPDATE `permission_name` = VALUES(`permission_name`);
INSERT INTO `lumina_permission` (`parent_id`, `permission_code`, `permission_name`, `permission_type`, `sort_order`)
SELECT p.`permission_id`, 'share:import', '导入与实例化', 2, 2 FROM `lumina_permission` p WHERE p.`permission_code` = 'share'
ON DUPLICATE KEY UPDATE `permission_name` = VALUES(`permission_name`);

-- SUPER_ADMIN (1) 全量；TENANT_ADMIN (3) 授予分享中心
INSERT IGNORE INTO `lumina_role_permission` (`role_id`, `permission_id`)
SELECT 1, `permission_id` FROM `lumina_permission` WHERE `permission_code` LIKE 'share%';
INSERT IGNORE INTO `lumina_role_permission` (`role_id`, `permission_id`)
SELECT 3, `permission_id` FROM `lumina_permission` WHERE `permission_code` LIKE 'share%';
