-- ========================================
-- Lumina Agent 推理链追踪表
-- 版本：3.7.0
-- 说明：记录 Agent 每次执行的推理链步骤（推理/工具调用/RAG检索/记忆注入），
--       用于可观测性和调试。由 LuminaTraceTracer 异步写入。
-- ========================================

CREATE TABLE IF NOT EXISTS `lumina_agent_trace` (
    `trace_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Trace ID',
    `trace_uuid` VARCHAR(36) NOT NULL COMMENT 'Trace UUID（前端可分享）',
    `task_uuid` VARCHAR(36) DEFAULT NULL COMMENT '关联的 Agent 任务 UUID（异步执行时）',
    `conversation_uuid` VARCHAR(36) DEFAULT NULL COMMENT '关联的会话 UUID',
    `agent_id` BIGINT DEFAULT NULL COMMENT 'Agent ID（临时 Agent 可能为 NULL）',
    `agent_name` VARCHAR(100) DEFAULT NULL COMMENT 'Agent 名称（冗余）',
    `agent_type` VARCHAR(50) DEFAULT NULL COMMENT 'Agent 类型（ReAct/PlanAndExecute）',

    -- 执行概况
    `input_text` TEXT COMMENT '用户输入任务',
    `output_text` TEXT COMMENT '最终输出',
    `status` VARCHAR(20) NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/SUCCESS/FAILED',

    -- Token 与成本（任务级汇总）
    `prompt_tokens` INT DEFAULT 0 COMMENT '输入 Token 总计',
    `completion_tokens` INT DEFAULT 0 COMMENT '输出 Token 总计',
    `total_tokens` INT DEFAULT 0 COMMENT 'Token 总计',

    -- 时序
    `duration_ms` BIGINT DEFAULT NULL COMMENT '总耗时（毫秒）',
    `started_at` DATETIME NOT NULL COMMENT '开始时间',
    `finished_at` DATETIME DEFAULT NULL COMMENT '结束时间',

    -- 步骤详情（JSON 数组）
    `steps` JSON COMMENT '推理链步骤详情（JSON 数组）',

    -- 审计
    `tenant_id` BIGINT NOT NULL COMMENT '租户 ID',
    `create_by` BIGINT DEFAULT NULL COMMENT '操作人',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    PRIMARY KEY (`trace_id`),
    UNIQUE KEY `uk_trace_uuid` (`trace_uuid`),
    KEY `idx_agent` (`agent_id`, `create_time`),
    KEY `idx_task` (`task_uuid`),
    KEY `idx_tenant_time` (`tenant_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 推理链追踪';
