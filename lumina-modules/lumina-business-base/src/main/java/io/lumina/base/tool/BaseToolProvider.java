package io.lumina.base.tool;

import io.lumina.agent.tool.AgentTool;
import io.lumina.base.api.dto.user.CreateUserDTO;
import io.lumina.base.api.vo.user.UserVO;
import io.lumina.base.service.UserService;
import io.lumina.common.core.R;
import io.lumina.common.core.BaseContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base模块工具提供者
 *
 * <p>为 Agent 提供 Base 模块功能的工具调用接口。
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Slf4j
@Component
public class BaseToolProvider {

    @Autowired
    private UserService userService;

    /**
     * 创建用户工具
     *
     * <p>Agent 可以通过此工具创建用户
     */
    @AgentTool(
        name = "base.createUser",
        description = "创建新用户。需要提供用户名、密码、真实姓名、邮箱、手机号等信息。返回创建的用户ID。",
        category = "base.user"
    )
    public Map<String, Object> createUser(String username, String password, String realName,
                                          String email, String phone) {
        log.info("Agent 调用创建用户工具: username={}", username);

        try {
            // 1. 构建 DTO
            CreateUserDTO dto = new CreateUserDTO();
            dto.setUsername(username);
            dto.setPassword(password);
            dto.setRealName(realName);
            dto.setEmail(email);
            dto.setPhone(phone);
            dto.setRoleIds(new ArrayList<>()); // 默认无角色

            // 2. 调用服务
            Long userId = userService.createUser(dto);

            // 3. 返回结果
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("userId", userId);
            result.put("message", "用户创建成功");
            return result;

        } catch (Exception e) {
            log.error("创建用户失败: {}", e.getMessage(), e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    /**
     * 查询用户信息工具
     *
     * <p>Agent 可以通过此工具查询用户信息
     */
    @AgentTool(
        name = "base.getUser",
        description = "根据用户ID查询用户详细信息。返回用户的用户名、真实姓名、邮箱、手机号、状态等信息。",
        category = "base.user"
    )
    public Map<String, Object> getUser(Long userId) {
        log.info("Agent 调用查询用户工具: userId={}", userId);

        try {
            // 1. 调用服务
            UserVO userVO = userService.getUserById(userId);

            // 2. 返回结果
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("userId", userVO.getUserId());
            result.put("username", userVO.getUsername());
            result.put("realName", userVO.getRealName());
            result.put("email", userVO.getEmail());
            result.put("phone", userVO.getPhone());
            result.put("status", userVO.getStatus());
            result.put("tenantId", userVO.getTenantId());
            return result;

        } catch (Exception e) {
            log.error("查询用户失败: {}", e.getMessage(), e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    /**
     * 获取当前用户上下文信息工具
     *
     * <p>Agent 可以通过此工具获取当前登录用户的信息
     */
    @AgentTool(
        name = "base.getCurrentUserContext",
        description = "获取当前登录用户的上下文信息，包括用户ID、用户名、租户ID、角色和权限。",
        category = "base.context"
    )
    public Map<String, Object> getCurrentUserContext() {
        log.info("Agent 调用获取当前用户上下文工具");

        try {
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("userId", BaseContext.getUserId());
            result.put("username", BaseContext.getUsername());
            result.put("tenantId", BaseContext.getTenantId());

            // 获取角色列表
            String[] roles = BaseContext.getRoles();
            List<String> roleList = roles != null ? List.of(roles) : new ArrayList<>();
            result.put("roles", roleList);

            // 获取权限列表
            String[] permissions = BaseContext.getPermissions();
            List<String> permissionList = permissions != null ? List.of(permissions) : new ArrayList<>();
            result.put("permissions", permissionList);

            // 判断是否是超级管理员
            result.put("isSuperAdmin", BaseContext.isSuperAdmin());
            result.put("isTenantAdmin", BaseContext.isTenantAdmin());

            return result;

        } catch (Exception e) {
            log.error("获取当前用户上下文失败: {}", e.getMessage(), e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    /**
     * 检查权限工具
     *
     * <p>Agent 可以通过此工具检查当前用户是否拥有特定权限
     */
    @AgentTool(
        name = "base.hasPermission",
        description = "检查当前用户是否拥有指定权限。权限格式如：system:user:create、system:tenant:list 等。",
        category = "base.permission"
    )
    public Map<String, Object> hasPermission(String permission) {
        log.info("Agent 调用检查权限工具: permission={}", permission);

        try {
            boolean hasPermission = BaseContext.hasPermission(permission);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("hasPermission", hasPermission);
            result.put("permission", permission);
            result.put("message", hasPermission ? "拥有该权限" : "没有该权限");
            return result;

        } catch (Exception e) {
            log.error("检查权限失败: {}", e.getMessage(), e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    /**
     * 获取所有可用工具列表
     *
     * <p>返回 Base 模块所有可用的工具及其描述
     */
    @AgentTool(
        name = "base.listTools",
        description = "列出 Base 模块所有可用的工具及其描述，帮助了解可调用的功能。",
        category = "base.meta"
    )
    public Map<String, Object> listTools() {
        log.info("Agent 调用列出工具工具");

        List<Map<String, String>> tools = new ArrayList<>();

        // 创建用户工具
        tools.add(Map.of(
            "name", "base.createUser",
            "description", "创建新用户",
            "category", "base.user"
        ));

        // 查询用户工具
        tools.add(Map.of(
            "name", "base.getUser",
            "description", "查询用户详细信息",
            "category", "base.user"
        ));

        // 获取当前用户上下文工具
        tools.add(Map.of(
            "name", "base.getCurrentUserContext",
            "description", "获取当前登录用户的上下文信息",
            "category", "base.context"
        ));

        // 检查权限工具
        tools.add(Map.of(
            "name", "base.hasPermission",
            "description", "检查当前用户是否拥有指定权限",
            "category", "base.permission"
        ));

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("tools", tools);
        result.put("count", tools.size());
        return result;
    }
}
