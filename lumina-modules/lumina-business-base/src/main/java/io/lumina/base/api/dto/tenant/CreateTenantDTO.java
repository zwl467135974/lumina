package io.lumina.base.api.dto.tenant;

import io.lumina.common.core.BaseDTO;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 创建租户 DTO
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CreateTenantDTO extends BaseDTO {

    /**
     * 租户编码（唯一标识）
     */
    @NotBlank(message = "租户编码不能为空")
    @Size(min = 2, max = 50, message = "租户编码长度必须在2-50之间")
    private String tenantCode;

    /**
     * 租户名称
     */
    @NotBlank(message = "租户名称不能为空")
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
}
