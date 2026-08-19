-- V52: Skill 技能系统（v3.11 渐进披露——目录进上下文，全文按需加载）

CREATE TABLE IF NOT EXISTS lumina_skill (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    name            VARCHAR(64) NOT NULL COMMENT '技能名（kebab-case，租户内唯一）',
    description     VARCHAR(500) NOT NULL COMMENT '一句话描述（进目录，供模型判断是否加载）',
    when_to_use     VARCHAR(500) NULL COMMENT '适用场景说明（进目录，可选）',
    content         MEDIUMTEXT NOT NULL COMMENT '技能全文（loadSkill 按需加载）',
    enabled         TINYINT NOT NULL DEFAULT 1 COMMENT '0=禁用（不进目录不可加载） 1=启用',
    tenant_id       BIGINT NOT NULL DEFAULT 0 COMMENT '租户 ID',
    create_by       BIGINT NULL,
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted      TINYINT NOT NULL DEFAULT 0,
    UNIQUE INDEX uk_tenant_name (tenant_id, name),
    INDEX idx_tenant_enabled (tenant_id, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 技能库（渐进披露）';

-- 菜单与按钮权限（参考 V17 prompt 模式）
INSERT INTO `lumina_permission` (`parent_id`, `permission_code`, `permission_name`, `permission_type`, `path`, `icon`, `sort_order`)
VALUES (0, 'skill', '技能管理', 1, '/skill', 'MagicStick', 70)
ON DUPLICATE KEY UPDATE `permission_name` = VALUES(`permission_name`);

INSERT INTO `lumina_permission` (`parent_id`, `permission_code`, `permission_name`, `permission_type`, `path`, `sort_order`)
SELECT p.`permission_id`, 'skill:list', '技能列表', 1, 'list', 1 FROM `lumina_permission` p WHERE p.`permission_code` = 'skill'
ON DUPLICATE KEY UPDATE `permission_name` = VALUES(`permission_name`);
INSERT INTO `lumina_permission` (`parent_id`, `permission_code`, `permission_name`, `permission_type`, `sort_order`)
SELECT p.`permission_id`, 'skill:create', '创建技能', 2, 2 FROM `lumina_permission` p WHERE p.`permission_code` = 'skill'
ON DUPLICATE KEY UPDATE `permission_name` = VALUES(`permission_name`);
INSERT INTO `lumina_permission` (`parent_id`, `permission_code`, `permission_name`, `permission_type`, `sort_order`)
SELECT p.`permission_id`, 'skill:update', '更新技能', 2, 3 FROM `lumina_permission` p WHERE p.`permission_code` = 'skill'
ON DUPLICATE KEY UPDATE `permission_name` = VALUES(`permission_name`);
INSERT INTO `lumina_permission` (`parent_id`, `permission_code`, `permission_name`, `permission_type`, `sort_order`)
SELECT p.`permission_id`, 'skill:delete', '删除技能', 2, 4 FROM `lumina_permission` p WHERE p.`permission_code` = 'skill'
ON DUPLICATE KEY UPDATE `permission_name` = VALUES(`permission_name`);

-- SUPER_ADMIN (role_id=1) 全量授予
INSERT IGNORE INTO `lumina_role_permission` (`role_id`, `permission_id`)
SELECT 1, `permission_id` FROM `lumina_permission` WHERE `permission_code` LIKE 'skill%';
-- TENANT_ADMIN (role_id=3) 授予技能管理
INSERT IGNORE INTO `lumina_role_permission` (`role_id`, `permission_id`)
SELECT 3, `permission_id` FROM `lumina_permission` WHERE `permission_code` LIKE 'skill%';
