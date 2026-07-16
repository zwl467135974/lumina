-- V32: A/B Testing 框架 — 实验/变体/曝光记录

-- 实验
CREATE TABLE IF NOT EXISTS `lumina_ab_experiment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '实验ID',
    `name` VARCHAR(100) NOT NULL COMMENT '实验名称',
    `description` VARCHAR(500) COMMENT '实验描述',
    `agent_id` BIGINT NOT NULL COMMENT '关联 Agent ID',
    `traffic_percent` INT NOT NULL DEFAULT 100 COMMENT '总流量百分比（0-100）',
    `status` VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '状态（DRAFT/RUNNING/PAUSED/COMPLETED）',
    `start_time` DATETIME COMMENT '开始时间',
    `end_time` DATETIME COMMENT '结束时间',
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    `create_by` BIGINT COMMENT '创建人',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_ab_experiment_agent` (`agent_id`),
    KEY `idx_ab_experiment_tenant` (`tenant_id`),
    KEY `idx_ab_experiment_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='A/B 测试实验';

-- 变体
CREATE TABLE IF NOT EXISTS `lumina_ab_variant` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '变体ID',
    `experiment_id` BIGINT NOT NULL COMMENT '实验ID',
    `name` VARCHAR(50) NOT NULL COMMENT '变体名称（A/B/C）',
    `weight` INT NOT NULL DEFAULT 50 COMMENT '权重（百分比）',
    `llm_config` TEXT COMMENT '变体 LLM 配置 JSON',
    `prompt_name` VARCHAR(100) COMMENT '变体使用的 Prompt 名称',
    `description` VARCHAR(500) COMMENT '变体描述',
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_ab_variant_experiment` (`experiment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='A/B 测试变体';

-- 曝光记录（采样记录执行结果）
CREATE TABLE IF NOT EXISTS `lumina_ab_exposure` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `experiment_id` BIGINT NOT NULL COMMENT '实验ID',
    `variant_id` BIGINT NOT NULL COMMENT '变体ID',
    `variant_name` VARCHAR(50) COMMENT '变体名称',
    `conversation_id` VARCHAR(64) COMMENT '会话ID',
    `user_id` BIGINT COMMENT '用户ID',
    `success` TINYINT COMMENT '执行结果（0失败 1成功）',
    `latency_ms` BIGINT COMMENT '执行耗时毫秒',
    `tokens` INT COMMENT 'Token 使用量',
    `error_msg` VARCHAR(500) COMMENT '错误信息',
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_ab_exposure_experiment` (`experiment_id`),
    KEY `idx_ab_exposure_variant` (`variant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='A/B 测试曝光记录';
