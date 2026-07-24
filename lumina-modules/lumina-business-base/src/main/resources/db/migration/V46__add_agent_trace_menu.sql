-- 推理追踪菜单（挂在 Agent 管理下，参考 V41 agent:trigger 模式）
INSERT INTO `lumina_permission` (`parent_id`, `permission_code`, `permission_name`, `permission_type`, `path`, `icon`, `sort_order`)
SELECT p.`permission_id`, 'agent:trace', '推理追踪', 1, 'trace', 'View', 8
FROM `lumina_permission` p WHERE p.`permission_code` = 'agent'
ON DUPLICATE KEY UPDATE `permission_name` = VALUES(`permission_name`);

-- 给 SUPER_ADMIN (role_id=1) 分配权限
INSERT IGNORE INTO `lumina_role_permission` (`role_id`, `permission_id`)
SELECT 1, `permission_id` FROM `lumina_permission`
WHERE `permission_code` = 'agent:trace';
