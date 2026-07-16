-- V30: 通知中心菜单 + 权限种子
-- 为通知中心页面添加侧边栏菜单入口和查看权限

-- 1. 顶级菜单
INSERT IGNORE INTO `lumina_permission`
    (`parent_id`, `permission_code`, `permission_name`, `permission_type`, `path`, `icon`, `sort_order`)
VALUES
    (0, 'notification', '通知中心', 1, '/notification', 'Bell', 70);

-- 2. 子权限：查看通知
SET @notifMenuId = (SELECT `permission_id` FROM `lumina_permission` WHERE `permission_code` = 'notification');

INSERT IGNORE INTO `lumina_permission`
    (`parent_id`, `permission_code`, `permission_name`, `permission_type`, `sort_order`)
VALUES
    (@notifMenuId, 'notification:view', '查看通知', 1, 1);

-- 3. 给 SUPER_ADMIN (role_id=1) 分配权限
INSERT IGNORE INTO `lumina_role_permission` (`role_id`, `permission_id`)
SELECT 1, `permission_id` FROM `lumina_permission`
WHERE `permission_code` IN ('notification', 'notification:view');
