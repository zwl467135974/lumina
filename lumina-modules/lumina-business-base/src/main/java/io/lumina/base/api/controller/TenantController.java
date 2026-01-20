package io.lumina.base.api.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.lumina.base.api.dto.tenant.CreateTenantDTO;
import io.lumina.base.api.dto.tenant.TenantQueryDTO;
import io.lumina.base.api.dto.tenant.UpdateTenantDTO;
import io.lumina.base.api.vo.tenant.TenantVO;
import io.lumina.base.service.TenantService;
import io.lumina.common.core.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

/**
 * 租户控制器
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/base/tenants")
public class TenantController {

    @Autowired
    private TenantService tenantService;

    /**
     * 创建租户
     */
    @PostMapping
    public R<Long> createTenant(@Valid @RequestBody CreateTenantDTO dto) {
        log.info("创建租户请求: tenantCode={}", dto.getTenantCode());
        Long tenantId = tenantService.createTenant(dto);
        return R.success(tenantId);
    }

    /**
     * 更新租户
     */
    @PutMapping("/{tenantId}")
    public R<Boolean> updateTenant(@PathVariable Long tenantId, @Valid @RequestBody UpdateTenantDTO dto) {
        log.info("更新租户请求: tenantId={}", tenantId);
        dto.setTenantId(tenantId);
        Boolean result = tenantService.updateTenant(dto);
        return R.success(result);
    }

    /**
     * 删除租户
     */
    @DeleteMapping("/{tenantId}")
    public R<Boolean> deleteTenant(@PathVariable Long tenantId) {
        log.info("删除租户请求: tenantId={}", tenantId);
        Boolean result = tenantService.deleteTenant(tenantId);
        return R.success(result);
    }

    /**
     * 获取租户详情
     */
    @GetMapping("/{tenantId}")
    public R<TenantVO> getTenantById(@PathVariable Long tenantId) {
        log.info("查询租户详情: tenantId={}", tenantId);
        TenantVO tenantVO = tenantService.getTenantById(tenantId);
        return R.success(tenantVO);
    }

    /**
     * 分页查询租户
     */
    @GetMapping
    public R<Page<TenantVO>> listTenants(@Valid TenantQueryDTO dto) {
        log.info("分页查询租户: current={}, size={}", dto.getCurrent(), dto.getSize());
        Page<TenantVO> page = tenantService.listTenants(dto);
        return R.success(page);
    }
}
