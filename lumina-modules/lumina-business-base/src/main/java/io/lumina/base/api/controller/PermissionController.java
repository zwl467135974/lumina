package io.lumina.base.api.controller;

import io.lumina.base.api.dto.permission.CreatePermissionDTO;
import io.lumina.base.api.dto.permission.UpdatePermissionDTO;
import io.lumina.base.api.vo.permission.PermissionVO;
import io.lumina.base.service.PermissionService;
import io.lumina.common.core.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/base/permissions")
public class PermissionController {

    @Autowired
    private PermissionService permissionService;

    /**
     * 获取权限树
     */
    @GetMapping("/tree")
    public R<List<PermissionVO>> getPermissionTree() {
        log.info("查询权限树");
        List<PermissionVO> tree = permissionService.getPermissionTree();
        return R.success(tree);
    }

    /**
     * 创建权限
     */
    @PostMapping
    public R<Long> createPermission(@Valid @RequestBody CreatePermissionDTO dto) {
        log.info("创建权限请求: permissionCode={}", dto.getPermissionCode());
        Long permissionId = permissionService.createPermission(dto);
        return R.success(permissionId);
    }

    /**
     * 更新权限
     */
    @PutMapping("/{permissionId}")
    public R<Boolean> updatePermission(@PathVariable Long permissionId, @Valid @RequestBody UpdatePermissionDTO dto) {
        log.info("更新权限请求: permissionId={}", permissionId);
        dto.setPermissionId(permissionId);
        Boolean result = permissionService.updatePermission(dto);
        return R.success(result);
    }

    /**
     * 删除权限
     */
    @DeleteMapping("/{permissionId}")
    public R<Boolean> deletePermission(@PathVariable Long permissionId) {
        log.info("删除权限请求: permissionId={}", permissionId);
        Boolean result = permissionService.deletePermission(permissionId);
        return R.success(result);
    }

    /**
     * 获取权限详情
     */
    @GetMapping("/{permissionId}")
    public R<PermissionVO> getPermissionById(@PathVariable Long permissionId) {
        log.info("查询权限详情: permissionId={}", permissionId);
        PermissionVO permissionVO = permissionService.getPermissionById(permissionId);
        return R.success(permissionVO);
    }

    /**
     * 按类型查询权限
     */
    @GetMapping("/type/{permissionType}")
    public R<List<PermissionVO>> listByType(@PathVariable Integer permissionType) {
        log.info("按类型查询权限: permissionType={}", permissionType);
        List<PermissionVO> list = permissionService.listByType(permissionType);
        return R.success(list);
    }
}
