package io.lumina.agent.evaluation.scorer;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 评分结果
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScoreResult {

    private double score;
    private String detail;
}
