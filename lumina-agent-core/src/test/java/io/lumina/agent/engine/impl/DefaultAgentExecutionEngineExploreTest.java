package io.lumina.agent.engine.impl;

import io.lumina.agent.config.LuminaAgentProperties;
import io.lumina.agent.config.RagProperties;
import io.lumina.agent.loader.ConfigLoader;
import io.lumina.agent.loader.PromptLoader;
import io.lumina.agent.manager.EnhancedToolManager;
import io.lumina.agent.manager.MemoryManager;
import io.lumina.agent.model.AgentConfig;
import io.lumina.agent.model.ChatModelFactory;
import io.lumina.agent.monitor.ToolCircuitBreaker;
import io.lumina.agent.monitor.ToolInvocationRecorder;
import io.lumina.agent.resilience.LlmResilienceWrapper;
import io.lumina.agent.steering.InMemorySteeringMessageStore;
import io.lumina.agent.tool.ToolDefinition;
import io.lumina.agent.tool.security.ReadOnlyToolClassifier;
import io.lumina.agent.tool.security.ToolExecutionContext;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.contains;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 引擎层的只读探索子代理与运行中转向接线测试（v3.14 批次 3.2）
 *
 * @author Lumina Team
 * @since 3.14.0
 */
class DefaultAgentExecutionEngineExploreTest {

    private DefaultAgentExecutionEngine engine;
    private PromptLoader promptLoader;
    private LuminaAgentProperties agentProperties;
    private ReadOnlyToolClassifier classifier;
    private InMemorySteeringMessageStore steeringStore;

    @BeforeEach
    void setUp() throws Exception {
        promptLoader = Mockito.mock(PromptLoader.class);
        agentProperties = new LuminaAgentProperties();
        classifier = Mockito.mock(ReadOnlyToolClassifier.class);
        steeringStore = new InMemorySteeringMessageStore(agentProperties);
        engine = new DefaultAgentExecutionEngine(
                Mockito.mock(ConfigLoader.class),
                promptLoader,
                Mockito.mock(MemoryManager.class),
                agentProperties,
                Mockito.mock(ChatModelFactory.class),
                Mockito.mock(LlmResilienceWrapper.class),
                Mockito.mock(ApplicationContext.class),
                Mockito.mock(EnhancedToolManager.class),
                Mockito.mock(ToolInvocationRecorder.class),
                Mockito.mock(ToolCircuitBreaker.class),
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry(),
                Mockito.mock(io.agentscope.core.rag.Knowledge.class),
                Mockito.mock(RagProperties.class));
        setField("readOnlyClassifier", classifier);
        setField("steeringMessageStore", steeringStore);
    }

    @AfterEach
    void tearDown() {
        io.lumina.common.core.BaseContext.clear();
    }

    private void setField(String name, Object value) throws Exception {
        Field field = DefaultAgentExecutionEngine.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(engine, value);
    }

    private AgentConfig config(String agentType, String sessionMode) {
        AgentConfig config = new AgentConfig();
        config.setAgentId(1L);
        config.setAgentName("tester");
        config.setAgentType(agentType);
        config.setSessionMode(sessionMode);
        return config;
    }

    // ==================== Explore 型会话模式强制 ====================

    @Test
    void exploreProfileForcesPlanEvenWithYolo() {
        assertThat(DefaultAgentExecutionEngine.effectiveSessionMode(config("Explore", "YOLO")))
                .isEqualTo("PLAN");
        assertThat(DefaultAgentExecutionEngine.effectiveSessionMode(config("explore", null)))
                .isEqualTo("PLAN");
    }

    @Test
    void nonExploreKeepsConfiguredMode() {
        assertThat(DefaultAgentExecutionEngine.effectiveSessionMode(config("ReAct", "YOLO")))
                .isEqualTo("YOLO");
        assertThat(DefaultAgentExecutionEngine.effectiveSessionMode(config("ReAct", null)))
                .isNull();
        assertThat(DefaultAgentExecutionEngine.effectiveSessionMode(null)).isNull();
    }

    @Test
    void exploreProfileDetection() {
        assertThat(DefaultAgentExecutionEngine.isExploreProfile(config("Explore", null))).isTrue();
        assertThat(DefaultAgentExecutionEngine.isExploreProfile(config("ReAct", null))).isFalse();
        assertThat(DefaultAgentExecutionEngine.isExploreProfile(null)).isFalse();
    }

    // ==================== Explore 型工具面过滤 ====================

    @Test
    void exploreBlocksNonReadonlyTool() {
        when(classifier.isProvablyReadOnly(any(ToolExecutionContext.class))).thenReturn(false);

        assertThat(engine.isToolBlockedByExploreProfile(config("Explore", null), tool("util.write")))
                .isTrue();
    }

    @Test
    void exploreAllowsProvablyReadonlyTool() {
        when(classifier.isProvablyReadOnly(any(ToolExecutionContext.class))).thenReturn(true);

        assertThat(engine.isToolBlockedByExploreProfile(config("Explore", null), tool("util.search")))
                .isFalse();
    }

    @Test
    void nonExploreProfileNeverBlocks() {
        when(classifier.isProvablyReadOnly(any(ToolExecutionContext.class))).thenReturn(false);

        assertThat(engine.isToolBlockedByExploreProfile(config("ReAct", null), tool("util.write")))
                .isFalse();
    }

    @Test
    void exploreWithoutClassifierDegradesToAllow() throws Exception {
        setField("readOnlyClassifier", null);

        assertThat(engine.isToolBlockedByExploreProfile(config("Explore", null), tool("util.write")))
                .isFalse();
    }

    @Test
    void exploreClassifierExceptionFailsClosed() {
        when(classifier.isProvablyReadOnly(any(ToolExecutionContext.class)))
                .thenThrow(new IllegalStateException("分类器故障"));

        assertThat(engine.isToolBlockedByExploreProfile(config("Explore", null), tool("util.write")))
                .isTrue();
    }

    private ToolDefinition tool(String name) {
        return ToolDefinition.create(name, "测试工具", "util", params -> "ok");
    }

    // ==================== 执行入口消费转向 ====================

    @Test
    void pendingSteeringAppendedToTaskAtEntry() {
        agentProperties.getSteering().setEnabled(true);
        steeringStore.offer("c9", "优先看订单模块");

        engine.executeSync("biz", "分析代码库", config("ReAct", null), "c9");

        verify(promptLoader).fillTemplate(eq(null), contains("运行中转向指令"));
        verify(promptLoader).fillTemplate(eq(null), contains("优先看订单模块"));
    }

    @Test
    void steeringDisabledSkipsConsumption() {
        steeringStore.offer("c9", "不该出现");

        engine.executeSync("biz", "分析代码库", config("ReAct", null), "c9");

        verify(promptLoader).fillTemplate(null, "分析代码库");
        assertThat(steeringStore.drain("c9")).containsExactly("不该出现");
    }

    @Test
    void steeringWithoutConversationSkipped() {
        agentProperties.getSteering().setEnabled(true);
        steeringStore.offer("c9", "无会话上下文");

        engine.executeSync("biz", "任务", config("ReAct", null), null);

        verify(promptLoader).fillTemplate(null, "任务");
    }

    // ==================== 转向续跑消息构建 ====================

    @Test
    void buildSteeringContinuationAppendsUserInstruction() throws Exception {
        java.lang.reflect.Method method = DefaultAgentExecutionEngine.class.getDeclaredMethod(
                "buildSteeringContinuationMessages", java.util.List.class, String.class, java.util.List.class);
        method.setAccessible(true);
        java.util.List<io.agentscope.core.message.Msg> base = java.util.List.of(
                io.agentscope.core.message.Msg.builder()
                        .role(io.agentscope.core.message.MsgRole.SYSTEM).textContent("sys").build());

        @SuppressWarnings("unchecked")
        java.util.List<io.agentscope.core.message.Msg> next = (java.util.List<io.agentscope.core.message.Msg>)
                method.invoke(engine, base, "上一段回答", java.util.List.of("看支付模块", "跳过前端"));

        assertThat(next).hasSize(3);
        assertThat(next.get(1).getRole()).isEqualTo(io.agentscope.core.message.MsgRole.ASSISTANT);
        assertThat(next.get(2).getRole()).isEqualTo(io.agentscope.core.message.MsgRole.USER);
        assertThat(next.get(2).getTextContent()).contains("运行中转向指令", "看支付模块", "跳过前端");
    }

    // ==================== 续跑预算 ====================

    @Test
    void segmentBudgetTakesMaxWhenSteeringActive() {
        agentProperties.getSteering().setEnabled(true);
        agentProperties.getHooks().setEnabled(true);
        agentProperties.getSteering().setMaxSegmentContinues(5);
        agentProperties.getHooks().setMaxStopContinues(2);

        assertThat(engine.segmentContinuesBudget(true)).isEqualTo(5);
        assertThat(engine.segmentContinuesBudget(false)).isEqualTo(5);
    }

    @Test
    void segmentBudgetFallsBackToStopBudgetWhenSteeringOff() {
        agentProperties.getHooks().setEnabled(true);
        agentProperties.getHooks().setMaxStopContinues(2);

        assertThat(engine.segmentContinuesBudget(true)).isEqualTo(2);
    }
}
