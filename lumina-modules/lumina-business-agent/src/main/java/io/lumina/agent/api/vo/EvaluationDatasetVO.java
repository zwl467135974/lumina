package io.lumina.agent.api.vo;

import io.lumina.agent.evaluation.model.EvaluationDataset;
import io.lumina.agent.evaluation.model.TestCase;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 评估数据集 VO
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationDatasetVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String description;
    private String agentType;
    private List<TestCase> cases;
    private String createTime;

    public static EvaluationDatasetVO from(EvaluationDataset dataset) {
        if (dataset == null) {
            return null;
        }
        return EvaluationDatasetVO.builder()
                .id(dataset.getId())
                .name(dataset.getName())
                .description(dataset.getDescription())
                .agentType(dataset.getAgentType())
                .cases(dataset.getCases())
                .createTime(dataset.getCreateTime())
                .build();
    }
}
