-- V34: A/B 测试菜单 + 权限种子
-- 为 A/B 测试页面添加侧边栏菜单入口和查看权限

-- 1. 顶级菜单
INSERT IGNORE INTO `lumina_permission`
    (`parent_id`, `permission_code`, `permission_name`, `permission_type`, `path`, `icon`, `sort_order`)
VALUES
    (0, 'ab-test', 'A/B 测试', 1, '/ab-test', 'Aim', 80);

-- 2. 子权限：查看实验
SET @abTestMenuId = (SELECT `permission_id` FROM `lumina_permission` WHERE `permission_code` = 'ab-test');

INSERT IGNORE INTO `lumina_permission`
    (`parent_id`, `permission_code`, `permission_name`, `permission_type`, `sort_order`)
VALUES
    (@abTestMenuId, 'ab-test:view', '查看实验', 1, 1);

-- 3. 给 SUPER_ADMIN (role_id=1) 分配权限
INSERT IGNORE INTO `lumina_role_permission` (`role_id`, `permission_id`)
SELECT 1, `permission_id` FROM `lumina_permission`
WHERE `permission_code` IN ('ab-test', 'ab-test:view');
