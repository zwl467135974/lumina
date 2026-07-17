-- V35: 长期记忆菜单种子
INSERT IGNORE INTO `lumina_permission`
    (`parent_id`, `permission_code`, `permission_name`, `permission_type`, `path`, `icon`, `sort_order`)
VALUES
    (0, 'long-term-memory', '长期记忆', 1, '/memory', 'Coin', 90);

INSERT IGNORE INTO `lumina_role_permission` (`role_id`, `permission_id`)
SELECT 1, `permission_id` FROM `lumina_permission`
WHERE `permission_code` = 'long-term-memory';
