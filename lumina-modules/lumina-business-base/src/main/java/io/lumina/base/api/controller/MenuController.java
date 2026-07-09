package io.lumina.base.api.controller;

import io.lumina.base.annotation.RequirePermission;
import io.lumina.base.api.vo.permission.PermissionVO;
import io.lumina.base.service.PermissionService;
import io.lumina.common.core.BaseContext;
import io.lumina.common.core.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 菜单 Controller（动态菜单，从权限表查询 + 按用户权限过滤）
 *
 * <p>菜单数据来源于 {@code lumina_permission} 表中 {@code permission_type=1}（菜单类型）
 * 的记录。通过 parent_id 构建树形结构，按当前用户的权限进行过滤。
 *
 * <p>新增模块只需在权限表中插入菜单类型记录，无需修改本类。
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/base/menus")
@Tag(name = "菜单管理", description = "动态菜单接口")
public class MenuController {

    private final PermissionService permissionService;

    /**
     * 获取当前用户的菜单树
     */
    @GetMapping
    @RequirePermission("system:menu:list")
    @Operation(summary = "获取当前用户菜单", description = "从权限表查询菜单类型记录，按用户权限过滤")
    public R<List<MenuVO>> getCurrentUserMenus() {
        List<PermissionVO> allMenus = permissionService.listAllPermissions().stream()
                .filter(p -> p.getPermissionType() == 1)
                .filter(p -> p.getVisible() == 1)
                .filter(p -> p.getStatus() == 1)
                .toList();

        List<MenuVO> filtered = buildAndFilterMenuTree(allMenus);
        log.debug("用户 {} 菜单加载完成: {} 项", BaseContext.getUsername(), filtered.size());
        return R.success(filtered);
    }

    /**
     * 构建菜单树并按用户权限过滤
     */
    private List<MenuVO> buildAndFilterMenuTree(List<PermissionVO> allMenus) {
        Map<Long, List<PermissionVO>> byParent = allMenus.stream()
                .collect(Collectors.groupingBy(PermissionVO::getParentId));

        return buildChildren(byParent, 0L);
    }

    /**
     * 递归构建子菜单（过滤无权限的节点）
     */
    private List<MenuVO> buildChildren(Map<Long, List<PermissionVO>> byParent, Long parentId) {
        List<PermissionVO> children = byParent.get(parentId);
        if (children == null || children.isEmpty()) {
            return List.of();
        }

        List<MenuVO> result = new ArrayList<>();
        for (PermissionVO perm : children) {
            if (!hasPermission(perm)) {
                continue;
            }

            MenuVO menu = toMenuVO(perm);
            List<MenuVO> subChildren = buildChildren(byParent, perm.getPermissionId());
            if (!subChildren.isEmpty()) {
                menu.setChildren(subChildren);
            }
            if ((menu.getPath() == null || menu.getPath().isBlank()) && subChildren.isEmpty()) {
                continue;
            }
            result.add(menu);
        }
        return result;
    }

    /**
     * 检查当前用户是否有该菜单的权限
     */
    private boolean hasPermission(PermissionVO perm) {
        if (BaseContext.isSuperAdmin()) {
            return true;
        }
        String code = perm.getPermissionCode();
        if (code == null || code.isBlank()) {
            return true;
        }
        return BaseContext.hasPermission(code);
    }

    /**
     * PermissionVO → MenuVO 转换
     */
    private MenuVO toMenuVO(PermissionVO perm) {
        MenuVO menu = new MenuVO();
        menu.setName(perm.getPermissionCode());
        menu.setPath(perm.getPath() != null ? perm.getPath() : "");
        menu.setTitle(perm.getPermissionName());
        menu.setIcon(perm.getIcon());
        menu.setPermission(perm.getPermissionCode());
        menu.setComponent(perm.getComponent());
        menu.setKeepAlive(false);
        return menu;
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
        private String component;
        private String permission;
        private Boolean keepAlive;
        private List<MenuVO> children;
    }
}
