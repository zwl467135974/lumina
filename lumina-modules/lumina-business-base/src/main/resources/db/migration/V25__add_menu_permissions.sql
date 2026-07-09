-- V25: 补充菜单管理/字典管理/在线用户/审计日志 的菜单权限

-- 查找系统管理菜单 ID
SET @sysMenuId = (SELECT permission_id FROM lumina_permission WHERE permission_code = 'system' AND permission_type = 1 LIMIT 1);

INSERT IGNORE INTO `lumina_permission` (`parent_id`, `permission_code`, `permission_name`, `permission_type`, `path`, `icon`, `sort_order`)
VALUES
    (@sysMenuId, 'system:audit',   '审计日志', 1, '/system/audit',   'Document', 50),
    (@sysMenuId, 'system:menu',    '菜单管理', 1, '/system/menu',    'Menu',    55),
    (@sysMenuId, 'system:dict',    '字典管理', 1, '/system/dict',    'Collection', 60),
    (@sysMenuId, 'system:online',  '在线用户', 1, '/system/online',  'Monitor', 65);

-- 给 admin 角色分配权限
INSERT IGNORE INTO `lumina_role_permission` (`role_id`, `permission_id`)
SELECT 1, `permission_id` FROM `lumina_permission`
WHERE `permission_code` IN ('system:audit', 'system:menu', 'system:dict', 'system:online');
