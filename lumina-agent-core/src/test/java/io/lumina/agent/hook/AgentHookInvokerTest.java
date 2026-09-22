package io.lumina.agent.hook;

import io.lumina.agent.config.LuminaAgentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

/**
 * AgentHookInvoker 单元测试
 *
 * <p>覆盖：组合语义（DENY 支配/REPLACE 首个/CONTEXT 累积/CONTINUE 首个）、
 * 隔离语义（超时/异常中立放行）、限额截断、无效动作忽略、开关跳过、Order 排序。
 *
 * @author Lumina Team
 * @since 3.14.0
 */
class AgentHookInvokerTest {

    private LuminaAgentProperties props;

    @BeforeEach
    void setUp() {
        props = new LuminaAgentProperties();
        props.getHooks().setEnabled(true);
        props.getHooks().setTimeoutMs(2000);
    }

    @SuppressWarnings("unchecked")
    private AgentHookInvoker invoker(AgentLifecycleHook... hooks) {
        ObjectProvider<AgentLifecycleHook> provider = Mockito.mock(ObjectProvider.class);
        Mockito.when(provider.orderedStream()).thenReturn(java.util.stream.Stream.of(hooks));
        ObjectProvider<io.micrometer.core.instrument.MeterRegistry> meterProvider =
                Mockito.mock(ObjectProvider.class);
        return new AgentHookInvoker(props, provider, meterProvider);
    }

    /** 可编程桩钩子：按点返回预设决策，记录收到的输入 */
    private static class StubHook implements AgentLifecycleHook {
        final int order;
        HookDecision promptDecision = HookDecision.allow();
        HookDecision sessionStartDecision = HookDecision.allow();
        HookDecision preToolDecision = HookDecision.allow();
        HookDecision stopDecision = HookDecision.allow();
        final AtomicReference<AgentLifecycleHook.ToolResultInput> lastResult = new AtomicReference<>();
        final AtomicInteger promptCalls = new AtomicInteger();

        StubHook(int order) {
            this.order = order;
        }

        @Override
        public int getOrder() {
            return order;
        }

        @Override
        public String getName() {
            return "stub-" + order;
        }

        @Override
        public HookDecision onUserPromptSubmit(AgentLifecycleHook.PromptSubmitInput input) {
            promptCalls.incrementAndGet();
            return promptDecision;
        }

        @Override
        public HookDecision onSessionStart(AgentLifecycleHook.SessionStartInput input) {
            return sessionStartDecision;
        }

        @Override
        public HookDecision onPreToolUse(AgentLifecycleHook.ToolUseInput input) {
            return preToolDecision;
        }

        @Override
        public void onPostToolUse(AgentLifecycleHook.ToolResultInput input) {
            lastResult.set(input);
        }

        @Override
        public HookDecision onStop(AgentLifecycleHook.StopInput input) {
            return stopDecision;
        }
    }

    private AgentLifecycleHook.PromptSubmitInput prompt() {
        return new AgentLifecycleHook.PromptSubmitInput("biz", 1L, "a", "c1", "BUILD", "hello", false);
    }

    @Test
    void denyDominatesAndShortCircuits() {
        StubHook denyHook = new StubHook(0);
        denyHook.promptDecision = HookDecision.deny("含敏感信息");
        StubHook later = new StubHook(1);
        AgentLifecycleHook laterSpy = Mockito.spy(later);

        HookDecision decision = invoker(denyHook, laterSpy).onUserPromptSubmit(prompt());

        assertThat(decision.denied()).isTrue();
        assertThat(decision.reason()).isEqualTo("含敏感信息");
        Mockito.verify(laterSpy, Mockito.never()).onUserPromptSubmit(any());
    }

    @Test
    void replaceInputFirstWinsByOrder() {
        StubHook later = new StubHook(1);
        later.promptDecision = HookDecision.replaceInput("第二个替换", null);
        StubHook first = new StubHook(0);
        first.promptDecision = HookDecision.replaceInput("第一个替换", "标准化");

        HookDecision decision = invoker(later, first).onUserPromptSubmit(prompt());

        assertThat(decision.action()).isEqualTo(HookDecision.Action.REPLACE_INPUT);
        assertThat(decision.replacementInput()).isEqualTo("第一个替换");
    }

    @Test
    void addContextAccumulatesAcrossHooks() {
        StubHook first = new StubHook(0);
        first.promptDecision = HookDecision.addContext("背景A");
        StubHook second = new StubHook(1);
        second.promptDecision = HookDecision.addContext("背景B");

        HookDecision decision = invoker(first, second).onUserPromptSubmit(prompt());

        assertThat(decision.action()).isEqualTo(HookDecision.Action.ADD_CONTEXT);
        assertThat(decision.additionalContext()).isEqualTo("背景A\n背景B");
    }

    @Test
    void replaceAndContextCombine() {
        StubHook replacer = new StubHook(0);
        replacer.promptDecision = HookDecision.replaceInput("REPLACED", null);
        StubHook adder = new StubHook(1);
        adder.promptDecision = HookDecision.addContext("补充");

        HookDecision decision = invoker(replacer, adder).onUserPromptSubmit(prompt());

        assertThat(decision.action()).isEqualTo(HookDecision.Action.REPLACE_INPUT);
        assertThat(decision.replacementInput()).isEqualTo("REPLACED");
        assertThat(decision.additionalContext()).isEqualTo("补充");
    }

    @Test
    void outputBeyondCapsIsTruncated() {
        props.getHooks().setMaxContextChars(100);
        props.getHooks().setMaxReasonChars(50);
        StubHook hook = new StubHook(0);
        hook.promptDecision = HookDecision.deny("r".repeat(500));

        HookDecision denied = invoker(hook).onUserPromptSubmit(prompt());
        assertThat(denied.reason().length()).isLessThanOrEqualTo(50 + 20);

        StubHook contextHook = new StubHook(0);
        contextHook.promptDecision = HookDecision.addContext("x".repeat(500));
        HookDecision withContext = invoker(contextHook).onUserPromptSubmit(prompt());
        assertThat(withContext.additionalContext().length()).isLessThanOrEqualTo(100 + 20);
    }

    @Test
    void timeoutYieldsNeutralWithoutBlockingTurn() {
        props.getHooks().setTimeoutMs(100);
        AgentLifecycleHook slow = new AgentLifecycleHook() {
            @Override
            public HookDecision onPreToolUse(AgentLifecycleHook.ToolUseInput input) {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return HookDecision.deny("迟到的拒绝");
            }
        };

        long start = System.currentTimeMillis();
        HookDecision decision = invoker(slow).onPreToolUse(
                new AgentLifecycleHook.ToolUseInput("util.search", "util", "{}", "c1"));
        long elapsed = System.currentTimeMillis() - start;

        assertThat(decision.action()).isEqualTo(HookDecision.Action.ALLOW);
        assertThat(elapsed).isLessThan(1500);
    }

    @Test
    void hookExceptionYieldsNeutral() {
        AgentLifecycleHook broken = new AgentLifecycleHook() {
            @Override
            public HookDecision onUserPromptSubmit(AgentLifecycleHook.PromptSubmitInput input) {
                throw new IllegalStateException("合规服务不可用");
            }
        };

        HookDecision decision = invoker(broken).onUserPromptSubmit(prompt());

        assertThat(decision.action()).isEqualTo(HookDecision.Action.ALLOW);
    }

    @Test
    void disabledSkipsAllHooks() {
        props.getHooks().setEnabled(false);
        StubHook hook = new StubHook(0);
        hook.promptDecision = HookDecision.deny("拒绝");

        HookDecision decision = invoker(hook).onUserPromptSubmit(prompt());

        assertThat(decision.action()).isEqualTo(HookDecision.Action.ALLOW);
        assertThat(hook.promptCalls.get()).isZero();
    }

    @Test
    void invalidActionAtPreToolUseIgnored() {
        StubHook hook = new StubHook(0);
        hook.preToolDecision = HookDecision.replaceInput("改参数", null);

        HookDecision decision = invoker(hook).onPreToolUse(
                new AgentLifecycleHook.ToolUseInput("util.search", "util", "{}", "c1"));

        assertThat(decision.action()).isEqualTo(HookDecision.Action.ALLOW);
    }

    @Test
    void sessionStartOnlyHonorsAddContext() {
        StubHook denier = new StubHook(0);
        denier.sessionStartDecision = HookDecision.deny("不允许开始");
        StubHook adder = new StubHook(1);
        adder.sessionStartDecision = HookDecision.addContext("会话背景");

        AgentHookInvoker agentHookInvoker = invoker(denier, adder);
        AgentLifecycleHook.SessionStartInput input =
                new AgentLifecycleHook.SessionStartInput("biz", 1L, "a", "c1");

        assertThat(agentHookInvoker.onSessionStart(input).action()).isEqualTo(HookDecision.Action.ADD_CONTEXT);
        assertThat(agentHookInvoker.onSessionStart(input).additionalContext()).isEqualTo("会话背景");
    }

    @Test
    void stopFirstContinueWins() {
        StubHook second = new StubHook(1);
        second.stopDecision = HookDecision.continueTurn("第二个理由");
        StubHook first = new StubHook(0);
        first.stopDecision = HookDecision.continueTurn("第一个理由");

        HookDecision decision = invoker(second, first).onStop(
                new AgentLifecycleHook.StopInput("biz", 1L, "a", "c1", "回答", 0, 3));

        assertThat(decision.action()).isEqualTo(HookDecision.Action.CONTINUE);
        assertThat(decision.reason()).isEqualTo("第一个理由");
    }

    @Test
    void stopInvalidActionTreatedAsAllow() {
        StubHook hook = new StubHook(0);
        hook.stopDecision = HookDecision.deny("拒绝结束");

        HookDecision decision = invoker(hook).onStop(
                new AgentLifecycleHook.StopInput("biz", 1L, "a", "c1", "回答", 0, 3));

        assertThat(decision.action()).isEqualTo(HookDecision.Action.ALLOW);
    }

    @Test
    void stopWithoutReasonFallsBackToPlaceholder() {
        StubHook hook = new StubHook(0);
        hook.stopDecision = HookDecision.continueTurn("  ");

        HookDecision decision = invoker(hook).onStop(
                new AgentLifecycleHook.StopInput("biz", 1L, "a", "c1", "回答", 0, 3));

        assertThat(decision.reason()).contains("未提供继续理由");
    }

    @Test
    void postToolUseNotifiesWithModelVisibleResult() {
        StubHook hook = new StubHook(0);
        AgentHookInvoker agentHookInvoker = invoker(hook);
        AgentLifecycleHook.ToolResultInput input = new AgentLifecycleHook.ToolResultInput(
                "util.search", "util", "{\"q\":\"a\"}", "预览结果", null, 42L, "c1");

        agentHookInvoker.onPostToolUse(input);

        assertThat(hook.lastResult.get()).isNotNull();
        assertThat(hook.lastResult.get().resultText()).isEqualTo("预览结果");
        assertThat(hook.lastResult.get().durationMs()).isEqualTo(42L);
    }

    @Test
    void isEnabledRequiresSwitchAndHooks() {
        assertThat(invoker(new StubHook(0)).isEnabled()).isTrue();
        props.getHooks().setEnabled(false);
        assertThat(invoker(new StubHook(0)).isEnabled()).isFalse();
        props.getHooks().setEnabled(true);
        assertThat(invoker().isEnabled()).isFalse();
    }

    @Test
    void preToolUseDenyCarriesReason() {
        StubHook hook = new StubHook(0);
        hook.preToolDecision = HookDecision.deny("外发审计拦截");

        HookDecision decision = invoker(hook).onPreToolUse(
                new AgentLifecycleHook.ToolUseInput("util.http", "util", "{\"url\":\"x\"}", "c1"));

        assertThat(decision.denied()).isTrue();
        assertThat(decision.reason()).isEqualTo("外发审计拦截");
    }
}
