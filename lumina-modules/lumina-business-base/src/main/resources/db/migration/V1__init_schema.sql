-- ========================================
-- Lumina 项目数据库初始化脚本
-- 版本：1.0.0
-- 日期：2025-01-20
-- 说明：创建所有核心业务表
-- ========================================

-- ========================================
-- 第一部分：Base 模块表（用户、角色、权限、租户）
-- ========================================

-- 1. 用户表
CREATE TABLE IF NOT EXISTS `lumina_user` (
    `user_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID（0表示系统管理员）',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名（租户内唯一）',
    `password` VARCHAR(255) NOT NULL COMMENT '密码（BCrypt加密）',
    `real_name` VARCHAR(100) COMMENT '真实姓名',
    `email` VARCHAR(100) COMMENT '邮箱',
    `phone` VARCHAR(20) COMMENT '手机号',
    `avatar` VARCHAR(255) COMMENT '头像URL',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态（0-禁用，1-启用）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`user_id`),
    UNIQUE KEY `uk_tenant_username` (`tenant_id`, `username`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_email` (`email`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 2. 租户表
CREATE TABLE IF NOT EXISTS `lumina_tenant` (
    `tenant_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '租户ID',
    `tenant_code` VARCHAR(50) NOT NULL COMMENT '租户编码（唯一标识）',
    `tenant_name` VARCHAR(100) NOT NULL COMMENT '租户名称',
    `contact_name` VARCHAR(50) COMMENT '联系人',
    `contact_phone` VARCHAR(20) COMMENT '联系电话',
    `contact_email` VARCHAR(100) COMMENT '联系邮箱',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态（0-禁用，1-启用）',
    `expire_time` DATETIME COMMENT '过期时间（NULL表示永久）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`tenant_id`),
    UNIQUE KEY `uk_tenant_code` (`tenant_code`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户表';

-- 3. 角色表
CREATE TABLE IF NOT EXISTS `lumina_role` (
    `role_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '角色ID',
    `tenant_id` BIGINT NOT NULL COMMENT '租户ID（0表示系统角色）',
    `role_code` VARCHAR(50) NOT NULL COMMENT '角色编码',
    `role_name` VARCHAR(100) NOT NULL COMMENT '角色名称',
    `description` VARCHAR(500) COMMENT '角色描述',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序序号',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态（0-禁用，1-启用）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`role_id`),
    UNIQUE KEY `uk_tenant_role_code` (`tenant_id`, `role_code`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

-- 4. 权限表（树形结构）
CREATE TABLE IF NOT EXISTS `lumina_permission` (
    `permission_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '权限ID',
    `parent_id` BIGINT NOT NULL DEFAULT 0 COMMENT '父权限ID（0表示根节点）',
    `permission_code` VARCHAR(100) NOT NULL COMMENT '权限编码（如：system:user:create）',
    `permission_name` VARCHAR(100) NOT NULL COMMENT '权限名称',
    `permission_type` TINYINT NOT NULL COMMENT '权限类型（1-菜单，2-按钮，3-接口）',
    `path` VARCHAR(255) COMMENT '路由路径（菜单类型专用）',
    `component` VARCHAR(255) COMMENT '组件路径（菜单类型专用）',
    `icon` VARCHAR(100) COMMENT '图标',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序序号',
    `visible` TINYINT NOT NULL DEFAULT 1 COMMENT '是否可见（0-隐藏，1-可见）',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态（0-禁用，1-启用）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`permission_id`),
    UNIQUE KEY `uk_permission_code` (`permission_code`),
    KEY `idx_parent_id` (`parent_id`),
    KEY `idx_permission_type` (`permission_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限表';

-- 5. 用户-角色关联表
CREATE TABLE IF NOT EXISTS `lumina_user_role` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';

-- 6. 角色-权限关联表
CREATE TABLE IF NOT EXISTS `lumina_role_permission` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `permission_id` BIGINT NOT NULL COMMENT '权限ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`),
    KEY `idx_role_id` (`role_id`),
    KEY `idx_permission_id` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联表';

-- ========================================
-- 第二部分：Agent 模块表
-- ========================================

-- 7. Agent 表
CREATE TABLE IF NOT EXISTS `lumina_agent` (
    `agent_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Agent ID',
    `agent_name` VARCHAR(100) NOT NULL COMMENT 'Agent 名称',
    `agent_type` VARCHAR(50) NOT NULL COMMENT 'Agent 类型',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '描述',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态（0-禁用，1-启用）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除（0-未删除，1-已删除）',
    PRIMARY KEY (`agent_id`),
    KEY `idx_agent_name` (`agent_name`),
    KEY `idx_agent_type` (`agent_type`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Agent 表';
