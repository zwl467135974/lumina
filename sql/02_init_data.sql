-- ========================================
-- Lumina 项目数据初始化脚本
-- 版本：1.0.0
-- 日期：2025-01-20
-- 说明：插入系统初始数据（租户、角色、权限、用户）
-- ========================================

-- ========================================
-- 第一部分：租户数据初始化
-- ========================================

-- 1. 插入系统租户（tenant_id=0）
INSERT INTO `lumina_tenant` (`tenant_id`, `tenant_code`, `tenant_name`, `status`)
VALUES (0, 'SYSTEM', '系统租户', 1)
ON DUPLICATE KEY UPDATE `tenant_name` = `tenant_name`;

-- 2. 创建默认租户（用于迁移现有用户）
INSERT INTO `lumina_tenant` (`tenant_code`, `tenant_name`, `status`)
VALUES ('DEFAULT', '默认租户', 1)
ON DUPLICATE KEY UPDATE `tenant_name` = `tenant_name`;

-- ========================================
-- 第二部分：角色数据初始化
-- ========================================

-- 3. 插入系统角色
INSERT INTO `lumina_role` (`role_id`, `tenant_id`, `role_code`, `role_name`, `sort_order`) VALUES
(1, 0, 'SUPER_ADMIN', '超级管理员', 1),
(2, 0, 'SYSTEM_ADMIN', '系统管理员', 2),
(3, 1, 'TENANT_ADMIN', '租户管理员', 1),
(4, 1, 'TENANT_USER', '普通用户', 2)
ON DUPLICATE KEY UPDATE `role_name` = `role_name`;

-- ========================================
-- 第三部分：权限数据初始化
-- ========================================

-- 4. 插入权限数据（系统管理模块）
INSERT INTO `lumina_permission` (`parent_id`, `permission_code`, `permission_name`, `permission_type`) VALUES
-- 系统管理根节点
(0, 'system', '系统管理', 1),
-- 租户管理
(1, 'system:tenant', '租户管理', 1),
(2, 'system:tenant:list', '租户列表', 2),
(2, 'system:tenant:create', '创建租户', 2),
(2, 'system:tenant:update', '编辑租户', 2),
(2, 'system:tenant:delete', '删除租户', 2),
-- 用户管理
(1, 'system:user', '用户管理', 1),
(7, 'system:user:list', '用户列表', 2),
(7, 'system:user:create', '创建用户', 2),
(7, 'system:user:update', '编辑用户', 2),
(7, 'system:user:delete', '删除用户', 2),
(7, 'system:user:assign', '分配角色', 2),
-- 角色管理
(1, 'system:role', '角色管理', 1),
(13, 'system:role:list', '角色列表', 2),
(13, 'system:role:create', '创建角色', 2),
(13, 'system:role:update', '编辑角色', 2),
(13, 'system:role:assign', '分配权限', 2),
(13, 'system:role:delete', '删除角色', 2),
-- 权限管理
(1, 'system:permission', '权限管理', 1),
(19, 'system:permission:tree', '权限树', 2),
(19, 'system:permission:create', '创建权限', 2),
(19, 'system:permission:update', '编辑权限', 2)
ON DUPLICATE KEY UPDATE `permission_name` = `permission_name`;

-- ========================================
-- 第四部分：角色权限关联
-- ========================================

-- 5. 为角色分配权限
-- 超级管理员拥有所有权限
INSERT IGNORE INTO `lumina_role_permission` (`role_id`, `permission_id`)
SELECT 1, `permission_id` FROM `lumina_permission`;

-- 租户管理员拥有用户管理权限
INSERT IGNORE INTO `lumina_role_permission` (`role_id`, `permission_id`)
SELECT 3, `permission_id` FROM `lumina_permission`
WHERE `permission_code` LIKE 'system:user%';

-- ========================================
-- 第五部分：用户数据初始化
-- ========================================

-- 6. 插入默认管理员用户（如果不存在）
-- 用户名：admin
-- 密码：admin123（BCrypt加密）
-- 租户：系统租户（tenant_id=0）
-- 角色：超级管理员
INSERT INTO `lumina_user` (`user_id`, `tenant_id`, `username`, `password`, `real_name`, `status`)
VALUES (1, 0, 'admin', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5NU9XKaQUeAqn.', '系统管理员', 1)
ON DUPLICATE KEY UPDATE `username` = `username`;

-- 7. 为管理员用户分配角色
INSERT IGNORE INTO `lumina_user_role` (`user_id`, `role_id`)
SELECT `user_id`, 1 FROM `lumina_user` WHERE `username` = 'admin';

-- ========================================
-- 初始化完成
-- ========================================

-- 显示初始化统计
SELECT '============================================' AS '';
SELECT 'Lumina 数据库初始化完成' AS '';
SELECT '============================================' AS '';
SELECT CONCAT('租户数量：', COUNT(*)) AS '' FROM `lumina_tenant`;
SELECT CONCAT('角色数量：', COUNT(*)) AS '' FROM `lumina_role`;
SELECT CONCAT('权限数量：', COUNT(*)) AS '' FROM `lumina_permission`;
SELECT CONCAT('用户数量：', COUNT(*)) AS '' FROM `lumina_user`;
SELECT '============================================' AS '';
SELECT '默认管理员账号：' AS '';
SELECT '用户名：admin' AS '';
SELECT '密码：admin123' AS '';
SELECT '租户：SYSTEM（系统租户）' AS '';
SELECT '角色：SUPER_ADMIN（超级管理员）' AS '';
SELECT '============================================' AS '';
