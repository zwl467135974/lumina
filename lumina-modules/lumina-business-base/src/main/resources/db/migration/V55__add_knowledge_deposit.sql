-- V55: 知识沉淀（自维护 Wiki 知识飞轮——任务产物 → 人工审核 → 入知识库）
-- 对标 WeKnora 自维护 Wiki：让 RAG 知识库随使用变厚，人工审核为质量闸门

CREATE TABLE IF NOT EXISTS `lumina_knowledge_deposit` (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    title           VARCHAR(200) NOT NULL COMMENT '沉淀标题（默认取任务输入前缀，可编辑）',
    content         MEDIUMTEXT NOT NULL COMMENT '沉淀内容（任务产物/手动整理）',
    source_type     VARCHAR(16) NOT NULL DEFAULT 'TASK' COMMENT '来源 TASK=任务产物 MANUAL=手动',
    source_id       VARCHAR(64) NULL COMMENT '来源标识（taskUuid / conversationUuid）',
    agent_id        BIGINT NULL COMMENT '产出该内容的 Agent',
    kb_id           BIGINT NOT NULL COMMENT '目标知识库',
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
    review_comment  VARCHAR(500) NULL COMMENT '审核意见（驳回原因等）',
    reviewed_by     BIGINT NULL,
    review_time     DATETIME NULL,
    doc_uuid        VARCHAR(64) NULL COMMENT '审核通过入库后生成的文档 uuid（回链）',
    tenant_id       BIGINT NOT NULL DEFAULT 0,
    create_by       BIGINT NULL,
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted      TINYINT NOT NULL DEFAULT 0,
    INDEX idx_tenant_status (tenant_id, status),
    INDEX idx_tenant_kb (tenant_id, kb_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识沉淀审核队列（自维护 Wiki 飞轮）';

-- 权限：发起/查看沉淀 与 审核分离（提出人 ≠ 审核人）
INSERT INTO `lumina_permission` (`parent_id`, `permission_code`, `permission_name`, `permission_type`, `sort_order`)
SELECT p.`permission_id`, 'knowledge:deposit', '知识沉淀', 2, 5 FROM `lumina_permission` p WHERE p.`permission_code` = 'knowledge'
ON DUPLICATE KEY UPDATE `permission_name` = VALUES(`permission_name`);
INSERT INTO `lumina_permission` (`parent_id`, `permission_code`, `permission_name`, `permission_type`, `sort_order`)
SELECT p.`permission_id`, 'knowledge:review', '知识审核', 2, 6 FROM `lumina_permission` p WHERE p.`permission_code` = 'knowledge'
ON DUPLICATE KEY UPDATE `permission_name` = VALUES(`permission_name`);

-- SUPER_ADMIN (role_id=1) 全量授予
INSERT IGNORE INTO `lumina_role_permission` (`role_id`, `permission_id`)
SELECT 1, `permission_id` FROM `lumina_permission` WHERE `permission_code` IN ('knowledge:deposit', 'knowledge:review');
-- TENANT_ADMIN (role_id=3) 授予沉淀与审核
INSERT IGNORE INTO `lumina_role_permission` (`role_id`, `permission_id`)
SELECT 3, `permission_id` FROM `lumina_permission` WHERE `permission_code` IN ('knowledge:deposit', 'knowledge:review');
