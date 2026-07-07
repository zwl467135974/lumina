package io.lumina.agent.evaluation.scorer;

import io.lumina.agent.evaluation.model.ScoringMethod;
import io.lumina.agent.evaluation.model.TestCase;
import org.springframework.stereotype.Component;

/**
 * LLM-as-Judge 评分器的占位实现
 *
 * <p>当前委托包含评分器，后续接入 ChatModelFactory 调用 Judge 模型。
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Component
public class LlmJudgeScorer implements EvaluationScorer {

    private final ContainsScorer containsScorer;

    public LlmJudgeScorer(ContainsScorer containsScorer) {
        this.containsScorer = containsScorer;
    }

    @Override
    public ScoringMethod getMethod() {
        return ScoringMethod.LLM_JUDGE;
    }

    @Override
    public ScoreResult score(TestCase testCase, String actual) {
        ScoreResult result = containsScorer.score(testCase, actual);
        result.setDetail(result.getDetail() + "（LLM Judge 待接入）");
        return result;
    }
}
