-- V21: 修正动态菜单图标与页面菜单路径

UPDATE `lumina_permission` SET `icon` = 'Monitor'
WHERE `permission_code` = 'agent' AND `permission_type` = 1;

UPDATE `lumina_permission` SET `path` = 'list'
WHERE `permission_code` = 'agent:list' AND `permission_type` = 1;

UPDATE `lumina_permission` SET `path` = 'list'
WHERE `permission_code` = 'workflow:list' AND `permission_type` = 1;

UPDATE `lumina_permission` SET `path` = ''
WHERE `permission_code` IN ('knowledge:list', 'cost:view', 'budget:list', 'evaluation:list')
  AND `permission_type` = 1;
