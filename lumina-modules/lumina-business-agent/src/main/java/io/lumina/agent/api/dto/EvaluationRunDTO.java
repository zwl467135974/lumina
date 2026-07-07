package io.lumina.agent.api.dto;

import io.lumina.agent.evaluation.model.ScoringMethod;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 执行评估请求 DTO
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Data
public class EvaluationRunDTO {

    @NotNull(message = "Agent ID 不能为空")
    private Long agentId;

    private ScoringMethod scoringMethod = ScoringMethod.EXACT_MATCH;
    private Double threshold = 0.7;
}
