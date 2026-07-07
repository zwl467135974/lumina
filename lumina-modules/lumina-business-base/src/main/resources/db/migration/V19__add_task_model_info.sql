-- V19: Agent 任务增加模型信息（成本计算精确化）

ALTER TABLE lumina_agent_task
    ADD COLUMN model_name VARCHAR(100) DEFAULT NULL COMMENT '实际使用的模型名称' AFTER total_tokens,
    ADD COLUMN provider VARCHAR(50) DEFAULT NULL COMMENT '模型提供商' AFTER model_name;
