-- V50: 工具调用审批权限（v3.11 工具安全管线）

-- 按钮权限：工具调用审批（挂在 Agent 管理下，参考 V41 agent:trigger 模式）
INSERT INTO `lumina_permission` (`parent_id`, `permission_code`, `permission_name`, `permission_type`, `sort_order`)
SELECT p.`permission_id`, 'agent:tool-approval', '工具调用审批', 2, 9 FROM `lumina_permission` p WHERE p.`permission_code` = 'agent'
ON DUPLICATE KEY UPDATE `permission_name` = VALUES(`permission_name`);

-- 给 SUPER_ADMIN (role_id=1) 分配权限
INSERT IGNORE INTO `lumina_role_permission` (`role_id`, `permission_id`)
SELECT 1, `permission_id` FROM `lumina_permission`
WHERE `permission_code` = 'agent:tool-approval';
