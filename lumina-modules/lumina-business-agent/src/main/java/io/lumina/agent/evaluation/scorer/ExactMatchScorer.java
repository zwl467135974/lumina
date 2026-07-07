package io.lumina.agent.evaluation.scorer;

import io.lumina.agent.evaluation.model.ScoringMethod;
import io.lumina.agent.evaluation.model.TestCase;
import org.springframework.stereotype.Component;

/**
 * 精确匹配评分器
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Component
public class ExactMatchScorer implements EvaluationScorer {

    @Override
    public ScoringMethod getMethod() {
        return ScoringMethod.EXACT_MATCH;
    }

    @Override
    public ScoreResult score(TestCase testCase, String actual) {
        String expected = normalize(testCase.getExpected());
        String actualText = normalize(actual);
        boolean matched = expected.equals(actualText);
        return new ScoreResult(matched ? 1.0 : 0.0, matched ? "完全匹配" : "输出与期望不一致");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
