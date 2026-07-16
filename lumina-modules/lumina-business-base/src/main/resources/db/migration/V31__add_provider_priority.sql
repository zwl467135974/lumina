-- V31: LLM Provider 表添加 priority 字段（用于 failover 链排序）
-- priority 越小优先级越高（1 = 最高优先级），默认 100

ALTER TABLE `lumina_llm_provider`
    ADD COLUMN `priority` INT NOT NULL DEFAULT 100 COMMENT '优先级（越小越高，默认100）' AFTER `status`;

CREATE INDEX `idx_llm_provider_priority` ON `lumina_llm_provider` (`priority`);
