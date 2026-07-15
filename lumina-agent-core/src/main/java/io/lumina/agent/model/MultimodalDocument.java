package io.lumina.agent.model;

/**
 * 多模态文档内容（已提取的纯文本）
 *
 * <p>用于将 PDF、Word 等文档解析后的文本以 {@code TextBlock} 形式投递给 LLM。
 * 支持文档过长时截断（默认上限 {@value #MAX_TEXT_LENGTH} 字符）。
 *
 * @param text           文档提取的全文文本
 * @param sourceFileName 源文件名（用于日志和调试，不传给 LLM）
 * @author Lumina Team
 * @since 3.3.0
 */
public record MultimodalDocument(String text, String sourceFileName) implements MultimodalContent {

    /**
     * 文档文本最大长度（超过将截断，避免超出 LLM 上下文窗口）
     */
    public static final int MAX_TEXT_LENGTH = 50_000;

    /**
     * 截断文本（超长时截取前 {@link #MAX_TEXT_LENGTH} 字符并追加截断提示）
     *
     * @param text           原始文本
     * @param sourceFileName 源文件名
     * @return 截断后的 MultimodalDocument
     */
    public static MultimodalDocument of(String text, String sourceFileName) {
        if (text != null && text.length() > MAX_TEXT_LENGTH) {
            text = text.substring(0, MAX_TEXT_LENGTH) + "\n\n[... 文档过长，已截断，仅展示前 " + MAX_TEXT_LENGTH + " 字符 ...]";
        }
        return new MultimodalDocument(text, sourceFileName);
    }
}
