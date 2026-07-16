package io.lumina.agent.rag;

/**
 * OCR 文字识别 Provider 接口
 *
 * <p>接收图片字节数据（PNG/JPEG），返回识别出的文本。
 * 用于扫描件 PDF 的文字提取：PDFBox 将页面渲染为图片后调用本接口。
 *
 * <p>实现类通过 {@code @ConditionalOnProperty(prefix="lumina.rag.reader.ocr", name="provider")}
 * 按 provider 值自动装配，新增厂商只需加一个 {@code @Component} 文件。
 *
 * @author Lumina Team
 * @since 3.3.1
 */
public interface OcrProvider {

    /**
     * 识别图片中的文字
     *
     * @param imageBytes 图片字节数据（PNG/JPEG/TIFF）
     * @param language   识别语言提示（如 chi_sim / eng，Provider 可忽略）
     * @return 识别出的纯文本（失败返回空字符串，不抛异常）
     */
    String recognize(byte[] imageBytes, String language);

    /**
     * Provider 名称（用于日志标识）
     */
    String getName();
}
