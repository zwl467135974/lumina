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
     */
    public static final int IMAGE_TOKEN_COST = 1000;

    private TokenEstimator() {
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
