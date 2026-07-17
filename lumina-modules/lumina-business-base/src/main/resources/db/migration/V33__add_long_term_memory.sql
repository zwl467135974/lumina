-- V33: 长期记忆表（Reflective Memory）
-- 存储从对话中提取的关键事实/偏好，跨会话保留

CREATE TABLE IF NOT EXISTS `lumina_long_term_memory` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '记忆ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID（跨会话共享）',
    `agent_id` BIGINT COMMENT '关联 Agent ID（null=全局记忆）',
    `conversation_id` VARCHAR(64) COMMENT '来源会话 UUID',
    `memory_type` VARCHAR(30) NOT NULL DEFAULT 'fact' COMMENT '类型（fact/preference/summary）',
    `content` VARCHAR(500) NOT NULL COMMENT '记忆内容',
    `importance` DECIMAL(3,2) NOT NULL DEFAULT 0.50 COMMENT '重要度（0-1）',
    `access_count` INT NOT NULL DEFAULT 0 COMMENT '被引用次数',
    `last_accessed` DATETIME COMMENT '最后引用时间',
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_ltm_user_agent` (`user_id`, `agent_id`),
    KEY `idx_ltm_importance` (`importance`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='长期记忆（Reflective Memory）';
