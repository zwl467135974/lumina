-- V39: Webhook 订阅菜单 + 权限种子（挂在通知中心菜单下，参考 V30）

-- 1. 子菜单：Webhook 订阅（挂在通知中心下）
SET @notifMenuId = (SELECT `permission_id` FROM `lumina_permission` WHERE `permission_code` = 'notification');

INSERT IGNORE INTO `lumina_permission`
    (`parent_id`, `permission_code`, `permission_name`, `permission_type`, `path`, `icon`, `sort_order`)
VALUES
    (@notifMenuId, 'notification:webhook', 'Webhook 订阅', 1, '/notification/webhooks', 'Link', 2);

-- 2. 给 SUPER_ADMIN (role_id=1) 分配权限
INSERT IGNORE INTO `lumina_role_permission` (`role_id`, `permission_id`)
SELECT 1, `permission_id` FROM `lumina_permission`
WHERE `permission_code` IN ('notification:webhook');
