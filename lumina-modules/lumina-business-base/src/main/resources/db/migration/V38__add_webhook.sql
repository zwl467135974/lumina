-- V38: 外部 Webhook 订阅表（per-user/per-category webhook 订阅）

CREATE TABLE IF NOT EXISTS `lumina_webhook` (
    `id`                BIGINT       AUTO_INCREMENT PRIMARY KEY,
    `user_id`           BIGINT       NOT NULL                           COMMENT '订阅用户',
    `tenant_id`         BIGINT       NOT NULL DEFAULT 0                 COMMENT '租户 ID',
    `name`              VARCHAR(64)  NOT NULL                           COMMENT '用户自填名称',
    `url`               VARCHAR(512) NOT NULL                           COMMENT 'webhook 接收端 URL',
    `secret`            VARCHAR(128) NULL                               COMMENT 'HMAC 签名密钥（可选）',
    `events`            VARCHAR(256) NOT NULL DEFAULT '*'               COMMENT '订阅的 NotificationCategory 逗号分隔，* 表示全部',
    `enabled`           TINYINT      NOT NULL DEFAULT 1                 COMMENT '0=禁用 1=启用',
    `last_triggered_at` DATETIME     NULL                               COMMENT '最后触发时间',
    `last_status`       VARCHAR(16)  NULL                               COMMENT 'SUCCESS/FAILED',
    `last_error`        VARCHAR(512) NULL                               COMMENT '最后失败原因',
    `fail_count`        INT          NOT NULL DEFAULT 0                 COMMENT '连续失败次数，达 5 自动 disable',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           TINYINT      NOT NULL DEFAULT 0                 COMMENT '逻辑删除',
    KEY `idx_user` (`user_id`),
    KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外部 Webhook 订阅';
