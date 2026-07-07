-- V14: 评估运行记录增加状态字段（支持异步评估）

ALTER TABLE lumina_evaluation_run
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED' COMMENT '评估状态（RUNNING/COMPLETED/FAILED）' AFTER total_tokens;
