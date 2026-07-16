package io.lumina.agent.rag;

import io.lumina.agent.config.RagProperties;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * PdfOcrProcessor 单元测试
 *
 * <p>使用 PDFBox 实际创建 PDF 文件来验证渲染流程（不依赖 OCR 云服务）。
 * OCR Provider 用 Mockito mock，验证调用链路正确性。
 *
 * @author Lumina Team
 * @since 3.3.1
 */
class PdfOcrProcessorTest {

    @TempDir
    Path tempDir;

    @Test
    void ocrDisabledReturnsEmpty() throws IOException {
        RagProperties props = new RagProperties();
        props.getReader().getOcr().setEnabled(false);

        OcrProvider mockProvider = mock(OcrProvider.class);
        PdfOcrProcessor processor = new PdfOcrProcessor(mockProvider, props);

        Path pdfFile = createTextPdf("Hello World");
        String result = processor.processPdf(pdfFile);

        assertThat(result).isEmpty();
        verifyNoInteractions(mockProvider);
    }

    @Test
    void noopProviderReturnsEmpty() throws IOException {
        RagProperties props = new RagProperties();
        props.getReader().getOcr().setEnabled(true);

        OcrProvider noopProvider = new NoopOcrProvider();
        PdfOcrProcessor processor = new PdfOcrProcessor(noopProvider, props);

        Path pdfFile = createTextPdf("Hello World");
        String result = processor.processPdf(pdfFile);

        assertThat(result).isEmpty();
    }

    @Test
    void rendersPdfAndCallsOcrProvider() throws IOException {
        RagProperties props = new RagProperties();
        props.getReader().getOcr().setEnabled(true);
        props.getReader().getOcr().setDpi(72); // 低 DPI 加速测试
        props.getReader().getOcr().setMaxPages(5);

        // Mock OCR Provider 返回模拟文本
        OcrProvider mockProvider = mock(OcrProvider.class);
        when(mockProvider.getName()).thenReturn("mock");
        when(mockProvider.recognize(any(byte[].class), anyString()))
                .thenReturn("Mocked OCR Text");

        PdfOcrProcessor processor = new PdfOcrProcessor(mockProvider, props);

        // 创建 2 页 PDF
        Path pdfFile = createMultiPagePdf(2);
        String result = processor.processPdf(pdfFile);

        assertThat(result).contains("Mocked OCR Text");
        // 应该被调用 2 次（2 页）
        verify(mockProvider, times(2)).recognize(any(byte[].class), anyString());
    }

    @Test
    void maxPagesLimitIsRespected() throws IOException {
        RagProperties props = new RagProperties();
        props.getReader().getOcr().setEnabled(true);
        props.getReader().getOcr().setDpi(72);
        props.getReader().getOcr().setMaxPages(3); // 只处理 3 页

        OcrProvider mockProvider = mock(OcrProvider.class);
        when(mockProvider.getName()).thenReturn("mock");
        when(mockProvider.recognize(any(byte[].class), anyString())).thenReturn("text");

        PdfOcrProcessor processor = new PdfOcrProcessor(mockProvider, props);

        // 创建 5 页 PDF
        Path pdfFile = createMultiPagePdf(5);
        processor.processPdf(pdfFile);

        // 只应该处理 3 页
        verify(mockProvider, times(3)).recognize(any(byte[].class), anyString());
    }

    @Test
    void invalidPdfReturnsEmpty() {
        RagProperties props = new RagProperties();
        props.getReader().getOcr().setEnabled(true);

        OcrProvider mockProvider = mock(OcrProvider.class);
        when(mockProvider.getName()).thenReturn("mock");

        PdfOcrProcessor processor = new PdfOcrProcessor(mockProvider, props);

        // 不存在的文件
        String result = processor.processPdf(tempDir.resolve("nonexistent.pdf"));

        assertThat(result).isEmpty();
        // recognize 不应被调用（文件加载失败前不会渲染页面）
        verify(mockProvider, never()).recognize(any(byte[].class), anyString());
    }

    // ==================== PDF 创建辅助方法 ====================

    /**
     * 创建一个空白 PDF（模拟扫描件，无文本层）
     */
    private Path createTextPdf(String text) throws IOException {
        // 空白页 PDF 即可（OCR 路径不需要 PDF 有文本）
        return createMultiPagePdf(1);
    }

    /**
     * 创建一个 N 页空白 PDF（模拟扫描件，无文本层）
     */
    private Path createMultiPagePdf(int pages) throws IOException {
        Path file = tempDir.resolve("multipage.pdf");
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < pages; i++) {
                doc.addPage(new PDPage());
            }
            doc.save(file.toFile());
        }
        return file;
    }

    private static String anyString() {
        return org.mockito.ArgumentMatchers.anyString();
    }
}
