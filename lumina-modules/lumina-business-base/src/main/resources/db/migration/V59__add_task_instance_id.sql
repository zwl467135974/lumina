-- V59: 任务实例隔离——多实例部署下的对账隔离
-- 背景（ADR-002）：启动对账此前把全库 RUNNING 任务标记 INTERRUPTED，双实例
-- 滚动发布时后启动的实例会误杀先启动实例正在执行的任务。提交时记录归属
-- 实例，对账只处理心跳已消失的死亡实例任务；存量 NULL 行按"遗留无主"
-- 处理（单实例部署等价旧行为，可配置关闭）。

ALTER TABLE `lumina_agent_task`
    ADD COLUMN `instance_id` VARCHAR(64) NULL COMMENT '提交实例标识（AgentInstanceRegistry 生成，对账隔离用）' AFTER `trigger_id`;

ALTER TABLE `lumina_agent_task`
    ADD INDEX `idx_status_instance` (`status`, `instance_id`);
