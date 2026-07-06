package io.lumina.agent.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 预算规则创建/更新 DTO
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Data
public class BudgetRuleDTO {

    @NotBlank(message = "规则名称不能为空")
    private String ruleName;

    @NotBlank(message = "范围类型不能为空")
    private String scopeType;

    private Long scopeId;

    @NotBlank(message = "周期类型不能为空")
    private String periodType;

    @NotNull(message = "预算上限不能为空")
    @DecimalMin(value = "0.0001", message = "预算上限必须大于 0")
    private BigDecimal limitAmount;

    @Min(value = 1, message = "告警阈值必须在 1-100 之间")
    private Integer alertThreshold = 80;
}
