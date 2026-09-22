package io.lumina.agent.util;

import java.util.List;

/**
 * Token 估算器（输入侧上下文预算）
 *
 * <p>用于在发送请求前对上下文做启发式 token 计量，驱动历史消息按预算装填，
 * 替代固定条数窗口。估算规则：
 * <ul>
 *   <li>中日韩字符 ≈ 1 token/字（保守估计）</li>
 *   <li>其他字符 ≈ 4 字符/token（英文文本经验值）</li>
 * </ul>
 * 估算结果仅用于输入侧裁剪决策，不用于计费（计费以 LLM 返回的 usage 为准）。
 *
 * @author Lumina Team
 * @since 3.11.0
 */
public final class TokenEstimator {

    /**
     * 单张图片的保守 token 成本（多数 VLM 按分辨率计 800~1600，取中间值）
     *
     * <p>同时是 {@link #estimateImageTokens} 解析失败时的回退值。
     */
    public static final int IMAGE_TOKEN_COST = 1000;

    /** 尺寸感知估算的下限（对齐 VLM 最小分块） */
    private static final int IMAGE_TOKENS_MIN = 85;

    /** 尺寸感知估算的上限（4K 级大图，超出按此截断） */
    private static final int IMAGE_TOKENS_MAX = 4000;

    /** 每 token 覆盖的像素数（VLM 常见 28px/块量级的近似） */
    private static final double PIXELS_PER_TOKEN = 750.0;

    private TokenEstimator() {
    }

    /**
     * 按图片尺寸估算 token 成本（替代固定 {@link #IMAGE_TOKEN_COST}）
     *
     * <p>从 Base64 解码后的文件头解析宽高（PNG/JPEG/GIF），按
     * {@code ceil(宽*高/750)} 估算并夹在 [85, 4000]；头解析失败或数据
     * 非法时回退固定 {@link #IMAGE_TOKEN_COST}（宁可高估预算，不误判为 0）。
     *
     * @param base64 图片 Base64（不含 data URL 前缀）
     * @return 估算 token 数（>= 0；null/空输入返回 0）
     * @since 3.13.0
     */
    public static int estimateImageTokens(String base64) {
        if (base64 == null || base64.isEmpty()) {
            return 0;
        }
        byte[] bytes;
        try {
            bytes = java.util.Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            return IMAGE_TOKEN_COST;
        }
        long[] size = imageSize(bytes);
        if (size == null) {
            return IMAGE_TOKEN_COST;
        }
        long pixels = size[0] * size[1];
        if (pixels <= 0) {
            return IMAGE_TOKEN_COST;
        }
        long tokens = Math.round(pixels / PIXELS_PER_TOKEN);
        return (int) Math.max(IMAGE_TOKENS_MIN, Math.min(IMAGE_TOKENS_MAX, tokens));
    }

    /**
     * 解析图片宽高（支持 PNG / JPEG / GIF 文件头；不满足则返回 null）
     *
     * <p>PNG：IHDR 固定偏移 16（宽）与 20（高），大端；
     * GIF：偏移 6（宽）与 8（高），小端；
     * JPEG：遍历段找 SOF0/SOF2（0xFFC0/0xFFC2），高在段内偏移 5、宽在 7，大端。
     */
    static long[] imageSize(byte[] bytes) {
        if (bytes == null || bytes.length < 10) {
            return null;
        }
        // PNG: 89 50 4E 47
        if ((bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G'
                && bytes.length >= 24) {
            return new long[]{readUIntBE(bytes, 16), readUIntBE(bytes, 20)};
        }
        // GIF: GIF8
        if (bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == '8') {
            return new long[]{readUShortLE(bytes, 6), readUShortLE(bytes, 8)};
        }
        // JPEG: FF D8
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8) {
            int i = 2;
            while (i + 9 < bytes.length) {
                if ((bytes[i] & 0xFF) != 0xFF) {
                    return null;
                }
                int marker = bytes[i + 1] & 0xFF;
                // SOF0~SOF15 中排除 DHT(C4)/DAC(CC)/RST(D0-D7)
                if (marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                    int height = ((bytes[i + 5] & 0xFF) << 8) | (bytes[i + 6] & 0xFF);
                    int width = ((bytes[i + 7] & 0xFF) << 8) | (bytes[i + 8] & 0xFF);
                    return new long[]{width, height};
                }
                int segmentLength = ((bytes[i + 2] & 0xFF) << 8) | (bytes[i + 3] & 0xFF);
                if (segmentLength <= 0) {
                    return null;
                }
                i += 2 + segmentLength;
            }
        }
        return null;
    }

    private static long readUIntBE(byte[] bytes, int offset) {
        return ((long) (bytes[offset] & 0xFF) << 24) | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8) | (bytes[offset + 3] & 0xFF);
    }

    private static int readUShortLE(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) | ((bytes[offset + 1] & 0xFF) << 8);
    }

    /**
     * 估算文本 token 数
     *
     * @param text 文本（null 或空返回 0）
     * @return 估算 token 数（>= 0）
     */
    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int cjk = 0;
        int other = 0;
        for (int i = 0; i < text.length(); i++) {
            if (isCjk(text.charAt(i))) {
                cjk++;
            } else {
                other++;
            }
        }
        // 1 CJK 字符 ≈ 1 token；4 个其他字符 ≈ 1 token
        return (int) Math.ceil(cjk + other / 4.0);
    }

    /**
     * 从最新到最旧装填，返回能装进预算的条数
     *
     * <p>逐条累计，一旦超预算立即停止（宁可少带历史，绝不超窗）。
     * 预算 <= 0 时返回 0。
     *
     * @param textsNewestFirst 按新到旧排序的文本列表
     * @param budgetTokens     token 预算
     * @return 可装填的条数（0 ~ textsNewestFirst.size()）
     */
    public static int countWithinBudget(List<String> textsNewestFirst, int budgetTokens) {
        if (textsNewestFirst == null || textsNewestFirst.isEmpty() || budgetTokens <= 0) {
            return 0;
        }
        int used = 0;
        int count = 0;
        for (String text : textsNewestFirst) {
            int cost = estimateTokens(text);
            if (used + cost > budgetTokens) {
                break;
            }
            used += cost;
            count++;
        }
        return count;
    }

    private static boolean isCjk(char c) {
        Character.UnicodeScript script = Character.UnicodeScript.of(c);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }
}
