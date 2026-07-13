-- 通知中心集成测试建表脚本（自包含，避免依赖 base 模块的迁移脚本形成 Maven 循环依赖）
-- DDL 与生产脚本 base/V26__add_notification_table.sql 保持一致
CREATE TABLE IF NOT EXISTS lumina_notification (
    id              BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT       NOT NULL                          COMMENT '收件人用户 ID',
    category        VARCHAR(30)  NOT NULL                          COMMENT '分类: BUDGET/TASK/WORKFLOW/DOCUMENT/EVALUATION/SYSTEM',
    title           VARCHAR(200) NOT NULL                          COMMENT '通知标题',
    content         TEXT         NOT NULL                          COMMENT '通知内容',
    severity        VARCHAR(10)  NOT NULL DEFAULT 'INFO'           COMMENT '级别: INFO/WARN/ERROR',
    ref_type        VARCHAR(50)  NULL                              COMMENT '关联实体类型(agent_task/workflow_instance/...)',
    ref_id          VARCHAR(100) NULL                              COMMENT '关联实体 ID',
    is_read         TINYINT      NOT NULL DEFAULT 0                COMMENT '0=未读 1=已读',
    tenant_id       BIGINT       NOT NULL DEFAULT 0                COMMENT '租户 ID',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    read_time       DATETIME     NULL                              COMMENT '已读时间',
    INDEX idx_user_read (user_id, is_read),
    INDEX idx_tenant_time (tenant_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内通知';
