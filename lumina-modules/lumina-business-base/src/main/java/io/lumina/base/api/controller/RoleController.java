package io.lumina.base.api.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.lumina.base.api.dto.role.AssignPermissionDTO;
import io.lumina.base.api.dto.role.CreateRoleDTO;
import io.lumina.base.api.dto.role.RoleQueryDTO;
import io.lumina.base.api.dto.role.UpdateRoleDTO;
import io.lumina.base.api.vo.role.RoleVO;
import io.lumina.base.service.RoleService;
import io.lumina.common.core.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

/**
 * 角色控制器
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/base/roles")
public class RoleController {

    @Autowired
    private RoleService roleService;

    /**
     * 创建角色
     */
    @PostMapping
    public R<Long> createRole(@Valid @RequestBody CreateRoleDTO dto) {
        log.info("创建角色请求: roleCode={}", dto.getRoleCode());
        Long roleId = roleService.createRole(dto);
        return R.success(roleId);
    }

    /**
     * 更新角色
     */
    @PutMapping("/{roleId}")
    public R<Boolean> updateRole(@PathVariable Long roleId, @Valid @RequestBody UpdateRoleDTO dto) {
        log.info("更新角色请求: roleId={}", roleId);
        dto.setRoleId(roleId);
        Boolean result = roleService.updateRole(dto);
        return R.success(result);
    }

    /**
     * 删除角色
     */
    @DeleteMapping("/{roleId}")
    public R<Boolean> deleteRole(@PathVariable Long roleId) {
        log.info("删除角色请求: roleId={}", roleId);
        Boolean result = roleService.deleteRole(roleId);
        return R.success(result);
    }

    /**
     * 获取角色详情
     */
    @GetMapping("/{roleId}")
    public R<RoleVO> getRoleById(@PathVariable Long roleId) {
        log.info("查询角色详情: roleId={}", roleId);
        RoleVO roleVO = roleService.getRoleById(roleId);
        return R.success(roleVO);
    }

    /**
     * 分页查询角色
     */
    @GetMapping
    public R<Page<RoleVO>> listRoles(@Valid RoleQueryDTO dto) {
        log.info("分页查询角色: current={}, size={}", dto.getCurrent(), dto.getSize());
        Page<RoleVO> page = roleService.listRoles(dto);
        return R.success(page);
    }

    /**
     * 分配权限
     */
    @PostMapping("/{roleId}/permissions")
    public R<Boolean> assignPermissions(@PathVariable Long roleId, @Valid @RequestBody AssignPermissionDTO dto) {
        log.info("分配权限请求: roleId={}", roleId);
        dto.setRoleId(roleId);
        Boolean result = roleService.assignPermissions(dto);
        return R.success(result);
    }

    /**
     * 获取角色权限ID列表
     */
    @GetMapping("/{roleId}/permissions")
    public R<List<Long>> getRolePermissionIds(@PathVariable Long roleId) {
        log.info("查询角色权限: roleId={}", roleId);
        List<Long> permissionIds = roleService.getRolePermissionIds(roleId);
        return R.success(permissionIds);
    }
}
