package io.lumina.base.api.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.lumina.base.api.dto.role.AssignPermissionDTO;
import io.lumina.base.api.dto.role.CreateRoleDTO;
import io.lumina.base.api.dto.role.RoleQueryDTO;
import io.lumina.base.api.dto.role.UpdateRoleDTO;
import io.lumina.base.api.vo.role.RoleVO;
import io.lumina.base.annotation.RequirePermission;
import io.lumina.base.service.RoleService;
import io.lumina.common.core.R;
import io.lumina.framework.audit.annotation.Audit;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Tag(name = "角色管理", description = "角色增删改查、权限分配等接口")
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/base/roles")
public class RoleController {

    private final RoleService roleService;

    /**
     * 创建角色
     */
    @Audit(module = "role", action = "CREATE")
    @PostMapping
    @RequirePermission("system:role:create")
    public R<Long> createRole(@Valid @RequestBody CreateRoleDTO dto) {
        log.info("创建角色请求: roleCode={}", dto.getRoleCode());
        Long roleId = roleService.createRole(dto);
        return R.success(roleId);
    }

    /**
     * 更新角色
     */
    @Audit(module = "role", action = "UPDATE")
    @PutMapping("/{roleId}")
    @RequirePermission("system:role:update")
    public R<Boolean> updateRole(@PathVariable Long roleId, @Valid @RequestBody UpdateRoleDTO dto) {
        log.info("更新角色请求: roleId={}", roleId);
        dto.setRoleId(roleId);
        Boolean result = roleService.updateRole(dto);
        return R.success(result);
    }

    /**
     * 删除角色
     */
    @Audit(module = "role", action = "DELETE")
    @DeleteMapping("/{roleId}")
    @RequirePermission("system:role:delete")
    public R<Boolean> deleteRole(@PathVariable Long roleId) {
        log.info("删除角色请求: roleId={}", roleId);
        Boolean result = roleService.deleteRole(roleId);
        return R.success(result);
    }

    /**
     * 获取角色详情
     */
    @GetMapping("/{roleId}")
    @RequirePermission("system:role:query")
    public R<RoleVO> getRoleById(@PathVariable Long roleId) {
        log.info("查询角色详情: roleId={}", roleId);
        RoleVO roleVO = roleService.getRoleById(roleId);
        return R.success(roleVO);
    }

    /**
     * 获取所有角色（不分页，用于用户分配角色等下拉选择场景）
     *
     * <p>注意：必须定义在 {@code /{roleId}} 之外的字面路径，否则 GET /roles/all 会被
     * /{roleId} 匹配，"all" 转 Long 失败触发「参数类型不匹配」。
     */
    @GetMapping("/all")
    @RequirePermission("system:role:list")
    public R<List<RoleVO>> listAllRoles(
            @RequestParam(defaultValue = "100") @jakarta.validation.constraints.Min(1) Integer size) {
        RoleQueryDTO dto = new RoleQueryDTO();
        dto.setCurrent(1);
        dto.setSize(Math.min(size, 1000));
        Page<RoleVO> page = roleService.listRoles(dto);
        return R.success(page.getRecords());
    }

    /**
     * 分页查询角色
     */
    @GetMapping
    @RequirePermission("system:role:list")
    public R<Page<RoleVO>> listRoles(@Valid RoleQueryDTO dto) {
        log.info("分页查询角色: current={}, size={}", dto.getCurrent(), dto.getSize());
        Page<RoleVO> page = roleService.listRoles(dto);
        return R.success(page);
    }

    /**
     * 分配权限
     */
    @PostMapping("/{roleId}/permissions")
    @RequirePermission("system:role:assign")
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
    @RequirePermission("system:role:query")
    public R<List<Long>> getRolePermissionIds(@PathVariable Long roleId) {
        log.info("查询角色权限: roleId={}", roleId);
        List<Long> permissionIds = roleService.getRolePermissionIds(roleId);
        return R.success(permissionIds);
    }
}
