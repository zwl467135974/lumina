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
import io.lumina.agent.model.StreamChunk;
import io.lumina.agent.model.StreamEventType;
import io.lumina.agent.monitor.ToolCircuitBreaker;
import io.lumina.agent.monitor.ToolInvocationRecorder;
import io.lumina.agent.resilience.LlmResilienceWrapper;
import io.lumina.agent.steering.InMemorySteeringMessageStore;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Stop 续跑 / 转向续段完整循环测试（v3.14 批次 3.1/3.2 补验）
 *
 * <p>以 spy stub {@code executeWithOverflowRecovery}（同包可见）驱动完整循环——
 * 验证"决策 → 再执行 → 再决策 → 上限强制结束"链路与续跑消息内容，
 * 不依赖真实 AgentScope/LLM。流式段循环（stopAwareSegmentStream）以反射
 * 注入假段构造器驱动。
 *
 * @author Lumina Team
 * @since 3.14.0
 */
class DefaultAgentExecutionEngineStopLoopTest {

    private LuminaAgentProperties props;
    private MemoryManager memoryManager;
    private InMemorySteeringMessageStore steeringStore;
    private DefaultAgentExecutionEngine engine;

    @BeforeEach
    void setUp() {
        props = new LuminaAgentProperties();
        memoryManager = Mockito.mock(MemoryManager.class);
        steeringStore = new InMemorySteeringMessageStore(props);
        engine = Mockito.spy(new DefaultAgentExecutionEngine(
                Mockito.mock(ConfigLoader.class),
                Mockito.mock(PromptLoader.class),
                memoryManager,
                props,
                Mockito.mock(ChatModelFactory.class),
                Mockito.mock(LlmResilienceWrapper.class),
                Mockito.mock(ApplicationContext.class),
                Mockito.mock(EnhancedToolManager.class),
                Mockito.mock(ToolInvocationRecorder.class),
                Mockito.mock(ToolCircuitBreaker.class),
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry(),
                Mockito.mock(io.agentscope.core.rag.Knowledge.class),
                Mockito.mock(RagProperties.class)));
    }

    @AfterEach
    void tearDown() {
        io.lumina.common.core.BaseContext.clear();
    }

    private void installHook(AgentLifecycleHook hook) throws Exception {
        org.springframework.beans.factory.ObjectProvider<AgentLifecycleHook> hookProvider =
                Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        Mockito.when(hookProvider.orderedStream()).thenReturn(java.util.stream.Stream.of(hook));
        org.springframework.beans.factory.ObjectProvider<MeterRegistry> meterProvider =
                Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        AgentHookInvoker invoker = new AgentHookInvoker(props, hookProvider, meterProvider);
        props.getHooks().setEnabled(true);
        setField("hookInvoker", invoker);
    }

    private void setField(String name, Object value) throws Exception {
        Field field = DefaultAgentExecutionEngine.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(engine, value);
    }

    private static Msg msg(String text) {
        return Msg.builder().role(MsgRole.ASSISTANT).textContent(text).build();
    }

    private AgentConfig config() {
        AgentConfig config = new AgentConfig();
        config.setAgentId(1L);
        config.setAgentName("tester");
        config.setAgentType("ReAct");
        return config;
    }

    // ==================== 同步路径：Stop 续跑循环 ====================

    @Test
    void stopContinueReexecutesUntilAllow() throws Exception {
        AtomicInteger continueCalls = new AtomicInteger();
        installHook(new AgentLifecycleHook() {
            @Override
            public HookDecision onStop(AgentLifecycleHook.StopInput input) {
                return input.continueCount() < 2
                        ? HookDecision.continueTurn("缺少结论段落")
                        : HookDecision.allow();
            }
        });
        Mockito.doReturn(msg("第一段"), msg("第二段"), msg("最终回答"))
                .when(engine).executeWithOverflowRecovery(any(), anyList());

        String result = engine.executeSync("biz", "任务", config(), "c1").getResult();

        assertThat(result).isEqualTo("最终回答");
        verify(engine, times(3)).executeWithOverflowRecovery(any(), anyList());
    }

    @Test
    void stopContinueInjectsReasonAsUserMessage() throws Exception {
        installHook(new AgentLifecycleHook() {
            @Override
            public HookDecision onStop(AgentLifecycleHook.StopInput input) {
                return input.continueCount() < 1
                        ? HookDecision.continueTurn("请补充风险清单")
                        : HookDecision.allow();
            }
        });
        Mockito.doReturn(msg("第一段"), msg("第二段"))
                .when(engine).executeWithOverflowRecovery(any(), anyList());

        engine.executeSync("biz", "任务", config(), "c1");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Msg>> captor = ArgumentCaptor.forClass(List.class);
        verify(engine, times(2)).executeWithOverflowRecovery(any(), captor.capture());
        List<Msg> secondCall = captor.getAllValues().get(1);
        Msg last = secondCall.get(secondCall.size() - 1);
        assertThat(last.getRole()).isEqualTo(MsgRole.USER);
        assertThat(last.getTextContent()).contains("系统继续指令", "请补充风险清单");
        Msg prev = secondCall.get(secondCall.size() - 2);
        assertThat(prev.getRole()).isEqualTo(MsgRole.ASSISTANT);
        assertThat(prev.getTextContent()).isEqualTo("第一段");
    }

    @Test
    void stopBudgetForcesEnd() throws Exception {
        props.getHooks().setMaxStopContinues(2);
        installHook(new AgentLifecycleHook() {
            @Override
            public HookDecision onStop(AgentLifecycleHook.StopInput input) {
                return HookDecision.continueTurn("永远不够");
            }
        });
        Mockito.doReturn(msg("1"), msg("2"), msg("3"), msg("4"))
                .when(engine).executeWithOverflowRecovery(any(), anyList());

        String result = engine.executeSync("biz", "任务", config(), "c1").getResult();

        // 初始 1 次 + 预算 2 次续跑 = 3 次执行，达上限强制结束
        assertThat(result).isEqualTo("3");
        verify(engine, times(3)).executeWithOverflowRecovery(any(), anyList());
    }

    // ==================== 同步路径：转向续跑 ====================

    @Test
    void steeringTriggersContinuationWithInstruction() throws Exception {
        props.getSteering().setEnabled(true);
        setField("steeringMessageStore", steeringStore);
        // 转向消息在首次执行期间到达（执行前到达的会走入口消费点合入任务——同语义两路径）
        Mockito.doAnswer(inv -> {
            steeringStore.offer("c9", "改看支付模块");
            return msg("第一段");
        }).doReturn(msg("转向后的回答"))
                .when(engine).executeWithOverflowRecovery(any(), anyList());

        String result = engine.executeSync("biz", "任务", config(), "c9").getResult();

        assertThat(result).isEqualTo("转向后的回答");
        verify(engine, times(2)).executeWithOverflowRecovery(any(), anyList());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Msg>> captor = ArgumentCaptor.forClass(List.class);
        verify(engine, times(2)).executeWithOverflowRecovery(any(), captor.capture());
        List<Msg> secondCall = captor.getAllValues().get(1);
        assertThat(secondCall.get(secondCall.size() - 1).getTextContent())
                .contains("用户运行中转向指令", "改看支付模块");
    }

    @Test
    void memorySavedOnceWithFinalResultAfterContinuation() throws Exception {
        installHook(new AgentLifecycleHook() {
            @Override
            public HookDecision onStop(AgentLifecycleHook.StopInput input) {
                return input.continueCount() < 1
                        ? HookDecision.continueTurn("补充")
                        : HookDecision.allow();
            }
        });
        Mockito.doReturn(msg("中间段"), msg("最终落库的回答"))
                .when(engine).executeWithOverflowRecovery(any(), anyList());

        engine.executeSync("biz", "任务", config(), "c9");

        // 中间段不入记忆，只有最终结果落库一次
        verify(memoryManager, times(1)).addMemory(eq("c9"), eq("assistant"), contains("最终落库的回答"));
        verify(memoryManager, org.mockito.Mockito.never())
                .addMemory(eq("c9"), eq("assistant"), contains("中间段"));
    }

    // ==================== 流式路径：续段循环（反射驱动假段） ====================

    @SuppressWarnings("unchecked")
    private java.util.function.Function<List<Msg>, reactor.core.publisher.Flux<StreamChunk>> fakeSegment(
            StringBuilder finalResponse, AtomicReference<List<Msg>> lastMessages, AtomicInteger segments) {
        return messages -> {
            lastMessages.set(messages);
            segments.incrementAndGet();
            return reactor.core.publisher.Flux
                    .just(new StreamChunk(StreamEventType.FINAL, "段内容" + segments.get(), true))
                    .doOnNext(chunk -> finalResponse.append(chunk.content()));
        };
    }

    private reactor.core.publisher.Flux<StreamChunk> invokeSegmentLoop(
            Function<List<Msg>, reactor.core.publisher.Flux<StreamChunk>> segmentBuilder,
            List<Msg> context, StringBuilder finalResponse, AtomicInteger used,
            int budget, boolean hooksActive, boolean steeringActive) throws Exception {
        Method method = DefaultAgentExecutionEngine.class.getDeclaredMethod("stopAwareSegmentStream",
                java.util.function.Function.class, List.class, StringBuilder.class,
                AtomicInteger.class, int.class, boolean.class, boolean.class,
                AgentConfig.class, String.class);
        method.setAccessible(true);
        return (reactor.core.publisher.Flux<StreamChunk>) method.invoke(engine, segmentBuilder,
                context, finalResponse, used, budget, hooksActive, steeringActive, config(), "c9");
    }

    @Test
    void streamSteeringContinuesSegmentThenCompletes() throws Exception {
        props.getSteering().setEnabled(true);
        setField("steeringMessageStore", steeringStore);
        steeringStore.offer("c9", "只看订单域");
        StringBuilder finalResponse = new StringBuilder();
        AtomicReference<List<Msg>> lastMessages = new AtomicReference<>(List.of());
        AtomicInteger segments = new AtomicInteger();

        invokeSegmentLoop(fakeSegment(finalResponse, lastMessages, segments),
                new ArrayList<>(List.of(msg("ctx"))), finalResponse,
                new AtomicInteger(), 3, false, true)
                .doOnNext(c -> { })
                .blockLast(java.time.Duration.ofSeconds(10));

        // 段1 → 消费转向续段 → 段2 → 队列空且无钩子 → 结束
        assertThat(segments.get()).isEqualTo(2);
        List<Msg> second = lastMessages.get();
        assertThat(second.get(second.size() - 1).getTextContent()).contains("只看订单域");
        assertThat(finalResponse.toString()).contains("段内容1").contains("段内容2");
    }

    @Test
    void streamStopBudgetExhaustedAfterOneContinue() throws Exception {
        installHook(new AgentLifecycleHook() {
            @Override
            public HookDecision onStop(AgentLifecycleHook.StopInput input) {
                return HookDecision.continueTurn("再想想");
            }
        });
        StringBuilder finalResponse = new StringBuilder();
        AtomicInteger segments = new AtomicInteger();

        invokeSegmentLoop(fakeSegment(finalResponse, new AtomicReference<>(List.of()), segments),
                new ArrayList<>(List.of(msg("ctx"))), finalResponse,
                new AtomicInteger(), 1, true, false)
                .blockLast(java.time.Duration.ofSeconds(10));

        // 预算 1：段1 → Stop continue → 段2 → 预算耗尽强制结束
        assertThat(segments.get()).isEqualTo(2);
    }
}
