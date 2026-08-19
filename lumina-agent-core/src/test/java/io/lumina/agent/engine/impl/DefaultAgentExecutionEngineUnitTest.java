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
