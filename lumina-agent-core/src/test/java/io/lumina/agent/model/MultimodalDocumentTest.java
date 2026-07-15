package io.lumina.agent.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MultimodalDocument 单元测试
 *
 * <p>验证文本截断逻辑和接口兼容性。
 *
 * @author Lumina Team
 * @since 3.3.0
 */
class MultimodalDocumentTest {

    @Test
    void shortTextPreservedAsIs() {
        MultimodalDocument doc = MultimodalDocument.of("短文本", "report.pdf");
        assertThat(doc.text()).isEqualTo("短文本");
        assertThat(doc.sourceFileName()).isEqualTo("report.pdf");
    }

    @Test
    void longTextTruncated() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < MultimodalDocument.MAX_TEXT_LENGTH + 1000; i++) {
            sb.append("a");
        }
        String longText = sb.toString();

        MultimodalDocument doc = MultimodalDocument.of(longText, "big.pdf");
        assertThat(doc.text()).hasSizeLessThan(longText.length());
        assertThat(doc.text()).contains("已截断");
        assertThat(doc.text().length()).isLessThanOrEqualTo(MultimodalDocument.MAX_TEXT_LENGTH + 100);
    }

    @Test
    void exactlyAtMaxLengthNotTruncated() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < MultimodalDocument.MAX_TEXT_LENGTH; i++) {
            sb.append("x");
        }
        MultimodalDocument doc = MultimodalDocument.of(sb.toString(), "edge.pdf");
        assertThat(doc.text()).hasSize(MultimodalDocument.MAX_TEXT_LENGTH);
        assertThat(doc.text()).doesNotContain("已截断");
    }

    @Test
    void nullFileNameAllowed() {
        MultimodalDocument doc = MultimodalDocument.of("content", null);
        assertThat(doc.text()).isEqualTo("content");
        assertThat(doc.sourceFileName()).isNull();
    }

    @Test
    void implementsMultimodalContent() {
        MultimodalDocument doc = new MultimodalDocument("text", "file.pdf");
        assertThat(doc).isInstanceOf(MultimodalContent.class);
    }

    @Test
    void multimodalImageAlsoImplementsContent() {
        MultimodalImage img = new MultimodalImage("image/png", "base64data");
        assertThat(img).isInstanceOf(MultimodalContent.class);
    }
}
