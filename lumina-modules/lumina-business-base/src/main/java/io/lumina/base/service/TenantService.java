package io.lumina.base.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.lumina.base.api.dto.tenant.CreateTenantDTO;
import io.lumina.base.api.dto.tenant.TenantQueryDTO;
import io.lumina.base.api.dto.tenant.UpdateTenantDTO;
import io.lumina.base.api.vo.tenant.TenantVO;

/**
 * 租户服务
 */
public interface TenantService {
    Long createTenant(CreateTenantDTO dto);
    Boolean updateTenant(UpdateTenantDTO dto);
    Boolean deleteTenant(Long tenantId);
    TenantVO getTenantById(Long tenantId);
    Page<TenantVO> listTenants(TenantQueryDTO dto);
}
