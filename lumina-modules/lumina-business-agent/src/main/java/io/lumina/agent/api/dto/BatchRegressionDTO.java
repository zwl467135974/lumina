package io.lumina.agent.api.dto;

import io.lumina.agent.evaluation.model.ScoringMethod;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 批量回归测试 DTO
 *
 * @author Lumina Team
 * @since 3.3.0
 */
@Data
public class BatchRegressionDTO {

    /** 要跑回归的数据集 ID 列表 */
    @NotEmpty(message = "数据集列表不能为空")
    private List<Long> datasetIds;

    /** 被测 Agent ID */
    @NotNull(message = "Agent ID 不能为空")
    private Long agentId;

    private ScoringMethod scoringMethod = ScoringMethod.EXACT_MATCH;
    private Double threshold = 0.7;

    /** 被测 Prompt 名称（可选） */
    private String promptName;

    /** 被测 Prompt 版本（可选） */
    private Integer promptVersion;

    /** 基线 run ID（可选，用于自动对比） */
    private Long baselineRunId;
}
