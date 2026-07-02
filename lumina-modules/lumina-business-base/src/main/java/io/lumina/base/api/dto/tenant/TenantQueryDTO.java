package io.lumina.base.api.dto.tenant;

import io.lumina.common.core.BaseDTO;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
    @Min(value = 1, message = "页码不能小于 1")
    private Integer current = 1;

    /**
     * 每页大小
     */
    @Min(value = 1, message = "每页大小不能小于 1")
    @Max(value = 100, message = "每页大小不能超过 100")
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
