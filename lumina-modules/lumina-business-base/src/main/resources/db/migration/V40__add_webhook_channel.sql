-- V40: lumina_webhook 加 channel 列（通知渠道：通用 Webhook / 企业微信群机器人）
-- 已有数据默认是 WEBHOOK，无需回填

ALTER TABLE `lumina_webhook`
    ADD COLUMN `channel` VARCHAR(16) NOT NULL DEFAULT 'WEBHOOK' COMMENT 'WEBHOOK|WE_COM' AFTER `url`;
