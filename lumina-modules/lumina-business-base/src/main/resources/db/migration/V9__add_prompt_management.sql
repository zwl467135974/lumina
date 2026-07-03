-- V9: Prompt 版本管理表

CREATE TABLE IF NOT EXISTS lumina_prompt (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL COMMENT 'Prompt 名称（如 react / customer-service）',
    version     INT NOT NULL DEFAULT 1 COMMENT '版本号',
    content     TEXT NOT NULL COMMENT 'Prompt 模板内容（支持 {变量名} 占位符）',
    description VARCHAR(500) COMMENT '描述',
    variables   VARCHAR(500) COMMENT '变量列表（逗号分隔，如 task,context）',
    status      TINYINT NOT NULL DEFAULT 0 COMMENT '0=草稿 1=已发布',
    is_active   TINYINT NOT NULL DEFAULT 0 COMMENT '1=当前激活版本（每个 name 仅一个）',
    tenant_id   BIGINT NOT NULL DEFAULT 0,
    create_by   BIGINT,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted  TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_name_version (name, version),
    INDEX idx_name_active (name, is_active),
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Prompt 版本管理';

-- 初始化：将现有 ClassPath Prompt 导入为 v1
INSERT INTO lumina_prompt (name, version, content, description, variables, status, is_active, tenant_id)
VALUES
    ('react', 1, '你是一个 ReAct Agent，请通过"思考-行动-观察"循环完成用户任务。\n\n用户任务：{task}', 'ReAct Agent 默认 Prompt', 'task', 1, 1, 0),
    ('simple', 1, '你是一个智能助手，请根据用户的需求提供帮助。\n\n用户需求：{task}', '简单对话 Agent Prompt', 'task', 1, 1, 0),
    ('tool', 1, '你是一个工具调用 Agent，可以使用提供的工具完成任务。\n\n用户任务：{task}', '工具调用 Agent Prompt', 'task', 1, 1, 0);
