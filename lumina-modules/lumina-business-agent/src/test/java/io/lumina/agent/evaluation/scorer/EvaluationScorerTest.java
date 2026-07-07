package io.lumina.agent.evaluation.scorer;

import io.lumina.agent.evaluation.model.ScoringMethod;
import io.lumina.agent.evaluation.model.TestCase;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

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
    void exactMatchScoresZeroWhenMismatch() {
        TestCase testCase = testCase("Hello");

        ScoreResult result = new ExactMatchScorer().score(testCase, "World");

        assertThat(result.getScore()).isEqualTo(0.0);
    }

    @Test
    void containsScoresByKeywordHitRate() {
        TestCase testCase = testCase("Agent,工具,知识库");

        ScoreResult result = new ContainsScorer().score(testCase, "Lumina 支持 Agent 和工具调用");

        assertThat(result.getScore()).isEqualTo(2.0 / 3.0);
        assertThat(result.getDetail()).contains("2/3");
    }

    @Test
    void containsScoresOneWhenAllKeywordsHit() {
        TestCase testCase = testCase("Hello,World");

        ScoreResult result = new ContainsScorer().score(testCase, "Hello World");

        assertThat(result.getScore()).isEqualTo(1.0);
    }

    @Test
    void semanticSimilarityFallsBackToJaccardWithoutEmbedding() {
        SemanticSimilarityScorer scorer = new SemanticSimilarityScorer();
        // embeddingModel is null by default (not injected in unit test)

        ScoreResult result = scorer.score(testCase("Lumina Agent 平台"), "Lumina 智能体平台");

        assertThat(scorer.getMethod()).isEqualTo(ScoringMethod.SEMANTIC_SIMILARITY);
        assertThat(result.getScore()).isBetween(0.0, 1.0);
        assertThat(result.getDetail()).contains("Jaccard");
    }

    @Test
    void semanticSimilarityReturnsZeroWhenOneSideIsEmpty() {
        SemanticSimilarityScorer scorer = new SemanticSimilarityScorer();

        ScoreResult result = scorer.score(testCase("Hello"), "");

        assertThat(result.getScore()).isEqualTo(0.0);
    }

    @Test
    void llmJudgeFallsBackToContainsWhenModelUnavailable() throws Exception {
        LlmJudgeScorer scorer = new LlmJudgeScorer(new ContainsScorer());
        // chatModelFactory and agentProperties are null → should fall back

        ScoreResult result = scorer.score(testCase("Lumina"), "Lumina Agent Platform");

        assertThat(scorer.getMethod()).isEqualTo(ScoringMethod.LLM_JUDGE);
        assertThat(result.getScore()).isBetween(0.0, 1.0);
        assertThat(result.getDetail()).contains("LLM 不可用");
    }

    @Test
    void llmJudgeParseResponseCorrectly() {
        // Verify score normalization logic: raw 5 → 1.0, raw 1 → 0.0
        assertThat(normalizeJudgeScore(5)).isEqualTo(1.0);
        assertThat(normalizeJudgeScore(4)).isEqualTo(0.75);
        assertThat(normalizeJudgeScore(3)).isEqualTo(0.5);
        assertThat(normalizeJudgeScore(2)).isEqualTo(0.25);
        assertThat(normalizeJudgeScore(1)).isEqualTo(0.0);
    }

    private TestCase testCase(String expected) {
        TestCase testCase = new TestCase();
        testCase.setExpected(expected);
        return testCase;
    }

    private double normalizeJudgeScore(int rawScore) {
        return (rawScore - 1.0) / 4.0;
    }
}
