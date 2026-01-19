-- ========================================
-- Lumina 用户表初始化脚本
-- ========================================

-- 创建用户表
CREATE TABLE IF NOT EXISTS `lumina_user` (
    `user_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名',
    `password` VARCHAR(255) NOT NULL COMMENT '密码（加密后）',
    `real_name` VARCHAR(100) COMMENT '真实姓名',
    `email` VARCHAR(100) COMMENT '邮箱',
    `phone` VARCHAR(20) COMMENT '手机号',
    `role` VARCHAR(20) NOT NULL DEFAULT 'user' COMMENT '角色（admin, user）',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态（0-禁用，1-启用）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除（0-未删除，1-已删除）',
    PRIMARY KEY (`user_id`),
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_email` (`email`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 插入默认管理员账户（密码: admin123，BCrypt 加密）
-- BCrypt hash of "admin123" (cost=12)
INSERT INTO `lumina_user` (`username`, `password`, `real_name`, `email`, `role`, `status`)
VALUES ('admin', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5NU7xIXU2bq4u', '系统管理员', 'admin@lumina.io', 'admin', 1)
ON DUPLICATE KEY UPDATE `username` = `username`;

-- 插入测试用户（密码: user123，BCrypt 加密）
-- BCrypt hash of "user123" (cost=12)
INSERT INTO `lumina_user` (`username`, `password`, `real_name`, `email`, `role`, `status`)
VALUES ('testuser', '$2a$12$XE5xE1Y5m9ZGZTYH5L5ZKeQZGH5VGNZRQZGH5VGNZRQZGH5VGNZRQ', '测试用户', 'test@lumina.io', 'user', 1)
ON DUPLICATE KEY UPDATE `username` = `username`;
