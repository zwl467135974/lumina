package io.lumina.agent.util;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TokenEstimator 单元测试
 *
 * @author Lumina Team
 * @since 3.11.0
 */
class TokenEstimatorTest {

    // ==================== estimateTokens ====================

    @Test
    void estimateTokensReturnsZeroForNullAndEmpty() {
        assertThat(TokenEstimator.estimateTokens(null)).isZero();
        assertThat(TokenEstimator.estimateTokens("")).isZero();
    }

    @Test
    void estimateTokensCountsCjkAsOneTokenPerChar() {
        // 10 个汉字 ≈ 10 token
        assertThat(TokenEstimator.estimateTokens("字".repeat(10))).isEqualTo(10);
    }

    @Test
    void estimateTokensCountsOtherCharsAsFourPerToken() {
        // 8 个英文字符 ≈ 2 token
        assertThat(TokenEstimator.estimateTokens("abcdefgh")).isEqualTo(2);
    }

    @Test
    void estimateTokensMixedContent() {
        // 4 汉字 + 8 英文字符 = 4 + 2 = 6 token
        assertThat(TokenEstimator.estimateTokens("你好世界abcdefgh")).isEqualTo(6);
    }

    @Test
    void estimateTokensWhitespacesCounted() {
        // 4 空格 ≈ 1 token
        assertThat(TokenEstimator.estimateTokens("    ")).isEqualTo(1);
    }

    // ==================== countWithinBudget ====================

    @Test
    void countWithinBudgetFillsAllWhenUnderBudget() {
        // 每条约 10 token，预算 100 → 全部装入
        List<String> texts = Arrays.asList(generateCjk(10), generateCjk(10), generateCjk(10));
        assertThat(TokenEstimator.countWithinBudget(texts, 100)).isEqualTo(3);
    }

    @Test
    void countWithinBudgetStopsWhenExceeded() {
        // 每条约 30 token，预算 100 → 只能装 3 条（90），第 4 条会到 120 超预算
        List<String> texts = Arrays.asList(
                generateCjk(30), generateCjk(30), generateCjk(30), generateCjk(30));
        assertThat(TokenEstimator.countWithinBudget(texts, 100)).isEqualTo(3);
    }

    @Test
    void countWithinBudgetPrefersNewest() {
        // newest-first 列表：第一条（最新）10 token，第二条 500 token，预算 100
        // → 第一条装入，第二条超预算停止
        List<String> texts = Arrays.asList(generateCjk(10), generateCjk(500));
        assertThat(TokenEstimator.countWithinBudget(texts, 100)).isEqualTo(1);
    }

    @Test
    void countWithinBudgetReturnsZeroForNonPositiveBudget() {
        assertThat(TokenEstimator.countWithinBudget(Arrays.asList("a", "b"), 0)).isZero();
        assertThat(TokenEstimator.countWithinBudget(Arrays.asList("a", "b"), -1)).isZero();
    }

    @Test
    void countWithinBudgetHandlesEmptyList() {
        assertThat(TokenEstimator.countWithinBudget(Collections.emptyList(), 100)).isZero();
        assertThat(TokenEstimator.countWithinBudget(null, 100)).isZero();
    }

    @Test
    void countWithinBudgetSkipsAllWhenSingleMessageTooLarge() {
        // 单条消息超预算 → 0 条（宁可少带历史，绝不超窗）
        List<String> texts = List.of(generateCjk(1000));
        assertThat(TokenEstimator.countWithinBudget(texts, 100)).isZero();
    }

    private String generateCjk(int charCount) {
        return "字".repeat(charCount);
    }
}
