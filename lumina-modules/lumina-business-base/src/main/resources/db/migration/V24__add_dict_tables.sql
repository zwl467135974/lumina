-- V24: 数据字典管理（字典类型 + 字典项）

CREATE TABLE IF NOT EXISTS `lumina_dict_type` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `dict_type`  VARCHAR(100) NOT NULL COMMENT '字典类型（如 agent_status）',
    `dict_name`  VARCHAR(100) NOT NULL COMMENT '字典名称（如：Agent状态）',
    `status`     TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=启用 0=禁用',
    `remark`     VARCHAR(255) NULL COMMENT '备注',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`    TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dict_type` (`dict_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典类型';

CREATE TABLE IF NOT EXISTS `lumina_dict_item` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `dict_type`   VARCHAR(100) NOT NULL COMMENT '所属字典类型',
    `dict_label`  VARCHAR(100) NOT NULL COMMENT '字典标签（显示文本）',
    `dict_value`  VARCHAR(100) NOT NULL COMMENT '字典值',
    `sort_order`  INT          NOT NULL DEFAULT 0 COMMENT '排序',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=启用 0=禁用',
    `remark`      VARCHAR(255) NULL COMMENT '备注',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    INDEX `idx_dict_type` (`dict_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典项';

-- 种子数据
INSERT INTO `lumina_dict_type` (`dict_type`, `dict_name`, `remark`) VALUES
    ('agent_status', 'Agent状态', 'Agent 启用/禁用状态'),
    ('agent_type', 'Agent类型', 'Agent 执行模式'),
    ('task_status', '任务状态', '异步任务执行状态'),
    ('llm_provider', '模型供应商', 'LLM 模型供应商标识');

INSERT INTO `lumina_dict_item` (`dict_type`, `dict_label`, `dict_value`, `sort_order`) VALUES
    ('agent_status', '启用', '1', 1),
    ('agent_status', '禁用', '0', 2),
    ('agent_type', 'ReAct', 'ReAct', 1),
    ('agent_type', 'Simple', 'simple', 2),
    ('agent_type', 'Tool', 'tool', 3),
    ('agent_type', 'PlanAndExecute', 'PlanAndExecute', 4),
    ('task_status', '排队中', 'QUEUED', 1),
    ('task_status', '运行中', 'RUNNING', 2),
    ('task_status', '已完成', 'COMPLETED', 3),
    ('task_status', '已失败', 'FAILED', 4),
    ('task_status', '已取消', 'CANCELLED', 5),
    ('llm_provider', 'OpenAI', 'openai', 1),
    ('llm_provider', 'Anthropic', 'anthropic', 2),
    ('llm_provider', 'DashScope', 'dashscope', 3),
    ('llm_provider', 'GLM', 'glm', 4),
    ('llm_provider', 'DeepSeek', 'deepseek', 5),
    ('llm_provider', 'Ollama', 'ollama', 6);
