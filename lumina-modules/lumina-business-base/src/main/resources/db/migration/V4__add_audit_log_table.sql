-- ========================================
-- Lumina 审计日志表
-- 版本：1.1.0
-- 说明：记录用户/角色/权限/租户/Agent 等关键操作的审计日志，
--       由 @Audit 注解 + AuditAspect 切面自动采集。
-- ========================================

CREATE TABLE IF NOT EXISTS `lumina_audit_log` (
    `audit_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '审计ID',
    `tenant_id` BIGINT DEFAULT NULL COMMENT '租户ID',
    `user_id` BIGINT DEFAULT NULL COMMENT '操作人ID',
    `username` VARCHAR(50) DEFAULT NULL COMMENT '操作人用户名',
    `module` VARCHAR(50) NOT NULL COMMENT '业务模块',
    `action` VARCHAR(50) NOT NULL COMMENT '操作类型',
    `target_type` VARCHAR(50) DEFAULT NULL COMMENT '目标类型',
    `target_id` VARCHAR(64) DEFAULT NULL COMMENT '目标ID',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '描述',
    `request_method` VARCHAR(10) DEFAULT NULL COMMENT 'HTTP方法',
    `request_url` VARCHAR(255) DEFAULT NULL COMMENT '请求URL',
    `request_ip` VARCHAR(50) DEFAULT NULL COMMENT '请求IP',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态（0-失败，1-成功）',
    `error_msg` VARCHAR(500) DEFAULT NULL COMMENT '错误信息',
    `duration_ms` BIGINT DEFAULT NULL COMMENT '耗时（毫秒）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`audit_id`),
    KEY `idx_tenant_user` (`tenant_id`, `user_id`),
    KEY `idx_module_action` (`module`, `action`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审计日志表';
