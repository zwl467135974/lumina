package io.lumina.base.api.dto.tenant;

import io.lumina.common.core.BaseDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 租户查询 DTO
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TenantQueryDTO extends BaseDTO {

    /**
     * 租户编码（模糊查询）
     */
    private String tenantCode;

    /**
     * 租户名称（模糊查询）
     */
    private String tenantName;

    /**
     * 状态（0-禁用，1-启用）
     */
    private Integer status;

    /**
     * 当前页码
     */
    private Integer current = 1;

    /**
     * 每页大小
     */
    private Integer size = 10;

    /**
     * 排序字段
     */
    private String orderBy = "tenant_id";

    /**
     * 排序方向（asc/desc）
     */
    private String orderDirection = "desc";
}
