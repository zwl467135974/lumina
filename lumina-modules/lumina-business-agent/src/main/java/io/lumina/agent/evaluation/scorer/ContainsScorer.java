package io.lumina.agent.evaluation.scorer;

import io.lumina.agent.evaluation.model.ScoringMethod;
import io.lumina.agent.evaluation.model.TestCase;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 关键词包含评分器
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Component
public class ContainsScorer implements EvaluationScorer {

    @Override
    public ScoringMethod getMethod() {
        return ScoringMethod.CONTAINS;
    }

    @Override
    public ScoreResult score(TestCase testCase, String actual) {
        String expected = testCase.getExpected() == null ? "" : testCase.getExpected();
        String actualText = actual == null ? "" : actual;
        List<String> keywords = Arrays.stream(expected.split("[,，\\n]"))
                .map(String::trim)
                .filter(keyword -> !keyword.isEmpty())
                .toList();
        if (keywords.isEmpty()) {
            return new ScoreResult(0.0, "期望关键词为空");
        }
        long hitCount = keywords.stream().filter(actualText::contains).count();
        double score = (double) hitCount / keywords.size();
        return new ScoreResult(score, "命中关键词 " + hitCount + "/" + keywords.size());
    }
}
