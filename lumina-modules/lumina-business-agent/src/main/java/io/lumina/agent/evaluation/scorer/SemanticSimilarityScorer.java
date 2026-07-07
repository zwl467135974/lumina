package io.lumina.agent.evaluation.scorer;

import io.agentscope.core.embedding.EmbeddingModel;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import io.lumina.agent.evaluation.model.ScoringMethod;
import io.lumina.agent.evaluation.model.TestCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 语义相似度评分器
 *
 * <p>当 RAG Embedding 模型可用时，使用向量余弦相似度；不可用时回退到字符级 Jaccard。
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Slf4j
@Component
public class SemanticSimilarityScorer implements EvaluationScorer {

    @Autowired(required = false)
    private EmbeddingModel embeddingModel;

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
        if (expected.isEmpty() || actualText.isEmpty()) {
            return new ScoreResult(0.0, "一方文本为空");
        }

        if (embeddingModel != null) {
            try {
                return scoreWithEmbedding(expected, actualText);
            } catch (Exception e) {
                log.warn("Embedding 评分失败，回退到字符相似度: {}", e.getMessage());
            }
        }

        return scoreWithJaccard(expected, actualText);
    }

    private ScoreResult scoreWithEmbedding(String expected, String actual) {
        double[] vecA = embed(expected);
        double[] vecB = embed(actual);
        double similarity = cosineSimilarity(vecA, vecB);
        return new ScoreResult(similarity, String.format("Embedding 余弦相似度 (%.4f)", similarity));
    }

    private double[] embed(String text) {
        ContentBlock block = TextBlock.builder().text(text).build();
        double[] vector = embeddingModel.embed(block).block();
        if (vector == null || vector.length == 0) {
            throw new RuntimeException("Embedding 返回空向量");
        }
        return vector;
    }

    private double cosineSimilarity(double[] a, double[] b) {
        double dotProduct = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denominator = Math.sqrt(normA) * Math.sqrt(normB);
        return denominator == 0 ? 0.0 : dotProduct / denominator;
    }

    private ScoreResult scoreWithJaccard(String expected, String actual) {
        long intersection = expected.chars().distinct().filter(ch -> actual.indexOf(ch) >= 0).count();
        long union = (expected + actual).chars().distinct().count();
        double score = union == 0 ? 0.0 : (double) intersection / union;
        return new ScoreResult(score, "字符级 Jaccard 相似度（Embedding 模型未启用）");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", "");
    }
}
