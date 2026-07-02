package io.lumina.base.api.controller;

import io.lumina.common.core.BaseContext;
import io.lumina.common.core.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 菜单 Controller（动态菜单，按用户权限过滤）
 *
 * @author Lumina Team
 * @since 1.2.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/base/menus")
@Tag(name = "菜单管理", description = "动态菜单接口")
public class MenuController {

    /**
     * 获取当前用户的菜单树
     */
    @GetMapping
    @Operation(summary = "获取当前用户菜单", description = "按用户权限过滤菜单树")
    public R<List<MenuVO>> getCurrentUserMenus() {
        List<MenuVO> allMenus = buildMenuTree();
        List<MenuVO> filtered = filterByPermissions(allMenus);
        log.debug("用户 {} 菜单加载完成: {} 项", BaseContext.getUsername(), filtered.size());
        return R.success(filtered);
    }

    /**
     * 构建完整菜单树
     */
    private List<MenuVO> buildMenuTree() {
        List<MenuVO> menus = new ArrayList<>();

        // Agent 管理
        MenuVO agent = new MenuVO();
        agent.setName("Agent");
        agent.setPath("/agent");
        agent.setTitle("Agent 管理");
        agent.setIcon("Agent");
        agent.setRedirect("/agent/list");
        agent.setChildren(Arrays.asList(
                child("AgentList", "/agent/list", "Agent 列表", null, true),
                child("AgentCreate", "/agent/create", "创建 Agent", "agent:create", false)
        ));
        menus.add(agent);

        // 知识库
        MenuVO knowledge = new MenuVO();
        knowledge.setName("Knowledge");
        knowledge.setPath("/knowledge");
        knowledge.setTitle("知识库");
        knowledge.setIcon("Document");
        menus.add(knowledge);

        // 系统管理
        MenuVO system = new MenuVO();
        system.setName("System");
        system.setPath("/system");
        system.setTitle("系统管理");
        system.setIcon("Setting");
        system.setRedirect("/system/user");
        system.setChildren(Arrays.asList(
                child("SystemUser", "/system/user", "用户管理", "system:user:list", false),
                child("SystemRole", "/system/role", "角色管理", "system:role:list", false),
                child("SystemPermission", "/system/permission", "权限管理", "system:permission:list", false),
                child("SystemTenant", "/system/tenant", "租户管理", "system:tenant:list", false)
        ));
        menus.add(system);

        // 监控中心
        MenuVO monitor = new MenuVO();
        monitor.setName("Monitor");
        monitor.setPath("/monitor");
        monitor.setTitle("监控中心");
        monitor.setIcon("Monitor");
        monitor.setRedirect("/monitor/tools");
        monitor.setChildren(List.of(
                child("MonitorTools", "/monitor/tools", "工具监控", null, false)
        ));
        menus.add(monitor);

        return menus;
    }

    private MenuVO child(String name, String path, String title, String permission, boolean keepAlive) {
        MenuVO m = new MenuVO();
        m.setName(name);
        m.setPath(path);
        m.setTitle(title);
        m.setPermission(permission);
        m.setKeepAlive(keepAlive);
        return m;
    }

    /**
     * 按用户权限递归过滤菜单（超管可见全部）
     */
    private List<MenuVO> filterByPermissions(List<MenuVO> menus) {
        if (menus == null) return List.of();
        if (BaseContext.isSuperAdmin()) return menus;

        List<MenuVO> result = new ArrayList<>();
        for (MenuVO menu : menus) {
            if (!hasPermission(menu)) {
                continue;
            }

            MenuVO copy = new MenuVO();
            copy.setName(menu.getName());
            copy.setPath(menu.getPath());
            copy.setTitle(menu.getTitle());
            copy.setIcon(menu.getIcon());
            copy.setRedirect(menu.getRedirect());
            copy.setPermission(menu.getPermission());
            copy.setKeepAlive(menu.getKeepAlive());

            if (menu.getChildren() != null && !menu.getChildren().isEmpty()) {
                List<MenuVO> filteredChildren = filterByPermissions(menu.getChildren());
                if (filteredChildren.isEmpty()) {
                    continue;
                }
                copy.setChildren(filteredChildren);
            }
            result.add(copy);
        }
        return result;
    }

    /**
     * 检查当前用户是否有该菜单的权限
     */
    private boolean hasPermission(MenuVO menu) {
        if (menu.getPermission() == null || menu.getPermission().isBlank()) {
            return true;
        }
        return BaseContext.hasPermission(menu.getPermission());
    }

    /**
     * 菜单 VO
     */
    @Data
    public static class MenuVO {
        private String name;
        private String path;
        private String title;
        private String icon;
        private String redirect;
        /** 权限标识（null=无需权限） */
        private String permission;
        /** 是否缓存 */
        private Boolean keepAlive;
        private List<MenuVO> children;
    }
}
