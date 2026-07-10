package io.lumina.agent.api.vo;

import io.lumina.agent.infrastructure.entity.BudgetRuleDO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 预算规则 VO
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetRuleVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String ruleName;
    private String scopeType;
    private Long scopeId;
    private String periodType;
    private BigDecimal limitAmount;
    private Integer alertThreshold;
    private Integer status;
    private Long createBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static BudgetRuleVO from(BudgetRuleDO do_) {
        if (do_ == null) {
            return null;
        }
        return BudgetRuleVO.builder()
                .id(do_.getId())
                .ruleName(do_.getRuleName())
                .scopeType(do_.getScopeType())
                .scopeId(do_.getScopeId())
                .periodType(do_.getPeriodType())
                .limitAmount(do_.getLimitAmount())
                .alertThreshold(do_.getAlertThreshold())
                .status(do_.getStatus())
                .createBy(do_.getCreateBy())
                .createTime(do_.getCreateTime())
                .updateTime(do_.getUpdateTime())
                .build();
    }
}
