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

    // ==================== estimateImageTokens（尺寸感知） ====================

    @Test
    void estimateImageTokensReturnsZeroForNullAndEmpty() {
        assertThat(TokenEstimator.estimateImageTokens(null)).isZero();
        assertThat(TokenEstimator.estimateImageTokens("")).isZero();
    }

    @Test
    void estimateImageTokensParsesPngDimensions() {
        // PNG 头 + IHDR 声明 800x600 → 480000/750 = 640 token
        assertThat(TokenEstimator.estimateImageTokens(pngBase64(800, 600))).isEqualTo(640);
    }

    @Test
    void estimateImageTokensParsesJpegDimensions() {
        // JPEG SOF0 声明 1400x1050 → 1470000/750 = 1960 token
        assertThat(TokenEstimator.estimateImageTokens(jpegBase64(1400, 1050))).isEqualTo(1960);
    }

    @Test
    void estimateImageTokensParsesGifDimensions() {
        // GIF 头声明 200x100 → 20000/750 ≈ 27 → 夹到下限 85
        assertThat(TokenEstimator.estimateImageTokens(gifBase64(200, 100)))
                .isEqualTo(85);
    }

    @Test
    void estimateImageTokensClampsLargeImages() {
        // 8000x8000 → 85333 → 夹到上限 4000
        assertThat(TokenEstimator.estimateImageTokens(pngBase64(8000, 8000))).isEqualTo(4000);
    }

    @Test
    void estimateImageTokensFallsBackOnUnrecognizableData() {
        // 非 Base64 / 非图片头 → 回退固定成本（绝不误判为 0）
        assertThat(TokenEstimator.estimateImageTokens("!!!not-base64!!!"))
                .isEqualTo(TokenEstimator.IMAGE_TOKEN_COST);
        assertThat(TokenEstimator.estimateImageTokens(
                java.util.Base64.getEncoder().encodeToString("plain text data".getBytes())))
                .isEqualTo(TokenEstimator.IMAGE_TOKEN_COST);
    }

    /** 构造声明指定尺寸的最小 PNG Base64（8 字节签名 + IHDR 块头 + 宽高） */
    private String pngBase64(int width, int height) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try {
            out.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
            out.write(new byte[]{0, 0, 0, 13});              // IHDR 长度
            out.write(new byte[]{'I', 'H', 'D', 'R'});       // 类型
            out.write(intToBytes(width));                    // 宽（偏移 16 起）
            out.write(intToBytes(height));                   // 高（偏移 20 起）
            out.write(new byte[5]);                          // 位深/颜色类型等占位
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        return java.util.Base64.getEncoder().encodeToString(out.toByteArray());
    }

    /** 构造声明指定尺寸的最小 JPEG Base64（SOI + APP0 段 + SOF0 段） */
    private String jpegBase64(int width, int height) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try {
            out.write(new byte[]{(byte) 0xFF, (byte) 0xD8});            // SOI
            out.write(new byte[]{(byte) 0xFF, (byte) 0xE0, 0, 2});      // APP0 段（长度 2 = 无载荷）
            out.write(new byte[]{(byte) 0xFF, (byte) 0xC0, 0, 8, 8});   // SOF0：段长 8、精度
            out.write(intToShortBytes(height));                          // 高（段内偏移 5）
            out.write(intToShortBytes(width));                           // 宽（段内偏移 7）
            out.write(new byte[]{3});                                    // 分量数占位
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        return java.util.Base64.getEncoder().encodeToString(out.toByteArray());
    }

    /** 构造声明指定尺寸的最小 GIF Base64 */
    private String gifBase64(int width, int height) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try {
            out.write(new byte[]{'G', 'I', 'F', '8', '9', 'a'});
            out.write(intToShortLEBytes(width));
            out.write(intToShortLEBytes(height));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        return java.util.Base64.getEncoder().encodeToString(out.toByteArray());
    }

    private byte[] intToBytes(int value) {
        return new byte[]{(byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value};
    }

    private byte[] intToShortBytes(int value) {
        return new byte[]{(byte) (value >>> 8), (byte) value};
    }

    private byte[] intToShortLEBytes(int value) {
        return new byte[]{(byte) value, (byte) (value >>> 8)};
    }

    private String generateCjk(int charCount) {
        return "字".repeat(charCount);
    }
}
