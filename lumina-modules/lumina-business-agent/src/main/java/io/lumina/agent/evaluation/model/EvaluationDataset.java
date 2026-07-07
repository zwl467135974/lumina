package io.lumina.agent.evaluation.model;

import lombok.Data;
import java.util.List;

/**
 * 评估数据集
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Data
public class EvaluationDataset {
    private Long id;
    private String name;
    private String description;
    private String agentType;
    private List<TestCase> cases;
    private Long tenantId;
    private String createTime;
}
