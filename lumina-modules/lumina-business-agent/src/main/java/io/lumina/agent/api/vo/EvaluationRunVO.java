package io.lumina.agent.api.vo;

import io.lumina.agent.infrastructure.entity.EvaluationRunDO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 评估运行记录 VO
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationRunVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long datasetId;
    private String datasetName;
    private Long agentId;
    private String agentType;
    private String modelName;
    private String provider;
    private String scoringMethod;
    private BigDecimal thresholdValue;
    private Integer totalCases;
    private Integer passedCases;
    private BigDecimal passRate;
    private BigDecimal avgScore;
    private Long avgLatencyMs;
    private Integer totalTokens;
    private String resultsJson;
    private String status;
    private Long createBy;
    private LocalDateTime createTime;

    public static EvaluationRunVO from(EvaluationRunDO do_) {
        if (do_ == null) {
            return null;
        }
        return EvaluationRunVO.builder()
                .id(do_.getId())
                .datasetId(do_.getDatasetId())
                .datasetName(do_.getDatasetName())
                .agentId(do_.getAgentId())
                .agentType(do_.getAgentType())
                .modelName(do_.getModelName())
                .provider(do_.getProvider())
                .scoringMethod(do_.getScoringMethod())
                .thresholdValue(do_.getThresholdValue())
                .totalCases(do_.getTotalCases())
                .passedCases(do_.getPassedCases())
                .passRate(do_.getPassRate())
                .avgScore(do_.getAvgScore())
                .avgLatencyMs(do_.getAvgLatencyMs())
                .totalTokens(do_.getTotalTokens())
                .resultsJson(do_.getResultsJson())
                .status(do_.getStatus())
                .createBy(do_.getCreateBy())
                .createTime(do_.getCreateTime())
                .build();
    }
}
