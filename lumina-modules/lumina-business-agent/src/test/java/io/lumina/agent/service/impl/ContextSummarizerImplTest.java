package io.lumina.agent.service.impl;

import io.lumina.agent.config.LuminaAgentProperties;
import io.lumina.agent.manager.MemoryManager;
import io.lumina.agent.model.ChatModelFactory;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ContextSummarizerImpl 单元测试（不调 LLM 的前置检查路径）
 *
 * @author Lumina Team
 * @since 3.11.0
 */
class ContextSummarizerImplTest {

    @Test
    void summarizeCheckpointSkipsWhenRegionTooSmall() {
        ChatModelFactory factory = Mockito.mock(ChatModelFactory.class);
        LuminaAgentProperties props = new LuminaAgentProperties();
        ContextSummarizerImpl impl = new ContextSummarizerImpl(factory, props);

        // 区域 token（约 3）<= summaryMaxTokens（默认 500），压缩无收益
        List<MemoryManager.Memory> small = List.of(new MemoryManager.Memory("user", "短消息", 1L));

        assertThat(impl.summarizeCheckpoint(small, "系统提示词", null)).isNull();
        Mockito.verifyNoInteractions(factory);
    }

    @Test
    void summarizeCheckpointReturnsNullOnEmptyInput() {
        ChatModelFactory factory = Mockito.mock(ChatModelFactory.class);
        ContextSummarizerImpl impl = new ContextSummarizerImpl(factory, new LuminaAgentProperties());

        assertThat(impl.summarizeCheckpoint(List.of(), "sys", null)).isNull();
        assertThat(impl.summarizeCheckpoint(null, "sys", null)).isNull();
        Mockito.verifyNoInteractions(factory);
    }

    @Test
    void legacySummarizeReturnsEmptyOnEmptyInput() {
        ChatModelFactory factory = Mockito.mock(ChatModelFactory.class);
        ContextSummarizerImpl impl = new ContextSummarizerImpl(factory, new LuminaAgentProperties());

        assertThat(impl.summarize(List.of(), "Agent")).isEmpty();
        Mockito.verifyNoInteractions(factory);
    }
}
