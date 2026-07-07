package io.lumina.agent.evaluation.scorer;

import io.lumina.agent.evaluation.model.ScoringMethod;
import io.lumina.agent.evaluation.model.TestCase;

/**
 * Agent 评估评分器
 *
 * @author Lumina Team
 * @since 2.0.0
 */
public interface EvaluationScorer {

    /**
     * 获取支持的评分方法
     */
    ScoringMethod getMethod();

    /**
     * 对单条用例输出评分，分值范围为 0-1。
     */
    ScoreResult score(TestCase testCase, String actual);
}
