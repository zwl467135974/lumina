-- V23: LLM 模型供应商管理表

CREATE TABLE IF NOT EXISTS `lumina_llm_provider` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`           VARCHAR(100) NOT NULL COMMENT '配置名称（如：生产环境-Qwen）',
    `provider`       VARCHAR(50)  NOT NULL COMMENT '供应商标识（openai/anthropic/dashscope/glm/zhipu/ollama 等）',
    `base_url`       VARCHAR(500) NULL COMMENT 'API Base URL（留空使用内置预设）',
    `api_key_enc`    TEXT         NULL COMMENT '加密后的 API Key',
    `default_model`  VARCHAR(100) NULL COMMENT '默认模型名（如 qwen-plus / gpt-4o）',
    `default_params` TEXT         NULL COMMENT '默认参数 JSON（temperature/maxTokens/topP 等）',
    `status`         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=启用 0=禁用',
    `tenant_id`      BIGINT       NOT NULL DEFAULT 0 COMMENT '租户 ID',
    `create_by`      BIGINT       NULL COMMENT '创建人',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    INDEX `idx_provider_tenant` (`provider`, `tenant_id`),
    INDEX `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LLM 模型供应商配置';

-- 插入权限种子（列名对齐 lumina_permission 表: permission_name/permission_type/path）
INSERT INTO `lumina_permission` (`parent_id`, `permission_code`, `permission_name`, `permission_type`, `path`, `icon`, `sort_order`)
VALUES (0, 'system:model', '模型管理', 1, '/system/models', 'Coin', 65);

SET @menuId = LAST_INSERT_ID();

INSERT INTO `lumina_permission` (`parent_id`, `permission_code`, `permission_name`, `permission_type`, `path`, `sort_order`)
VALUES
    (@menuId, 'model:list',   '查看', 2, 'GET:/api/v1/llm-providers',          1),
    (@menuId, 'model:create', '创建', 2, 'POST:/api/v1/llm-providers',         2),
    (@menuId, 'model:update', '编辑', 2, 'PUT:/api/v1/llm-providers/*',        3),
    (@menuId, 'model:delete', '删除', 2, 'DELETE:/api/v1/llm-providers/*',     4),
    (@menuId, 'model:test',   '测试', 2, 'POST:/api/v1/llm-providers/*/test',  5);

-- 给 admin 角色分配权限
INSERT IGNORE INTO `lumina_role_permission` (`role_id`, `permission_id`)
SELECT 1, `permission_id` FROM `lumina_permission`
WHERE `permission_code` IN ('system:model', 'model:list', 'model:create', 'model:update', 'model:delete', 'model:test');
