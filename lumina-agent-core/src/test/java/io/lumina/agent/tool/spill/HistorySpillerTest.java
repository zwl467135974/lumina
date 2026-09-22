package io.lumina.agent.tool.spill;

import io.lumina.agent.config.LuminaAgentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 历史消息外存化器（v3.13 批次 2.3）单元测试
 *
 * <p>核心语义：超阈值历史消息存档留预览 + 取回标记；<b>一切降级路径都
 * 保留原文</b>（历史宁可贵不失真——与工具结果 spill 的硬截断降级刻意不同）。
 *
 * @author Lumina Team
 * @since 3.13.0
 */
class HistorySpillerTest {

    private LuminaAgentProperties properties;
    private ToolArtifactStore artifactStore;
    private HistorySpiller spiller;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        properties = new LuminaAgentProperties();
        properties.getTool().setSpill(new LuminaAgentProperties.SpillConfig());
        properties.getTool().getSpill().setEnabled(true);
        properties.getTool().getSpill().setHistoryEnabled(true);
        // 阈值调小便于构造用例
        properties.getTool().getSpill().setHistoryThresholdChars(100);
        properties.getTool().getSpill().setHistoryHeadChars(20);
        properties.getTool().getSpill().setHistoryTailChars(10);

        artifactStore = mock(ToolArtifactStore.class);
        ObjectProvider<ToolArtifactStore> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(artifactStore);
        spiller = new HistorySpiller(properties, provider);
    }

    private String text(int length) {
        return "字".repeat(length);
    }

    @Test
    void shortContentPassesThroughUnchanged() {
        assertThat(spiller.spillIfNeeded("user", "conv-1", text(50))).isEqualTo(text(50));
    }

    @Test
    void longContentReplacedWithPreviewAndRetrievalMarker() {
        when(artifactStore.save(anyString(), anyString(), anyString())).thenReturn("art-123");

        String preview = spiller.spillIfNeeded("user", "conv-1", text(500));

        assertThat(preview).startsWith("字".repeat(20));
        assertThat(preview).endsWith("字".repeat(10));
        assertThat(preview).contains("历史消息过长");
        assertThat(preview).contains("artifactId=art-123");
        assertThat(preview).contains("util.getArtifact");
        // 预览 = head + tail + 固定标记文案（约 70 字符），远小于原文
        assertThat(preview.length()).isLessThan(150);
        assertThat(preview.length()).isLessThan(500);
    }

    @Test
    void archiveFailureDegradesToOriginalNeverTruncates() {
        when(artifactStore.save(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("存储不可用"));

        // 历史语义：存档失败保留原文（对比工具结果 spill 降级硬截断）
        assertThat(spiller.spillIfNeeded("assistant", "conv-1", text(500))).isEqualTo(text(500));
    }

    @Test
    void disabledSwitchesKeepOriginal() {
        properties.getTool().getSpill().setHistoryEnabled(false);
        assertThat(spiller.spillIfNeeded("user", "conv-1", text(500))).isEqualTo(text(500));

        properties.getTool().getSpill().setHistoryEnabled(true);
        properties.getTool().getSpill().setEnabled(false);
        assertThat(spiller.spillIfNeeded("user", "conv-1", text(500))).isEqualTo(text(500));
    }

    @Test
    void nullConversationIdKeepsOriginal() {
        // 无会话（单轮无状态）：不外存，原样返回
        assertThat(spiller.spillIfNeeded("user", null, text(500))).isEqualTo(text(500));
    }

    @Test
    @SuppressWarnings("unchecked")
    void missingArtifactStoreKeepsOriginal() {
        ObjectProvider<ToolArtifactStore> emptyProvider = mock(ObjectProvider.class);
        HistorySpiller noStore = new HistorySpiller(properties, emptyProvider);

        assertThat(noStore.spillIfNeeded("user", "conv-1", text(500))).isEqualTo(text(500));
    }

    @Test
    void sourceTagCarriesRoleForAuditTrail() {
        when(artifactStore.save(anyString(), anyString(), anyString())).thenReturn("art-9");

        spiller.spillIfNeeded("user", "conv-1", text(500));

        org.mockito.Mockito.verify(artifactStore)
                .save(org.mockito.ArgumentMatchers.eq("conv-1"),
                      org.mockito.ArgumentMatchers.eq("history.user"),
                      org.mockito.ArgumentMatchers.eq(text(500)));
    }
}
