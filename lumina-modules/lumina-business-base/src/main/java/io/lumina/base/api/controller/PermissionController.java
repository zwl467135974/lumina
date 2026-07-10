package io.lumina.base.api.controller;

import io.lumina.base.api.dto.permission.CreatePermissionDTO;
import io.lumina.base.api.dto.permission.UpdatePermissionDTO;
import io.lumina.base.api.vo.permission.PermissionVO;
import io.lumina.base.annotation.RequirePermission;
import io.lumina.base.service.PermissionService;
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
 * 权限控制器
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Tag(name = "权限管理", description = "权限增删改查、权限树等接口")
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/base/permissions")
public class PermissionController {

    private final PermissionService permissionService;

    /**
     * 获取权限树
     */
    @GetMapping("/tree")
    @RequirePermission("system:permission:list")
    public R<List<PermissionVO>> getPermissionTree() {
        log.info("查询权限树");
        List<PermissionVO> tree = permissionService.getPermissionTree();
        return R.success(tree);
    }

    /**
     * 创建权限
     */
    @Audit(module = "permission", action = "CREATE")
    @PostMapping
    @RequirePermission("system:permission:create")
    public R<Long> createPermission(@Valid @RequestBody CreatePermissionDTO dto) {
        log.info("创建权限请求: permissionCode={}", dto.getPermissionCode());
        Long permissionId = permissionService.createPermission(dto);
        return R.success(permissionId);
    }

    /**
     * 更新权限
     */
    @Audit(module = "permission", action = "UPDATE")
    @PutMapping("/{permissionId}")
    @RequirePermission("system:permission:update")
    public R<Boolean> updatePermission(@PathVariable Long permissionId, @Valid @RequestBody UpdatePermissionDTO dto) {
        log.info("更新权限请求: permissionId={}", permissionId);
        dto.setPermissionId(permissionId);
        Boolean result = permissionService.updatePermission(dto);
        return R.success(result);
    }

    /**
     * 删除权限
     */
    @Audit(module = "permission", action = "DELETE")
    @DeleteMapping("/{permissionId}")
    @RequirePermission("system:permission:delete")
    public R<Boolean> deletePermission(@PathVariable Long permissionId) {
        log.info("删除权限请求: permissionId={}", permissionId);
        Boolean result = permissionService.deletePermission(permissionId);
        return R.success(result);
    }

    /**
     * 获取权限详情
     */
    @GetMapping("/{permissionId}")
    @RequirePermission("system:permission:query")
    public R<PermissionVO> getPermissionById(@PathVariable Long permissionId) {
        log.info("查询权限详情: permissionId={}", permissionId);
        PermissionVO permissionVO = permissionService.getPermissionById(permissionId);
        return R.success(permissionVO);
    }

    /**
     * 按类型查询权限
     */
    @GetMapping("/type/{permissionType}")
    @RequirePermission("system:permission:list")
    public R<List<PermissionVO>> listByType(@PathVariable Integer permissionType) {
        log.info("按类型查询权限: permissionType={}", permissionType);
        List<PermissionVO> list = permissionService.listByType(permissionType);
        return R.success(list);
    }
}
