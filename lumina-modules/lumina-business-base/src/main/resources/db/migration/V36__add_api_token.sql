-- V36: 外部调用 API Token 表（OpenAI 兼容端点认证用）
-- 明文 Token 只在创建时返回一次，DB 仅存 SHA-256 哈希

CREATE TABLE IF NOT EXISTS `lumina_api_token` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Token ID',
    `token_hash` VARCHAR(64) NOT NULL COMMENT 'SHA-256(明文)，查询用',
    `user_id` BIGINT NOT NULL COMMENT '所属用户 ID',
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户 ID',
    `name` VARCHAR(64) NOT NULL COMMENT '用户自填名称',
    `scopes` VARCHAR(256) DEFAULT 'agent:execute' COMMENT '权限范围（逗号分隔）',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态（1-启用，0-禁用）',
    `last_used_at` DATETIME NULL COMMENT '最后使用时间',
    `expires_at` DATETIME NULL COMMENT '过期时间（NULL=永不过期）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除（0-未删除，1-已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_token_hash` (`token_hash`),
    KEY `idx_user` (`user_id`),
    KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='外部调用 API Token';
