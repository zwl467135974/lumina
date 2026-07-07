package io.lumina.agent.evaluation.scorer;

import io.lumina.agent.evaluation.model.ScoringMethod;
import io.lumina.agent.evaluation.model.TestCase;
import org.springframework.stereotype.Component;

/**
 * 语义相似度评分器的占位实现
 *
 * <p>当前先使用字符级 Jaccard 相似度形成可运行闭环，后续可替换为 Embedding 余弦相似度。
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Component
public class SemanticSimilarityScorer implements EvaluationScorer {

    @Override
    public ScoringMethod getMethod() {
        return ScoringMethod.SEMANTIC_SIMILARITY;
    }

    @Override
    public ScoreResult score(TestCase testCase, String actual) {
        String expected = normalize(testCase.getExpected());
        String actualText = normalize(actual);
        if (expected.isEmpty() && actualText.isEmpty()) {
            return new ScoreResult(1.0, "文本均为空");
        }
        long intersection = expected.chars().distinct().filter(ch -> actualText.indexOf(ch) >= 0).count();
        long union = (expected + actualText).chars().distinct().count();
        double score = union == 0 ? 0.0 : (double) intersection / union;
        return new ScoreResult(score, "字符级相似度（Embedding 评分待接入）");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", "");
    }
}
