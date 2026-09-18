-- V57: 大输入分治（fan-out 批次任务）——任务表加父子关系列
-- 借鉴 open-code-review 分治设计：工程代码确定性拆分（bundle）→ 隔离上下文
-- 子任务并发执行（复用完整执行管线的限流/预算/审计）→ 全部完成后合并

ALTER TABLE `lumina_agent_task`
    ADD COLUMN `parent_uuid` VARCHAR(64) NULL COMMENT '所属批次父任务（分治模式子任务专用）' AFTER `conversation_uuid`,
    ADD COLUMN `bundle_index` INT NULL COMMENT '分片序号（从 0 起）' AFTER `parent_uuid`,
    ADD COLUMN `bundle_count` INT NULL COMMENT '批次总分片数' AFTER `bundle_index`;

ALTER TABLE `lumina_agent_task`
    ADD INDEX `idx_parent_uuid` (`parent_uuid`);
