-- V37: API Token 管理菜单种子（参考 V25/V27 模式）

-- 查找系统管理菜单 ID
SET @sysMenuId = (SELECT permission_id FROM lumina_permission WHERE permission_code = 'system' AND permission_type = 1 LIMIT 1);

INSERT IGNORE INTO `lumina_permission` (`parent_id`, `permission_code`, `permission_name`, `permission_type`, `path`, `icon`, `sort_order`)
VALUES
    (@sysMenuId, 'system:api-token', 'API Token', 1, '/system/api-tokens', 'Key', 70);

-- 给 admin 角色分配权限
INSERT IGNORE INTO `lumina_role_permission` (`role_id`, `permission_id`)
SELECT 1, `permission_id` FROM `lumina_permission`
WHERE `permission_code` = 'system:api-token';
