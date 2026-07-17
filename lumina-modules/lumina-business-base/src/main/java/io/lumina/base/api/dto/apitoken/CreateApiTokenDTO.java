package io.lumina.base.api.dto.apitoken;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建 API Token DTO
 *
 * @author Lumina Team
 * @since 3.4.0
 */
@Data
public class CreateApiTokenDTO {

    /**
     * Token 名称（用户自填，便于识别用途）
     */
    @NotBlank(message = "Token 名称不能为空")
    @Size(max = 64, message = "Token 名称不能超过 64 个字符")
    private String name;

    /**
     * 有效天数（null=永不过期）
     */
    @Min(value = 1, message = "有效天数最小为 1")
    @Max(value = 3650, message = "有效天数最大为 3650")
    private Integer expiresInDays;

    /**
     * 权限范围（逗号分隔，默认 agent:execute）
     */
    @Size(max = 256, message = "权限范围不能超过 256 个字符")
    private String scopes;
}
