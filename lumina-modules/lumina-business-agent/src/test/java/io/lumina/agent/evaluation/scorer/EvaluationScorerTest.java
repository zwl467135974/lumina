package io.lumina.agent.evaluation.scorer;

import io.lumina.agent.evaluation.model.ScoringMethod;
import io.lumina.agent.evaluation.model.TestCase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 评估评分器单元测试
 *
 * @author Lumina Team
 * @since 2.0.0
 */
class EvaluationScorerTest {

    @Test
    void exactMatchScoresOneWhenTextMatches() {
        TestCase testCase = testCase("Lumina");

        ScoreResult result = new ExactMatchScorer().score(testCase, " Lumina ");

        assertThat(result.getScore()).isEqualTo(1.0);
        assertThat(result.getDetail()).isEqualTo("完全匹配");
    }

    @Test
    void containsScoresByKeywordHitRate() {
        TestCase testCase = testCase("Agent,工具,知识库");

        ScoreResult result = new ContainsScorer().score(testCase, "Lumina 支持 Agent 和工具调用");

        assertThat(result.getScore()).isEqualTo(2.0 / 3.0);
        assertThat(result.getDetail()).contains("2/3");
    }

    @Test
    void semanticSimilarityReturnsValueBetweenZeroAndOne() {
        TestCase testCase = testCase("Lumina Agent 平台");

        ScoreResult result = new SemanticSimilarityScorer().score(testCase, "Lumina 智能体平台");

        assertThat(result.getScore()).isBetween(0.0, 1.0);
        assertThat(result.getDetail()).contains("Embedding");
    }

    @Test
    void llmJudgeDelegatesToContainsScorerForNow() {
        LlmJudgeScorer scorer = new LlmJudgeScorer(new ContainsScorer());

        ScoreResult result = scorer.score(testCase("Agent"), "Lumina Agent");

        assertThat(scorer.getMethod()).isEqualTo(ScoringMethod.LLM_JUDGE);
        assertThat(result.getScore()).isEqualTo(1.0);
        assertThat(result.getDetail()).contains("LLM Judge");
    }

    private TestCase testCase(String expected) {
        TestCase testCase = new TestCase();
        testCase.setExpected(expected);
        return testCase;
    }
}
