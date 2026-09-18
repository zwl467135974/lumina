-- V56: OAuth2 第三方登录（授权码流程：GitHub 预设 + 通用 OIDC）
-- 首次登录自动建号并绑定三方身份；一个用户可绑定多个三方身份

CREATE TABLE IF NOT EXISTS `lumina_oauth2_identity` (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT NOT NULL COMMENT '绑定的 Lumina 用户',
    provider        VARCHAR(32) NOT NULL COMMENT '提供商标识（github / oidc / 自定义）',
    open_id         VARCHAR(128) NOT NULL COMMENT '提供方用户唯一标识（github id / oidc sub）',
    username        VARCHAR(128) NULL COMMENT '提供方用户名（展示/日志用）',
    avatar          VARCHAR(500) NULL,
    email           VARCHAR(128) NULL,
    raw_userinfo    TEXT NULL COMMENT '原始 userinfo JSON（排查用，截断存储）',
    tenant_id       BIGINT NOT NULL DEFAULT 0,
    create_by       BIGINT NULL,
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted      TINYINT NOT NULL DEFAULT 0,
    UNIQUE INDEX uk_provider_openid (provider, open_id),
    INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAuth2 三方身份绑定';

-- 权限：系统配置页展示（绑定关系管理后续按需）
INSERT INTO `lumina_permission` (`parent_id`, `permission_code`, `permission_name`, `permission_type`, `sort_order`)
SELECT p.`permission_id`, 'system:user:oauth2', '三方登录绑定', 2, 8 FROM `lumina_permission` p WHERE p.`permission_code` = 'system:user'
ON DUPLICATE KEY UPDATE `permission_name` = VALUES(`permission_name`);

INSERT IGNORE INTO `lumina_role_permission` (`role_id`, `permission_id`)
SELECT 1, `permission_id` FROM `lumina_permission` WHERE `permission_code` = 'system:user:oauth2';
INSERT IGNORE INTO `lumina_role_permission` (`role_id`, `permission_id`)
SELECT 3, `permission_id` FROM `lumina_permission` WHERE `permission_code` = 'system:user:oauth2';
