package io.lumina.agent.engine.impl;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.lumina.agent.config.LuminaAgentProperties;
import io.lumina.agent.config.RagProperties;
import io.lumina.agent.hook.AgentHookInvoker;
import io.lumina.agent.hook.AgentLifecycleHook;
import io.lumina.agent.hook.HookDecision;
import io.lumina.agent.loader.ConfigLoader;
import io.lumina.agent.loader.PromptLoader;
import io.lumina.agent.manager.EnhancedToolManager;
import io.lumina.agent.manager.MemoryManager;
import io.lumina.agent.model.AgentConfig;
import io.lumina.agent.model.ChatModelFactory;
import io.lumina.agent.model.ExecuteResult;
import io.lumina.agent.monitor.ToolCircuitBreaker;
import io.lumina.agent.monitor.ToolInvocationRecorder;
import io.lumina.agent.resilience.LlmResilienceWrapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 引擎层的生命周期 Hook 接线测试（v3.14 批次 3.1）
 *
 * <p>UserPromptSubmit / SessionStart 在进入 LLM 执行前生效，可用 mocked 依赖
 * 覆盖到决策点；Stop 续跑的上下文构建以反射验证（完整 Stop 循环依赖真实
 * AgentScope 运行时，由集成环境覆盖）。
 *
 * @author Lumina Team
 * @since 3.14.0
 */
class DefaultAgentExecutionEngineHookTest {

    private DefaultAgentExecutionEngine engine;
    private PromptLoader promptLoader;
    private MemoryManager memoryManager;
    private LuminaAgentProperties agentProperties;
    private AgentConfig agentConfig;

    @BeforeEach
    void setUp() throws Exception {
        memoryManager = Mockito.mock(MemoryManager.class);
        promptLoader = Mockito.mock(PromptLoader.class);
        agentProperties = new LuminaAgentProperties();
        agentProperties.getHooks().setEnabled(true);
        engine = new DefaultAgentExecutionEngine(
                Mockito.mock(ConfigLoader.class),
                promptLoader,
                memoryManager,
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

        agentConfig = new AgentConfig();
        agentConfig.setAgentId(1L);
        agentConfig.setAgentName("tester");
        agentConfig.setAgentType("ReAct");
    }

    @AfterEach
    void tearDown() {
        io.lumina.common.core.BaseContext.clear();
    }

    @SuppressWarnings("unchecked")
    private void installHook(AgentLifecycleHook hook) throws Exception {
        org.springframework.beans.factory.ObjectProvider<AgentLifecycleHook> hookProvider =
                Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        Mockito.when(hookProvider.orderedStream()).thenReturn(java.util.stream.Stream.of(hook));
        org.springframework.beans.factory.ObjectProvider<MeterRegistry> meterProvider =
                Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        AgentHookInvoker invoker = new AgentHookInvoker(agentProperties, hookProvider, meterProvider);
        Field field = DefaultAgentExecutionEngine.class.getDeclaredField("hookInvoker");
        field.setAccessible(true);
        field.set(engine, invoker);
    }

    @Test
    void userPromptDenyFailsTurnWithVisibleReason() throws Exception {
        AtomicReference<String> seenPrompt = new AtomicReference<>();
        installHook(new AgentLifecycleHook() {
            @Override
            public HookDecision onUserPromptSubmit(AgentLifecycleHook.PromptSubmitInput input) {
                seenPrompt.set(input.prompt());
                return HookDecision.deny("包含敏感数据");
            }
        });

        ExecuteResult result = engine.executeSync("biz", "原始任务", agentConfig, "c1");

        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getError()).contains("生命周期钩子拒绝", "包含敏感数据");
        assertThat(seenPrompt.get()).isEqualTo("原始任务");
    }

    @Test
    void userPromptReplaceFlowsIntoPromptFill() throws Exception {
        installHook(new AgentLifecycleHook() {
            @Override
            public HookDecision onUserPromptSubmit(AgentLifecycleHook.PromptSubmitInput input) {
                return HookDecision.replaceInput("脱敏后的任务", "PII 脱敏");
            }
        });

        engine.executeSync("biz", "原始任务", agentConfig, null);

        verify(promptLoader).fillTemplate(null, "脱敏后的任务");
    }

    @Test
    void sessionStartInjectsContextOnFirstTurn() throws Exception {
        Mockito.when(memoryManager.getRecentMemories("c1", 1)).thenReturn(List.of());
        installHook(new AgentLifecycleHook() {
            @Override
            public HookDecision onSessionStart(AgentLifecycleHook.SessionStartInput input) {
                return HookDecision.addContext("本会话为合规审计模式");
            }
        });

        engine.executeSync("biz", "任务", agentConfig, "c1");

        verify(promptLoader).fillTemplate(eq(null), contains("会话开始上下文"));
    }

    @Test
    void sessionStartSkippedWhenHistoryExists() throws Exception {
        Mockito.when(memoryManager.getRecentMemories("c1", 1))
                .thenReturn(List.of(new MemoryManager.Memory("user", "上一轮", 1L)));
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        installHook(new AgentLifecycleHook() {
            @Override
            public HookDecision onSessionStart(AgentLifecycleHook.SessionStartInput input) {
                calls.incrementAndGet();
                return HookDecision.addContext("不该注入");
            }
        });

        engine.executeSync("biz", "任务", agentConfig, "c1");

        assertThat(calls.get()).isZero();
        verify(promptLoader).fillTemplate(null, "任务");
    }

    @Test
    void noHookInvokerKeepsLegacyBehavior() {
        ExecuteResult result = engine.executeSync("biz", "任务", agentConfig, null);

        // 无钩子时决策点零介入（后续因 mocked LLM 工厂失败属既有行为，不因钩子改动改变）
        assertThat(result).isNotNull();
        verify(promptLoader).fillTemplate(null, "任务");
    }

    @Test
    void buildStopContinuationAppendsAssistantAndInstruction() throws Exception {
        List<Msg> base = List.of(Msg.builder().role(MsgRole.SYSTEM).textContent("sys").build());
        Method method = DefaultAgentExecutionEngine.class.getDeclaredMethod(
                "buildStopContinuationMessages", List.class, String.class, String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<Msg> next = (List<Msg>) method.invoke(engine, base, "上一段回答", "未校验数量");

        assertThat(next).hasSize(3);
        assertThat(next.get(1).getRole()).isEqualTo(MsgRole.ASSISTANT);
        assertThat(next.get(1).getTextContent()).isEqualTo("上一段回答");
        assertThat(next.get(2).getRole()).isEqualTo(MsgRole.USER);
        assertThat(next.get(2).getTextContent()).contains("系统继续指令", "未校验数量");
    }

    @Test
    void buildStopContinuationSkipsBlankAssistantText() throws Exception {
        List<Msg> base = List.of(Msg.builder().role(MsgRole.SYSTEM).textContent("sys").build());
        Method method = DefaultAgentExecutionEngine.class.getDeclaredMethod(
                "buildStopContinuationMessages", List.class, String.class, String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<Msg> next = (List<Msg>) method.invoke(engine, base, "  ", "理由");

        assertThat(next).hasSize(2);
        assertThat(next.get(1).getRole()).isEqualTo(MsgRole.USER);
    }
}
