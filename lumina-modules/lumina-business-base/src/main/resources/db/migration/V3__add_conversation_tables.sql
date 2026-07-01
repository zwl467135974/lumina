-- ========================================
-- Lumina 会话与消息表
-- 版本：1.1.0
-- 说明：新增会话维度（conversation）与消息维度（message）持久化表，
--       支持多轮对话、历史回放与 token 用量统计。
-- ========================================

-- 8. 会话表
CREATE TABLE IF NOT EXISTS `lumina_conversation` (
    `conversation_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '会话ID',
    `conversation_uuid` VARCHAR(64) NOT NULL COMMENT '会话UUID（对外标识）',
    `agent_id` BIGINT NOT NULL COMMENT '关联 Agent ID',
    `tenant_id` BIGINT NOT NULL COMMENT '租户ID',
    `user_id` BIGINT DEFAULT NULL COMMENT '创建用户ID',
    `title` VARCHAR(200) DEFAULT NULL COMMENT '会话标题（默认取首条消息摘要）',
    `message_count` INT NOT NULL DEFAULT 0 COMMENT '消息条数',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态（0-已关闭，1-活跃）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`conversation_id`),
    UNIQUE KEY `uk_conversation_uuid` (`conversation_uuid`),
    KEY `idx_agent_id` (`agent_id`),
    KEY `idx_tenant_user` (`tenant_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话表';

-- 9. 消息表
CREATE TABLE IF NOT EXISTS `lumina_message` (
    `message_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '消息ID',
    `conversation_id` BIGINT NOT NULL COMMENT '会话ID',
    `role` VARCHAR(20) NOT NULL COMMENT '角色（user/assistant/system）',
    `content` MEDIUMTEXT COMMENT '消息内容',
    `token_count` INT NOT NULL DEFAULT 0 COMMENT 'Token 消耗量',
    `duration_ms` BIGINT DEFAULT NULL COMMENT '生成耗时（毫秒，仅 assistant）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`message_id`),
    KEY `idx_conversation_id` (`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话消息表';
