-- V18: 字段名对齐（user/tenant）+ Agent 配置列（v3.0 稳定化）

-- ========================================
-- 1. 用户表：real_name → nickname
-- ========================================
ALTER TABLE `lumina_user` CHANGE COLUMN `real_name` `nickname` VARCHAR(100) NULL COMMENT '昵称';

-- ========================================
-- 2. 租户表：contact_name → contact, contact_phone → phone, contact_email → email
-- ========================================
ALTER TABLE `lumina_tenant` CHANGE COLUMN `contact_name` `contact` VARCHAR(50) NULL COMMENT '联系人';
ALTER TABLE `lumina_tenant` CHANGE COLUMN `contact_phone` `phone` VARCHAR(20) NULL COMMENT '联系电话';
ALTER TABLE `lumina_tenant` CHANGE COLUMN `contact_email` `email` VARCHAR(100) NULL COMMENT '联系邮箱';

-- ========================================
-- 3. Agent 表：增加 llm_config + tools + tenant_id
-- ========================================
ALTER TABLE `lumina_agent`
    ADD COLUMN `llm_config` TEXT NULL COMMENT 'LLM 配置 JSON（modelType/modelName/temperature 等）' AFTER `description`,
    ADD COLUMN `tools` VARCHAR(500) NULL COMMENT '工具列表（逗号分隔）' AFTER `llm_config`,
    ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户 ID' AFTER `tools`;
