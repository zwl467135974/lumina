package io.lumina.base.api.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.lumina.base.api.dto.tenant.CreateTenantDTO;
import io.lumina.base.api.dto.tenant.TenantQueryDTO;
import io.lumina.base.api.dto.tenant.UpdateTenantDTO;
import io.lumina.base.api.vo.tenant.TenantVO;
import io.lumina.base.annotation.RequirePermission;
import io.lumina.base.service.TenantService;
import io.lumina.common.core.R;
import io.lumina.framework.audit.annotation.Audit;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

/**
 * 租户控制器
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Tag(name = "租户管理", description = "租户增删改查等接口")
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/base/tenants")
public class TenantController {

    private final TenantService tenantService;

    /**
     * 创建租户
     */
    @Audit(module = "tenant", action = "CREATE")
    @PostMapping
    @RequirePermission("system:tenant:create")
    public R<Long> createTenant(@Valid @RequestBody CreateTenantDTO dto) {
        log.info("创建租户请求: tenantCode={}", dto.getTenantCode());
        Long tenantId = tenantService.createTenant(dto);
        return R.success(tenantId);
    }

    /**
     * 更新租户
     */
    @Audit(module = "tenant", action = "UPDATE")
    @PutMapping("/{tenantId}")
    @RequirePermission("system:tenant:update")
    public R<Boolean> updateTenant(@PathVariable Long tenantId, @Valid @RequestBody UpdateTenantDTO dto) {
        log.info("更新租户请求: tenantId={}", tenantId);
        dto.setTenantId(tenantId);
        Boolean result = tenantService.updateTenant(dto);
        return R.success(result);
    }

    /**
     * 删除租户
     */
    @Audit(module = "tenant", action = "DELETE")
    @DeleteMapping("/{tenantId}")
    @RequirePermission("system:tenant:delete")
    public R<Boolean> deleteTenant(@PathVariable Long tenantId) {
        log.info("删除租户请求: tenantId={}", tenantId);
        Boolean result = tenantService.deleteTenant(tenantId);
        return R.success(result);
    }

    /**
     * 获取租户详情
     */
    @GetMapping("/{tenantId}")
    @RequirePermission("system:tenant:query")
    public R<TenantVO> getTenantById(@PathVariable Long tenantId) {
        log.info("查询租户详情: tenantId={}", tenantId);
        TenantVO tenantVO = tenantService.getTenantById(tenantId);
        return R.success(tenantVO);
    }

    /**
     * 分页查询租户
     */
    @GetMapping
    @RequirePermission("system:tenant:list")
    public R<Page<TenantVO>> listTenants(@Valid TenantQueryDTO dto) {
        log.info("分页查询租户: current={}, size={}", dto.getCurrent(), dto.getSize());
        Page<TenantVO> page = tenantService.listTenants(dto);
        return R.success(page);
    }
}
