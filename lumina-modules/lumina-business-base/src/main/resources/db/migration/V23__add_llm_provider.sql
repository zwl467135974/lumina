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

-- 插入权限种子
INSERT INTO `lumina_permission` (`parent_id`, `name`, `permission_code`, `type`, `resource_path`, `sort_order`, `icon`, `status`)
VALUES
    (0, '模型管理', 'system:model', 1, '/system/models', 65, 'Coin', 1);

SET @menuId = LAST_INSERT_ID();

INSERT INTO `lumina_permission` (`parent_id`, `name`, `permission_code`, `type`, `resource_path`, `sort_order`, `status`)
VALUES
    (@menuId, '查看', 'model:list',   2, 'GET:/api/v1/agent/llm-providers',     1, 1),
    (@menuId, '创建', 'model:create', 2, 'POST:/api/v1/agent/llm-providers',    2, 1),
    (@menuId, '编辑', 'model:update', 2, 'PUT:/api/v1/agent/llm-providers/*',   3, 1),
    (@menuId, '删除', 'model:delete', 2, 'DELETE:/api/v1/agent/llm-providers/*',4, 1),
    (@menuId, '测试', 'model:test',   2, 'POST:/api/v1/agent/llm-providers/*/test', 5, 1);

-- 给 admin 角色分配权限
INSERT INTO `lumina_role_permission` (`role_id`, `permission_id`)
SELECT 1, permission_id FROM `lumina_permission`
WHERE permission_code IN ('system:model', 'model:list', 'model:create', 'model:update', 'model:delete', 'model:test');
