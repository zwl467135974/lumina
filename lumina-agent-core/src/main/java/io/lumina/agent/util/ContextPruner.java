package io.lumina.agent.util;

/**
 * 上下文确定性修剪器（压缩管线第一级，免 LLM）
 *
 * <p>对超过阈值的历史消息做 head/tail 保留 + 中段省略标记，成本为零、无信息幻觉。
 * 在 LLM 检查点摘要（第二级）之前先行削减，借鉴 DeepSeek Harness 的
 * "能确定性解决的不过模型"原则。
 *
 * @author Lumina Team
 * @since 3.11.0
 */
public final class ContextPruner {

    private ContextPruner() {
    }

    /**
     * 修剪超长文本（不超阈值原样返回）
     *
     * @param content         原文
     * @param thresholdChars  触发修剪的长度阈值（字符数）
     * @param headKeepChars   保留头部字符数
     * @param tailKeepChars   保留尾部字符数
     * @return 修剪后文本（头 + 省略标记 + 尾）
     */
    public static String prune(String content, int thresholdChars, int headKeepChars, int tailKeepChars) {
        if (content == null || content.length() <= thresholdChars) {
            return content;
        }
        int head = Math.max(0, Math.min(headKeepChars, content.length()));
        int tail = Math.max(0, Math.min(tailKeepChars, content.length() - head));
        int omitted = content.length() - head - tail;
        return content.substring(0, head)
                + "\n\n[...内容过长，已省略 " + omitted + " 字符...]\n\n"
                + content.substring(content.length() - tail);
    }
}
