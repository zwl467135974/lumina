-- V27: 补充监控中心菜单权限（工具监控 + MCP 管理）

-- 插入监控中心父菜单（permission_type=1 为菜单）
INSERT IGNORE INTO `lumina_permission` (`parent_id`, `permission_code`, `permission_name`, `permission_type`, `path`, `icon`, `sort_order`)
VALUES
    (0, 'monitor',       '监控中心',  1, '/monitor',       'Monitor',  90),
    (0, 'monitor:tools', '工具监控',  1, '/monitor/tools', 'Tools',    91),
    (0, 'monitor:mcp',   'MCP 管理', 1, '/monitor/mcp',   'Connection', 92);

-- 设置父菜单关系（tools 和 mcp 的 parent_id 指向 monitor）
SET @monitorId = (SELECT permission_id FROM lumina_permission WHERE permission_code = 'monitor' AND permission_type = 1 LIMIT 1);

UPDATE `lumina_permission` SET `parent_id` = @monitorId
WHERE `permission_code` IN ('monitor:tools', 'monitor:mcp') AND `parent_id` = 0;

-- 给 admin 角色分配权限
INSERT IGNORE INTO `lumina_role_permission` (`role_id`, `permission_id`)
SELECT 1, `permission_id` FROM `lumina_permission`
WHERE `permission_code` IN ('monitor', 'monitor:tools', 'monitor:mcp');
