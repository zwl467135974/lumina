package io.lumina.base.api.dto.apitoken;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 校验 API Token DTO（Gateway 内部调用）
 *
 * @author Lumina Team
 * @since 3.4.0
 */
@Data
public class ValidateApiTokenDTO {

    /**
     * Token 明文（sk- 开头）
     */
    @NotBlank(message = "Token 不能为空")
    private String token;
}
