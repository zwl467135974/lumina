package io.lumina.agent.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 模型价格配置 DTO
 *
 * @author Lumina Team
 * @since 3.6.0
 */
@Data
public class ModelPricingDTO {

    @NotBlank(message = "Provider 不能为空")
    @Size(max = 50, message = "Provider 不能超过 50 字符")
    private String provider;

    @NotBlank(message = "模型名称不能为空")
    @Size(max = 100, message = "模型名称不能超过 100 字符")
    private String modelName;

    @NotBlank(message = "输入价格不能为空")
    @DecimalMin(value = "0", message = "输入价格不能为负")
    private BigDecimal inputPrice;

    @NotBlank(message = "输出价格不能为空")
    @DecimalMin(value = "0", message = "输出价格不能为负")
    private BigDecimal outputPrice;

    private String currency;
    private Integer isActive;
}
