-- V42: lumina_agent_task 增加触发来源回链（v3.5 Cron Trigger）

ALTER TABLE lumina_agent_task ADD COLUMN trigger_id BIGINT NULL COMMENT '触发来源（定时 trigger 的 id）';
ALTER TABLE lumina_agent_task ADD INDEX idx_trigger (trigger_id);
