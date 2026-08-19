package io.lumina.agent.engine.impl;

import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.ChatUsage;
import io.lumina.agent.config.LuminaAgentProperties;
import io.lumina.agent.config.RagProperties;
import io.lumina.agent.loader.ConfigLoader;
import io.lumina.agent.loader.PromptLoader;
import io.lumina.agent.manager.EnhancedToolManager;
import io.lumina.agent.manager.MemoryManager;
import io.lumina.agent.model.ChatModelFactory;
import io.lumina.agent.model.ExecuteResult;
import io.lumina.agent.model.StreamChunk;
import io.lumina.agent.monitor.ToolCircuitBreaker;
import io.lumina.agent.monitor.ToolInvocationRecorder;
import io.lumina.agent.resilience.LlmResilienceWrapper;
import io.lumina.common.core.BaseContext;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DefaultAgentExecutionEngine 纯逻辑方法单元测试
 *
 * <p>覆盖 private 工具方法的转换与提取逻辑，不依赖 Spring 容器与 AgentScope 运行时。
 * 主执行路径（executeSyncInternal）依赖 createReActAgent 内部创建真实 ReActAgent，
 * 需要完整集成测试环境，不在本类覆盖。
 *
 * @author Lumina Team
 * @since 3.2.0
 */
class DefaultAgentExecutionEngineUnitTest {

    private DefaultAgentExecutionEngine engine;

    private MemoryManager memoryManager;

    private LuminaAgentProperties agentProperties;

    @BeforeEach
    void setUp() {
        memoryManager = Mockito.mock(MemoryManager.class);
        agentProperties = new LuminaAgentProperties();
        engine = new DefaultAgentExecutionEngine(
                Mockito.mock(ConfigLoader.class),
                Mockito.mock(PromptLoader.class),
                memoryManager,
                agentProperties,
                Mockito.mock(ChatModelFactory.class),
                Mockito.mock(LlmResilienceWrapper.class),
                Mockito.mock(ApplicationContext.class),
                Mockito.mock(EnhancedToolManager.class),
                Mockito.mock(ToolInvocationRecorder.class),
                Mockito.mock(ToolCircuitBreaker.class),
                Mockito.mock(MeterRegistry.class),
                Mockito.mock(io.agentscope.core.rag.Knowledge.class),
                Mockito.mock(RagProperties.class));
    }

    // ==================== toStreamChunk ====================

    @Test
    void toStreamChunkConvertsAgentResultEvent() throws Exception {
        Msg msg = Mockito.mock(Msg.class);
        Mockito.when(msg.getTextContent()).thenReturn("最终回复");
        Event event = new Event(EventType.AGENT_RESULT, msg, true);

        StreamChunk chunk = invokeToStreamChunk(event);

        assertThat(chunk.type()).isEqualTo("AGENT_RESULT");
        assertThat(chunk.content()).isEqualTo("最终回复");
        assertThat(chunk.last()).isTrue();
    }

    @Test
    void toStreamChunkConvertsReasoningEvent() throws Exception {
        Msg msg = Mockito.mock(Msg.class);
        Mockito.when(msg.getTextContent()).thenReturn("思考中...");
        Event event = new Event(EventType.REASONING, msg, false);

        StreamChunk chunk = invokeToStreamChunk(event);

        assertThat(chunk.type()).isEqualTo("REASONING");
        assertThat(chunk.content()).isEqualTo("思考中...");
        assertThat(chunk.last()).isFalse();
    }

    @Test
    void toStreamChunkHandlesNullMessage() throws Exception {
        Event event = new Event(EventType.REASONING, null, false);

        StreamChunk chunk = invokeToStreamChunk(event);

        assertThat(chunk.type()).isEqualTo("REASONING");
        assertThat(chunk.content()).isEmpty();
    }

    @Test
    void toStreamChunkHandlesNullType() throws Exception {
        Msg msg = Mockito.mock(Msg.class);
        Mockito.when(msg.getTextContent()).thenReturn("内容");
        Event event = new Event(null, msg, false);

        StreamChunk chunk = invokeToStreamChunk(event);

        // type 为 null 时回退为 "CHUNK"
        assertThat(chunk.type()).isEqualTo("CHUNK");
        assertThat(chunk.content()).isEqualTo("内容");
    }

    // ==================== extractTokenUsage ====================

    @Test
    void extractTokenUsageSuccess() throws Exception {
        Msg response = Mockito.mock(Msg.class);
        ChatUsage usage = Mockito.mock(ChatUsage.class);
        Mockito.when(usage.getInputTokens()).thenReturn(100);
        Mockito.when(usage.getOutputTokens()).thenReturn(50);
        Mockito.when(usage.getTotalTokens()).thenReturn(150);
        Mockito.when(response.getChatUsage()).thenReturn(usage);

        ExecuteResult.TokenUsage result = invokeExtractTokenUsage(response);

        assertThat(result).isNotNull();
        assertThat(result.getPromptTokens()).isEqualTo(100L);
        assertThat(result.getCompletionTokens()).isEqualTo(50L);
        assertThat(result.getTotalTokens()).isEqualTo(150L);
    }

    @Test
    void extractTokenUsageReturnsNullWhenNoUsage() throws Exception {
        Msg response = Mockito.mock(Msg.class);
        Mockito.when(response.getChatUsage()).thenReturn(null);

        ExecuteResult.TokenUsage result = invokeExtractTokenUsage(response);

        assertThat(result).isNull();
    }

    @Test
    void extractTokenUsageReturnsNullOnException() throws Exception {
        Msg response = Mockito.mock(Msg.class);
        Mockito.when(response.getChatUsage()).thenThrow(new RuntimeException("parse error"));

        ExecuteResult.TokenUsage result = invokeExtractTokenUsage(response);

        assertThat(result).isNull();
    }

    // ==================== buildContextMessages（Token 预算装填） ====================

    @Test
    void buildContextMessagesKeepsAllHistoryUnderBudget() throws Exception {
        agentProperties.getMemory().setContextWindowTokens(16000);
        Mockito.when(memoryManager.getRecentMemories("conv-1", 100))
                .thenReturn(buildMemories(30, 20));

        List<Msg> messages = invokeBuildContextMessages("conv-1", "当前问题");

        // 30 条历史（30*20=600 token << 预算）+ 1 条当前输入
        assertThat(messages).hasSize(31);
        assertThat(messages.get(0).getTextContent()).startsWith("消息0");
        assertThat(messages.get(29).getTextContent()).startsWith("消息29");
        assertThat(messages.get(30).getTextContent()).isEqualTo("当前问题");
    }

    @Test
    void buildContextMessagesDropsOldestWhenBudgetExceeded() throws Exception {
        // 预算 100，prompt 占 2，5 条历史每条 20 token → 只能装 4 条最新的
        agentProperties.getMemory().setContextWindowTokens(100);
        Mockito.when(memoryManager.getRecentMemories("conv-1", 100))
                .thenReturn(buildMemories(5, 20));

        List<Msg> messages = invokeBuildContextMessages("conv-1", "你好");

        // 4 条历史（消息1~消息4，最旧的消息0 被裁掉）+ 1 条当前输入
        assertThat(messages).hasSize(5);
        assertThat(messages.get(0).getTextContent()).startsWith("消息1");
        assertThat(messages.get(3).getTextContent()).startsWith("消息4");
    }

    @Test
    void buildContextMessagesFallsBackToCountWindowWhenBudgetDisabled() throws Exception {
        agentProperties.getMemory().setContextWindowTokens(0);
        Mockito.when(memoryManager.getRecentMemories("conv-1", 20))
                .thenReturn(buildMemories(20, 20).subList(0, 20));

        List<Msg> messages = invokeBuildContextMessages("conv-1", "当前问题");

        // 固定窗口 20 条 + 1 条当前输入
        assertThat(messages).hasSize(21);
    }

    private List<MemoryManager.Memory> buildMemories(int count, int cjkCharsPerMessage) {
        java.util.List<MemoryManager.Memory> memories = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            // "消息{i}" + 填充汉字，保证估算 token 可控（每条 = 2+数字位数 + cjkCharsPerMessage）
            String content = "消息" + i + "字".repeat(cjkCharsPerMessage);
            memories.add(new MemoryManager.Memory("user", content, System.currentTimeMillis() + i));
        }
        return memories;
    }

    @SuppressWarnings("unchecked")
    private List<Msg> invokeBuildContextMessages(String conversationId, String currentPrompt) throws Exception {
        Method method = DefaultAgentExecutionEngine.class.getDeclaredMethod(
                "buildContextMessages", String.class, String.class, List.class,
                io.lumina.agent.model.AgentConfig.class);
        method.setAccessible(true);
        return (List<Msg>) method.invoke(engine, conversationId, currentPrompt,
                java.util.Collections.emptyList(), (io.lumina.agent.model.AgentConfig) null);
    }

    // ==================== 上下文压缩（两级管线） ====================

    @Test
    void buildContextMessagesInjectsCheckpointSummaryForDroppedHistory() throws Exception {
        // 预算 100，prompt 占 2，5 条历史每条 20 token → 裁掉最旧 1 条，压缩为摘要
        agentProperties.getMemory().setContextWindowTokens(100);
        agentProperties.getMemory().getCompression().setEnabled(true);
        Mockito.when(memoryManager.getRecentMemories("conv-1", 100))
                .thenReturn(buildMemories(5, 20));
        io.lumina.agent.service.ContextSummarizer summarizer =
                Mockito.mock(io.lumina.agent.service.ContextSummarizer.class);
        Mockito.when(summarizer.summarizeCheckpoint(Mockito.anyList(), Mockito.any(), Mockito.any()))
                .thenReturn("检查点摘要");
        injectField("contextSummarizer", summarizer);

        List<Msg> messages = invokeBuildContextMessages("conv-1", "你好");

        // [摘要 SYSTEM] + 4 条保留历史 + 1 条当前输入
        assertThat(messages).hasSize(6);
        assertThat(messages.get(0).getTextContent()).startsWith("[对话历史摘要] 检查点摘要");
        assertThat(messages.get(1).getTextContent()).startsWith("消息1");
        Mockito.verify(summarizer).summarizeCheckpoint(
                Mockito.argThat(list -> list.size() == 1), Mockito.isNull(), Mockito.isNull());
    }

    @Test
    void buildContextMessagesDegradesToDropWhenSummaryUnavailable() throws Exception {
        agentProperties.getMemory().setContextWindowTokens(100);
        agentProperties.getMemory().getCompression().setEnabled(true);
        Mockito.when(memoryManager.getRecentMemories("conv-1", 100))
                .thenReturn(buildMemories(5, 20));
        io.lumina.agent.service.ContextSummarizer summarizer =
                Mockito.mock(io.lumina.agent.service.ContextSummarizer.class);
        Mockito.when(summarizer.summarizeCheckpoint(Mockito.anyList(), Mockito.any(), Mockito.any()))
                .thenReturn(null);
        injectField("contextSummarizer", summarizer);

        List<Msg> messages = invokeBuildContextMessages("conv-1", "你好");

        // 摘要不可用 -> 降级为丢弃，无摘要消息
        assertThat(messages).hasSize(5);
        assertThat(messages.get(0).getTextContent()).startsWith("消息1");
    }

    @Test
    void buildContextMessagesAppliesDeterministicPruneToOversizedHistory() throws Exception {
        // 单条历史 5000 字（超过默认 pruneThresholdChars=4000），预算充足也应被修剪
        agentProperties.getMemory().setContextWindowTokens(16000);
        Mockito.when(memoryManager.getRecentMemories("conv-1", 100))
                .thenReturn(List.of(new MemoryManager.Memory("user", "字".repeat(5000), 1L)));

        List<Msg> messages = invokeBuildContextMessages("conv-1", "当前问题");

        assertThat(messages).hasSize(2);
        String historyContent = messages.get(0).getTextContent();
        assertThat(historyContent).contains("已省略");
        assertThat(historyContent.length()).isLessThan(2500);
    }

    // ==================== 溢出恢复 ====================

    @Test
    void isContextOverflowMatchesProviderSignatures() throws Exception {
        Method method = DefaultAgentExecutionEngine.class.getDeclaredMethod("isContextOverflow", Throwable.class);
        method.setAccessible(true);

        assertThat((Boolean) method.invoke(engine,
                new RuntimeException("This model's maximum context length is 4096 tokens"))).isTrue();
        assertThat((Boolean) method.invoke(engine,
                new RuntimeException("prompt is too long: 200000 tokens > 128000 maximum"))).isTrue();
        assertThat((Boolean) method.invoke(engine,
                new RuntimeException("Connection timeout after 30000ms"))).isFalse();
        assertThat((Boolean) method.invoke(engine,
                new RuntimeException("nested", new RuntimeException("messages too long")))).isTrue();
    }

    @Test
    void emergencyCompactKeepsSystemHeadAndRecentTail() throws Exception {
        List<Msg> messages = new java.util.ArrayList<>();
        messages.add(Msg.builder().role(io.agentscope.core.message.MsgRole.SYSTEM).textContent("长期记忆").build());
        messages.add(Msg.builder().role(io.agentscope.core.message.MsgRole.USER).textContent("问题1").build());
        messages.add(Msg.builder().role(io.agentscope.core.message.MsgRole.ASSISTANT).textContent("回答1").build());
        messages.add(Msg.builder().role(io.agentscope.core.message.MsgRole.USER).textContent("问题2").build());
        messages.add(Msg.builder().role(io.agentscope.core.message.MsgRole.ASSISTANT).textContent("回答2").build());
        messages.add(Msg.builder().role(io.agentscope.core.message.MsgRole.USER).textContent("问题3").build());

        Method method = DefaultAgentExecutionEngine.class.getDeclaredMethod("emergencyCompact", List.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Msg> compacted = (List<Msg>) method.invoke(engine, messages);

        // [SYSTEM 长期记忆] + [SYSTEM 紧急压缩段] + 最近 2 条 = 4 条
        assertThat(compacted).hasSize(4);
        assertThat(compacted.get(0).getTextContent()).isEqualTo("长期记忆");
        assertThat(compacted.get(1).getTextContent()).contains("已紧急压缩").contains("问题1");
        assertThat(compacted.get(2).getTextContent()).isEqualTo("回答2");
        assertThat(compacted.get(3).getTextContent()).isEqualTo("问题3");
    }

    private void injectField(String name, Object value) throws Exception {
        java.lang.reflect.Field field = DefaultAgentExecutionEngine.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(engine, value);
    }

    // ==================== 反射辅助方法 ====================

    private StreamChunk invokeToStreamChunk(Event event) throws Exception {
        Method method = DefaultAgentExecutionEngine.class.getDeclaredMethod("toStreamChunk", Event.class);
        method.setAccessible(true);
        return (StreamChunk) method.invoke(engine, event);
    }

    private ExecuteResult.TokenUsage invokeExtractTokenUsage(Msg response) throws Exception {
        Method method = DefaultAgentExecutionEngine.class.getDeclaredMethod("extractTokenUsage", Msg.class);
        method.setAccessible(true);
        return (ExecuteResult.TokenUsage) method.invoke(engine, response);
    }
}
