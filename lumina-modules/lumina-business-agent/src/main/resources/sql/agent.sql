-- 创建 Agent 表
CREATE TABLE IF NOT EXISTS `lumina_agent` (
    `agent_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Agent ID',
    `agent_name` VARCHAR(100) NOT NULL COMMENT 'Agent 名称',
    `agent_type` VARCHAR(50) NOT NULL COMMENT 'Agent 类型',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '描述',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态（0-禁用，1-启用）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除（0-未删除，1-已删除）',
    PRIMARY KEY (`agent_id`),
    KEY `idx_agent_name` (`agent_name`),
    KEY `idx_agent_type` (`agent_type`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Agent 表';
