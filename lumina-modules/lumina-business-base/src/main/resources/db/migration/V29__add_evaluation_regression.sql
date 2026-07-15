-- V29: 评估回归增强（Prompt 版本绑定 + 基线标记 + 回归规则）

-- 1. evaluation_run 加列：Prompt 版本绑定 + 基线标记
ALTER TABLE `lumina_evaluation_run`
    ADD COLUMN `prompt_name` VARCHAR(100) NULL COMMENT '被测 Prompt 名称' AFTER `provider`,
    ADD COLUMN `prompt_version` INT NULL COMMENT '被测 Prompt 版本' AFTER `prompt_name`,
    ADD COLUMN `is_baseline` TINYINT NOT NULL DEFAULT 0 COMMENT '0=普通 1=基线 run' AFTER `prompt_version`;

-- 2. 回归规则表（每个数据集可配一条基线 + 告警阈值）
CREATE TABLE IF NOT EXISTS `lumina_evaluation_regression_rule` (
    `id`                BIGINT       AUTO_INCREMENT PRIMARY KEY,
    `name`              VARCHAR(100) NOT NULL                          COMMENT '规则名称',
    `dataset_id`        BIGINT       NOT NULL                          COMMENT '关联数据集 ID',
    `baseline_run_id`   BIGINT       NULL                              COMMENT '基线 run ID',
    `max_regressed`     INT          NOT NULL DEFAULT 0                COMMENT '允许的最大回归用例数',
    `alert_webhook`     VARCHAR(500) NULL                              COMMENT '告警 webhook URL（可选）',
    `enabled`           TINYINT      NOT NULL DEFAULT 1                COMMENT '0=禁用 1=启用',
    `tenant_id`         BIGINT       NOT NULL DEFAULT 0                COMMENT '租户 ID',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_dataset` (`dataset_id`),
    INDEX `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评估回归规则';
