-- V17: 补充各模块权限 + 菜单路由 + 角色分配 + 种子数据（v3.0 稳定化）

-- ========================================
-- 1. 新增模块权限（根菜单 type=1）
-- ========================================

INSERT INTO `lumina_permission` (`parent_id`, `permission_code`, `permission_name`, `permission_type`, `path`, `icon`, `sort_order`) VALUES
(0, 'agent', 'Agent 管理', 1, '/agent', 'Agent', 10),
(0, 'workflow', '工作流管理', 1, '/workflow', 'Connection', 20),
(0, 'knowledge', '知识库', 1, '/knowledge', 'Document', 30),
(0, 'knowledge-base', '知识库联邦', 1, '/knowledge-base', 'Collection', 31),
(0, 'prompt', 'Prompt 管理', 1, '/prompt', 'EditPen', 40),
(0, 'cost', '成本仪表盘', 1, '/cost', 'Money', 50),
(0, 'budget', '预算管理', 1, '/budget', 'Wallet', 51),
(0, 'evaluation', 'Agent 评估', 1, '/evaluation', 'DataAnalysis', 60)
ON DUPLICATE KEY UPDATE `permission_name` = VALUES(`permission_name`);

-- ========================================
-- 2. 子权限（按钮/页面 type=2/1，parent_id 通过子查询引用）
-- ========================================

-- Agent 模块子权限
INSERT INTO `lumina_permission` (`parent_id`, `permission_code`, `permission_name`, `permission_type`, `sort_order`)
SELECT p.`permission_id`, 'agent:list', 'Agent 列表', 1, 1 FROM `lumina_permission` p WHERE p.`permission_code` = 'agent'
ON DUPLICATE KEY UPDATE `permission_name` = VALUES(`permission_name`);
INSERT INTO `lumina_permission` (`parent_id`, `permission_code`, `permission_name`, `permission_type`, `sort_order`)
SELECT p.`permission_id`, 'agent:create', '创建 Agent', 2, 2 FROM `lumina_permission` p WHERE p.`permission_code` = 'agent'
ON DUPLICATE KEY UPDATE `permission_name` = VALUES(`permission_name`);
INSERT INTO `lumina_permission` (`parent_id`, `permission_code`, `permission_name`, `permission_type`, `sort_order`)
SELECT p.`permission_id`, 'agent:update', '编辑 Agent', 2, 3 FROM `lumina_permission` p WHERE p.`permission_code` = 'agent'
ON DUPLICATE KEY UPDATE `permission_name` = VALUES(`permission_name`);
INSERT INTO `lumina_permission` (`parent_id`, `permission_code`, `permission_name`, `permission_type`, `sort_order`)
SELECT p.`permission_id`, 'agent:delete', '删除 Agent', 2, 4 FROM `lumina_permission` p WHERE p.`permission_code` = 'agent'
ON DUPLICATE KEY UPDATE `permission_name` = VALUES(`permission_name`);
INSERT INTO `lumina_permission` (`parent_id`, `permission_code`, `permission_name`, `permission_type`, `sort_order`)
SELECT p.`permission_id`, 'agent:execute', '执行 Agent', 2, 5 FROM `lumina_permission` p WHERE p.`permission_code` = 'agent'
ON DUPLICATE KEY UPDATE `permission_name` = VALUES(`permission_name`);
INSERT INTO `lumina_permission` (`parent_id`, `permission_code`, `permission_name`, `permission_type`, `path`, `sort_order`)
SELECT p.`permission_id`, 'agent:tasks', '异步任务', 1, 'tasks', 6 FROM `lumina_permission` p WHERE p.`permission_code` = 'agent'
ON DUPLICATE KEY UPDATE `permission_name` = VALUES(`permission_name`);

-- 工作流模块子权限
INSERT INTO `lumina_permission` (`parent_id`, `permission_code`, `permission_name`, `permission_type`, `sort_order`)
SELECT p.`permission_id`, 'workflow:list', '工作流列表', 1, 1 FROM `lumina_permission` p WHERE p.`permission_code` = 'workflow'
ON DUPLICATE KEY UPDATE `permission_name` = VALUES(`permission_name`);
INSERT INTO `lumina_permission` (`parent_id`, `permission_code`, `permission_name`, `permission_type`, `sort_order`)
SELECT p.`permission_id`, 'workflow:create', '创建工作流', 2, 2 FROM `lumina_permission` p WHERE p.`permission_code` = 'workflow'
ON DUPLICATE KEY UPDATE `permission_name` = VALUES(`permission_name`);
INSERT INTO `lumina_permission` (`parent_id`, `permission_code`, `permission_name`, `permission_type`, `sort_order`)
SELECT p.`permission_id`, 'workflow:execute', '执行工作流', 2, 3 FROM `lumina_permission` p WHERE p.`permission_code` = 'workflow'
ON DUPLICATE KEY UPDATE `permission_name` = VALUES(`permission_name`);

-- 知识库模块子权限
INSERT INTO `lumina_permission` (`parent_id`, `permission_code`, `permission_name`, `permission_type`, `sort_order`)
SELECT p.`permission_id`, 'knowledge:list', '文档管理', 1, 1 FROM `lumina_permission` p WHERE p.`permission_code` = 'knowledge'
ON DUPLICATE KEY UPDATE `permission_name` = VALUES(`permission_name`);
INSERT INTO `lumina_permission` (`parent_id`, `permission_code`, `permission_name`, `permission_type`, `sort_order`)
SELECT p.`permission_id`, 'knowledge:upload', '上传文档', 2, 2 FROM `lumina_permission` p WHERE p.`permission_code` = 'knowledge'
ON DUPLICATE KEY UPDATE `permission_name` = VALUES(`permission_name`);
INSERT INTO `lumina_permission` (`parent_id`, `permission_code`, `permission_name`, `permission_type`, `sort_order`)
SELECT p.`permission_id`, 'knowledge:delete', '删除文档', 2, 3 FROM `lumina_permission` p WHERE p.`permission_code` = 'knowledge'
ON DUPLICATE KEY UPDATE `permission_name` = VALUES(`permission_name`);
INSERT INTO `lumina_permission` (`parent_id`, `permission_code`, `permission_name`, `permission_type`, `sort_order`)
SELECT p.`permission_id`, 'knowledge:search', '检索测试', 2, 4 FROM `lumina_permission` p WHERE p.`permission_code` = 'knowledge'
ON DUPLICATE KEY UPDATE `permission_name` = VALUES(`permission_name`);

-- 知识库联邦子权限
INSERT INTO `lumina_permission` (`parent_id`, `permission_code`, `permission_name`, `permission_type`, `sort_order`)
SELECT p.`permission_id`, 'knowledge-base:list', '知识库列表', 1, 1 FROM `lumina_permission` p WHERE p.`permission_code` = 'knowledge-base'
ON DUPLICATE KEY UPDATE `permission_name` = VALUES(`permission_name`);
INSERT INTO `lumina_permission` (`parent_id`, `permission_code`, `permission_name`, `permission_type`, `sort_order`)
SELECT p.`permission_id`, 'knowledge-base:create', '创建知识库', 2, 2 FROM `lumina_permission` p WHERE p.`permission_code` = 'knowledge-base'
ON DUPLICATE KEY UPDATE `permission_name` = VALUES(`permission_name`);
INSERT INTO `lumina_permission` (`parent_id`, `permission_code`, `permission_name`, `permission_type`, `sort_order`)
SELECT p.`permission_id`, 'knowledge-base:mount', '挂载知识库', 2, 3 FROM `lumina_permission` p WHERE p.`permission_code` = 'knowledge-base'
ON DUPLICATE KEY UPDATE `permission_name` = VALUES(`permission_name`);

-- Prompt 模块子权限
INSERT INTO `lumina_permission` (`parent_id`, `permission_code`, `permission_name`, `permission_type`, `sort_order`)
SELECT p.`permission_id`, 'prompt:list', 'Prompt 列表', 1, 1 FROM `lumina_permission` p WHERE p.`permission_code` = 'prompt'
ON DUPLICATE KEY UPDATE `permission_name` = VALUES(`permission_name`);
INSERT INTO `lumina_permission` (`parent_id`, `permission_code`, `permission_name`, `permission_type`, `sort_order`)
SELECT p.`permission_id`, 'prompt:create', '创建 Prompt', 2, 2 FROM `lumina_permission` p WHERE p.`permission_code` = 'prompt'
ON DUPLICATE KEY UPDATE `permission_name` = VALUES(`permission_name`);
INSERT INTO `lumina_permission` (`parent_id`, `permission_code`, `permission_name`, `permission_type`, `sort_order`)
SELECT p.`permission_id`, 'prompt:publish', '发布 Prompt', 2, 3 FROM `lumina_permission` p WHERE p.`permission_code` = 'prompt'
ON DUPLICATE KEY UPDATE `permission_name` = VALUES(`permission_name`);

-- 成本/预算子权限
INSERT INTO `lumina_permission` (`parent_id`, `permission_code`, `permission_name`, `permission_type`, `sort_order`)
SELECT p.`permission_id`, 'cost:view', '查看成本', 1, 1 FROM `lumina_permission` p WHERE p.`permission_code` = 'cost'
ON DUPLICATE KEY UPDATE `permission_name` = VALUES(`permission_name`);
INSERT INTO `lumina_permission` (`parent_id`, `permission_code`, `permission_name`, `permission_type`, `sort_order`)
SELECT p.`permission_id`, 'budget:list', '预算列表', 1, 1 FROM `lumina_permission` p WHERE p.`permission_code` = 'budget'
ON DUPLICATE KEY UPDATE `permission_name` = VALUES(`permission_name`);
INSERT INTO `lumina_permission` (`parent_id`, `permission_code`, `permission_name`, `permission_type`, `sort_order`)
SELECT p.`permission_id`, 'budget:create', '创建预算', 2, 2 FROM `lumina_permission` p WHERE p.`permission_code` = 'budget'
ON DUPLICATE KEY UPDATE `permission_name` = VALUES(`permission_name`);

-- 评估子权限
INSERT INTO `lumina_permission` (`parent_id`, `permission_code`, `permission_name`, `permission_type`, `sort_order`)
SELECT p.`permission_id`, 'evaluation:list', '评估列表', 1, 1 FROM `lumina_permission` p WHERE p.`permission_code` = 'evaluation'
ON DUPLICATE KEY UPDATE `permission_name` = VALUES(`permission_name`);
INSERT INTO `lumina_permission` (`parent_id`, `permission_code`, `permission_name`, `permission_type`, `sort_order`)
SELECT p.`permission_id`, 'evaluation:create', '创建数据集', 2, 2 FROM `lumina_permission` p WHERE p.`permission_code` = 'evaluation'
ON DUPLICATE KEY UPDATE `permission_name` = VALUES(`permission_name`);
INSERT INTO `lumina_permission` (`parent_id`, `permission_code`, `permission_name`, `permission_type`, `sort_order`)
SELECT p.`permission_id`, 'evaluation:run', '执行评估', 2, 3 FROM `lumina_permission` p WHERE p.`permission_code` = 'evaluation'
ON DUPLICATE KEY UPDATE `permission_name` = VALUES(`permission_name`);

-- ========================================
-- 3. 更新已有系统管理权限的路由信息
-- ========================================
UPDATE `lumina_permission` SET `path` = '/system', `icon` = 'Setting', `sort_order` = 100
WHERE `permission_code` = 'system' AND `permission_type` = 1;

UPDATE `lumina_permission` SET `path` = 'user', `icon` = 'User'
WHERE `permission_code` = 'system:user' AND `permission_type` = 1;

UPDATE `lumina_permission` SET `path` = 'role', `icon` = 'UserFilled'
WHERE `permission_code` = 'system:role' AND `permission_type` = 1;

UPDATE `lumina_permission` SET `path` = 'permission', `icon` = 'Lock'
WHERE `permission_code` = 'system:permission' AND `permission_type` = 1;

UPDATE `lumina_permission` SET `path` = 'tenant', `icon` = 'OfficeBuilding'
WHERE `permission_code` = 'system:tenant' AND `permission_type` = 1;

-- ========================================
-- 4. 角色权限分配
-- ========================================

-- SUPER_ADMIN（role_id=1）拥有所有权限（含新增模块）
INSERT IGNORE INTO `lumina_role_permission` (`role_id`, `permission_id`)
SELECT 1, `permission_id` FROM `lumina_permission`;

-- TENANT_ADMIN（role_id=3）：系统用户/角色管理 + 所有业务模块
INSERT IGNORE INTO `lumina_role_permission` (`role_id`, `permission_id`)
SELECT 3, `permission_id` FROM `lumina_permission`
WHERE `permission_code` LIKE 'system:user%'
   OR `permission_code` LIKE 'system:role%'
   OR `permission_code` LIKE 'agent%'
   OR `permission_code` LIKE 'workflow%'
   OR `permission_code` LIKE 'knowledge%'
   OR `permission_code` LIKE 'prompt%'
   OR `permission_code` LIKE 'cost%'
   OR `permission_code` LIKE 'budget%'
   OR `permission_code` LIKE 'evaluation%';

-- TENANT_USER（role_id=4）：基础查看 + Agent 执行
INSERT IGNORE INTO `lumina_role_permission` (`role_id`, `permission_id`)
SELECT 4, `permission_id` FROM `lumina_permission`
WHERE `permission_code` IN (
    'agent', 'agent:list', 'agent:execute', 'agent:tasks',
    'workflow', 'workflow:list',
    'knowledge', 'knowledge:list', 'knowledge:search',
    'prompt', 'prompt:list',
    'cost', 'cost:view',
    'evaluation', 'evaluation:list'
);

-- ========================================
-- 5. 种子数据：示例 Agent（tenant_id 在 V18 中添加）
-- ========================================
INSERT INTO `lumina_agent` (`agent_id`, `agent_name`, `agent_type`, `description`, `status`) VALUES
(1, '通用助手', 'assistant', '通用 AI 助手，支持日常问答、文本创作、代码辅助等场景', 1),
(2, '客服 Agent', 'customer-service', '模拟客服场景的智能体，支持意图分类、工单查询、退款政策问答', 1)
ON DUPLICATE KEY UPDATE `agent_name` = VALUES(`agent_name`);

-- ========================================
-- 6. 种子数据：示例 Prompt（status=1 已发布，is_active=1 激活）
-- ========================================
INSERT INTO `lumina_prompt` (`name`, `description`, `content`, `agent_type`, `version`, `status`, `is_active`, `tenant_id`, `create_by`) VALUES
('assistant', '通用助手系统提示词', '你是一个友好、专业的 AI 助手。请根据用户的问题给出准确、有帮助的回答。\n\n用户任务：{task}', 'assistant', 1, 1, 1, 0, 1),
('customer-service', '客服场景系统提示词', '你是一个专业的客服代表。请耐心解答用户问题，涉及退款、物流、产品咨询等场景。如果无法回答，请建议转接人工客服。\n\n用户任务：{task}', 'customer-service', 1, 1, 1, 0, 1)
ON DUPLICATE KEY UPDATE `content` = VALUES(`content`);
