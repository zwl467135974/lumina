package io.lumina.base.api.dto.tenant;

import io.lumina.common.core.BaseDTO;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 更新租户 DTO
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UpdateTenantDTO extends BaseDTO {

    /**
     * 租户ID
     */
    private Long tenantId;

    /**
     * 租户名称
     */
    @Size(max = 100, message = "租户名称长度不能超过100")
    private String tenantName;

    /**
     * 联系人
     */
    @Size(max = 50, message = "联系人长度不能超过50")
    private String contactName;

    /**
     * 联系电话
     */
    @Size(max = 20, message = "联系电话长度不能超过20")
    private String contactPhone;

    /**
     * 联系邮箱
     */
    @Size(max = 100, message = "联系邮箱长度不能超过100")
    private String contactEmail;

    /**
     * 状态（0-禁用，1-启用）
     */
    private Integer status;

    /**
     * 过期时间（NULL表示永久）
     */
    private LocalDateTime expireTime;
}
