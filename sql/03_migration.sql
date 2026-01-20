-- ========================================
-- Lumina 项目数据迁移脚本
-- 版本：1.0.0
-- 日期：2025-01-20
-- 说明：从旧版本迁移数据到新结构
-- ========================================

-- ========================================
-- 迁移步骤说明
-- ========================================
-- 1. 检查并添加 tenant_id 字段到 lumina_user 表
-- 2. 更新现有用户的租户 ID
-- 3. 为现有用户分配默认角色
-- 4. 删除旧的 role 字段
-- ========================================

-- ========================================
-- 第一步：修改用户表结构
-- ========================================

-- 1. 检查 tenant_id 字段是否存在，不存在则添加
SET @column_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'lumina_user'
    AND COLUMN_NAME = 'tenant_id'
);

SET @sql = IF(@column_exists = 0,
    'ALTER TABLE `lumina_user` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1 AFTER `user_id`',
    'SELECT "Column tenant_id already exists"');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. 添加复合唯一索引（租户内用户名唯一）
-- 先删除旧的唯一索引（如果存在）
ALTER TABLE `lumina_user` DROP INDEX IF EXISTS `uk_username`;
ALTER TABLE `lumina_user` ADD UNIQUE KEY `uk_tenant_username` (`tenant_id`, `username`);

-- ========================================
-- 第二步：更新现有用户的租户 ID
-- ========================================

-- 3. admin 用户分配到系统租户（tenant_id=0）
UPDATE `lumina_user` SET `tenant_id` = 0 WHERE `username` = 'admin';

-- 4. 其他用户分配到默认租户（tenant_id=1）
UPDATE `lumina_user` SET `tenant_id` = 1 WHERE `tenant_id` NOT IN (0, 1);

-- ========================================
-- 第三步：为现有用户分配角色
-- ========================================

-- 5. 为非 admin 用户分配普通用户角色
INSERT IGNORE INTO `lumina_user_role` (`user_id`, `role_id`)
SELECT `user_id`, 4 FROM `lumina_user` WHERE `username` != 'admin';

-- ========================================
-- 第四步：清理旧字段
-- ========================================

-- 6. 删除旧的 role 字段（如果存在）
SET @column_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'lumina_user'
    AND COLUMN_NAME = 'role'
);

SET @sql = IF(@column_exists > 0,
    'ALTER TABLE `lumina_user` DROP COLUMN `role`',
    'SELECT "Column role does not exist or already dropped"');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ========================================
-- 迁移完成
-- ========================================

SELECT '============================================' AS '';
SELECT '数据迁移完成！' AS '';
SELECT '============================================' AS '';
SELECT '请检查以下内容：' AS '';
SELECT '1. 用户表已添加 tenant_id 字段' AS '';
SELECT '2. admin 用户已分配到系统租户（tenant_id=0）' AS '';
SELECT '3. 其他用户已分配到默认租户（tenant_id=1）' AS '';
SELECT '4. 用户名在租户内唯一（uk_tenant_username）' AS '';
SELECT '5. 旧的 role 字段已删除' AS '';
SELECT '============================================' AS '';
