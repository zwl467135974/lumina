package io.lumina.agent.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MultimodalImage 单元测试（图片历史卸载的引用标记构建）
 *
 * @author Lumina Team
 * @since 3.13.0
 */
class MultimodalImageTest {

    @Test
    void referenceNoteEmptyWhenNoContents() {
        assertThat(MultimodalImage.buildReferenceNote(null)).isEmpty();
        assertThat(MultimodalImage.buildReferenceNote(List.of())).isEmpty();
    }

    @Test
    void referenceNoteSkipsDocuments() {
        String note = MultimodalImage.buildReferenceNote(
                List.of(MultimodalDocument.of("文档文本", "报告.pdf")));
        assertThat(note).isEmpty();
    }

    @Test
    void referenceNoteIncludesNameAndFileUuid() {
        String note = MultimodalImage.buildReferenceNote(List.of(
                new MultimodalImage("image/png", "aGk=", "uuid-123", "截图.png")));

        assertThat(note).startsWith("\n[本轮用户发送了 1 张图片: ");
        assertThat(note).contains("截图.png");
        assertThat(note).contains("fileUuid=uuid-123");
        assertThat(note).contains("不再附带图片本体");
        // 引用标记必须是轻量文本：绝不包含 Base64 本体
        assertThat(note).doesNotContain("aGk=");
    }

    @Test
    void referenceNoteDegradesGracefullyWithoutMetadata() {
        // 双参构造（旧调用方兼容）：无 fileUuid/文件名时退化为通用标记
        String note = MultimodalImage.buildReferenceNote(List.of(
                new MultimodalImage("image/png", "aGk=")));

        assertThat(note).contains("1 张图片");
        assertThat(note).contains("image");
        assertThat(note).doesNotContain("fileUuid=");
    }

    @Test
    void referenceNoteListsMultipleImages() {
        String note = MultimodalImage.buildReferenceNote(List.of(
                new MultimodalImage("image/png", "aGk=", "u1", "a.png"),
                new MultimodalImage("image/jpeg", "aGk=", "u2", "b.jpg")));

        assertThat(note).contains("2 张图片");
        assertThat(note).contains("a.png (fileUuid=u1)");
        assertThat(note).contains("b.jpg (fileUuid=u2)");
    }
}
