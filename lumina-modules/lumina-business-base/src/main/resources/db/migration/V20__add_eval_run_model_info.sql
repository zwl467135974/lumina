-- V20: 评估运行记录增加模型信息

ALTER TABLE lumina_evaluation_run
    ADD COLUMN model_name VARCHAR(100) DEFAULT NULL COMMENT '使用的模型名称' AFTER agent_type,
    ADD COLUMN provider VARCHAR(50) DEFAULT NULL COMMENT '模型提供商' AFTER model_name;
