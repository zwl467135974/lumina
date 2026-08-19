package io.lumina.agent.tool.spill;

import io.lumina.agent.config.LuminaAgentProperties;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ToolResultSpiller 单元测试
 *
 * @author Lumina Team
 * @since 3.11.0
 */
class ToolResultSpillerTest {

    @SuppressWarnings("unchecked")
    private ToolResultSpiller spiller(ToolArtifactStore store) {
        LuminaAgentProperties props = new LuminaAgentProperties();
        props.getTool().getSpill().setEnabled(true);
        props.getTool().getSpill().setThresholdChars(1000);
        props.getTool().getSpill().setHeadChars(300);
        props.getTool().getSpill().setTailChars(100);
        ObjectProvider<ToolArtifactStore> provider = Mockito.mock(ObjectProvider.class);
        Mockito.when(provider.getIfAvailable()).thenReturn(store);
        return new ToolResultSpiller(props, provider);
    }

    @Test
    void returnsOriginalWhenUnderThreshold() {
        ToolResultSpiller spiller = spiller(null);
        String content = "a".repeat(999);

        assertThat(spiller.spillIfNeeded("util.httpRequest", "conv-1", content)).isSameAs(content);
    }

    @Test
    void returnsOriginalWhenDisabled() {
        LuminaAgentProperties props = new LuminaAgentProperties();
        ObjectProvider<ToolArtifactStore> provider = Mockito.mock(ObjectProvider.class);
        ToolResultSpiller spiller = new ToolResultSpiller(props, provider);
        String content = "a".repeat(99999);

        assertThat(spiller.spillIfNeeded("util.httpRequest", "conv-1", content)).isSameAs(content);
    }

    @Test
    void spillsOversizedResultWithPreviewAndArtifactId() {
        ToolArtifactStore store = Mockito.mock(ToolArtifactStore.class);
        Mockito.when(store.save(Mockito.any(), Mockito.eq("util.httpRequest"), Mockito.any()))
                .thenReturn("abc123");
        ToolResultSpiller spiller = spiller(store);
        String content = "H".repeat(400) + "M".repeat(3000) + "T".repeat(400);

        String preview = spiller.spillIfNeeded("util.httpRequest", "conv-1", content);

        assertThat(preview).startsWith("H".repeat(300));
        assertThat(preview).endsWith("T".repeat(100));
        assertThat(preview).contains("artifactId=abc123");
        assertThat(preview).contains("已省略 3400 字符");
        // 预览必须在阈值内（含提示行）
        assertThat(preview.length()).isLessThanOrEqualTo(1000);
    }

    @Test
    void retrievalToolResultNeverSpillsToAvoidLoop() {
        ToolArtifactStore store = Mockito.mock(ToolArtifactStore.class);
        ToolResultSpiller spiller = spiller(store);
        String content = "x".repeat(5000);

        String truncated = spiller.spillIfNeeded(ToolResultSpiller.RETRIEVAL_TOOL_NAME, "conv-1", content);

        assertThat(truncated).startsWith("xxx");
        assertThat(truncated).contains("已截断");
        assertThat(truncated.length()).isLessThanOrEqualTo(400);
        Mockito.verifyNoInteractions(store);
    }

    @Test
    void degradesToHardTruncateWhenStoreFails() {
        ToolArtifactStore store = Mockito.mock(ToolArtifactStore.class);
        Mockito.when(store.save(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenThrow(new RuntimeException("DB 不可用"));
        ToolResultSpiller spiller = spiller(store);
        String content = "x".repeat(5000);

        String truncated = spiller.spillIfNeeded("util.httpRequest", "conv-1", content);

        assertThat(truncated).contains("已截断");
        assertThat(truncated.length()).isLessThanOrEqualTo(400);
    }
}
