package io.lumina.base.api.controller;

import io.lumina.base.infrastructure.entity.PermissionDO;
import io.lumina.base.infrastructure.mapper.PermissionMapper;
import io.lumina.common.core.BaseContext;
import io.lumina.common.core.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
@RestController
@RequestMapping("/api/v1/base/menus")
@Tag(name = "菜单管理", description = "动态菜单接口")
public class MenuController {

    @Autowired
    private PermissionMapper permissionMapper;

    /**
     * 获取当前用户的菜单树
     */
    @GetMapping
    @Operation(summary = "获取当前用户菜单", description = "从权限表查询菜单类型记录，按用户权限过滤")
    public R<List<MenuVO>> getCurrentUserMenus() {
        List<PermissionDO> allMenus = permissionMapper.selectAllPermissions().stream()
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
    private List<MenuVO> buildAndFilterMenuTree(List<PermissionDO> allMenus) {
        Map<Long, List<PermissionDO>> byParent = allMenus.stream()
                .collect(Collectors.groupingBy(PermissionDO::getParentId));

        return buildChildren(byParent, 0L);
    }

    /**
     * 递归构建子菜单（过滤无权限的节点）
     */
    private List<MenuVO> buildChildren(Map<Long, List<PermissionDO>> byParent, Long parentId) {
        List<PermissionDO> children = byParent.get(parentId);
        if (children == null || children.isEmpty()) {
            return List.of();
        }

        List<MenuVO> result = new ArrayList<>();
        for (PermissionDO perm : children) {
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
    private boolean hasPermission(PermissionDO perm) {
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
     * PermissionDO → MenuVO 转换
     */
    private MenuVO toMenuVO(PermissionDO perm) {
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
