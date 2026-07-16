package io.lumina.agent.rag;

import io.lumina.agent.config.RagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;

/**
 * PDF OCR 处理器
 *
 * <p>使用 PDFBox 将 PDF 每页渲染为图片，然后调用 {@link OcrProvider} 识别文字。
 * 用于扫描件/图片型 PDF（PDFTextStripper 提取为空时触发）。
 *
 * <p>处理流程：
 * <ol>
 *   <li>PDFBox {@link PDFRenderer} 将每页渲染为 PNG 图片（DPI 由配置控制）</li>
 *   <li>逐页调用 {@link OcrProvider#recognize} 识别文字</li>
 *   <li>拼接所有页面的文字返回</li>
 * </ol>
 *
 * @author Lumina Team
 * @since 3.3.1
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PdfOcrProcessor {

    private final OcrProvider ocrProvider;
    private final RagProperties ragProperties;

    /**
     * 对 PDF 文件执行 OCR 识别
     *
     * @param pdfFile PDF 文件路径
     * @return 识别出的全部文字（各页用 \n\n 分隔），OCR 未启用或失败返回空字符串
     */
    public String processPdf(Path pdfFile) {
        RagProperties.OcrConfig ocrConfig = ragProperties.getReader().getOcr();

        if (!ocrConfig.isEnabled()) {
            log.debug("OCR 未启用，跳过 PDF OCR 处理");
            return "";
        }

        if ("none".equals(ocrProvider.getName())) {
            log.debug("OCR Provider 为 none，跳过处理");
            return "";
        }

        int dpi = ocrConfig.getDpi() > 0 ? ocrConfig.getDpi() : 200;
        int maxPages = ocrConfig.getMaxPages() > 0 ? ocrConfig.getMaxPages() : 50;
        String language = ocrConfig.getLanguage() != null ? ocrConfig.getLanguage() : "chi_sim";

        log.info("开始 PDF OCR 处理: file={}, dpi={}, maxPages={}, provider={}",
                pdfFile.getFileName(), dpi, maxPages, ocrProvider.getName());

        try (org.apache.pdfbox.pdmodel.PDDocument document = Loader.loadPDF(pdfFile.toFile())) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pageCount = document.getNumberOfPages();
            int pagesToProcess = Math.min(pageCount, maxPages);

            if (pageCount > maxPages) {
                log.warn("PDF 页数 {} 超过最大限制 {}，仅处理前 {} 页", pageCount, maxPages, maxPages);
            }

            StringBuilder fullText = new StringBuilder();

            for (int i = 0; i < pagesToProcess; i++) {
                log.debug("OCR 处理第 {}/{} 页", i + 1, pagesToProcess);

                // 渲染页面为图片
                BufferedImage image = renderer.renderImageWithDPI(i, dpi, ImageType.RGB);

                // 转为 PNG 字节
                byte[] imageBytes;
                try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    ImageIO.write(image, "png", baos);
                    imageBytes = baos.toByteArray();
                }

                // 调用 OCR 识别
                String pageText = ocrProvider.recognize(imageBytes, language);
                if (pageText != null && !pageText.isBlank()) {
                    fullText.append(pageText.trim()).append("\n\n");
                }

                // 释放图片内存
                image.flush();
            }

            String result = fullText.toString().trim();
            log.info("PDF OCR 完成: pages={}/{}, textLength={}",
                    pagesToProcess, pageCount, result.length());
            return result;

        } catch (Exception e) {
            log.error("PDF OCR 处理失败: file={}, error={}", pdfFile.getFileName(), e.getMessage(), e);
            return "";
        }
    }
}
