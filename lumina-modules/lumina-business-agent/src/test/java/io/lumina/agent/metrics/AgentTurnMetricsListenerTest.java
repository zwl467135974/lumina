package io.lumina.agent.metrics;

import io.lumina.agent.event.AgentTurnEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AgentTurnMetricsListener 单元测试
 *
 * @author Lumina Team
 * @since 3.11.0
 */
class AgentTurnMetricsListenerTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final AgentTurnMetricsListener listener = new AgentTurnMetricsListener(registry);

    @Test
    void recordsCompletedCountAndDurationAndTokens() {
        io.lumina.agent.model.ExecuteResult.TokenUsage usage =
                new io.lumina.agent.model.ExecuteResult.TokenUsage();
        usage.setPromptTokens(100);
        usage.setCompletionTokens(50);
        usage.setTotalTokens(150);
        listener.onTurnEvent(AgentTurnEvent.completed("assistant", 1L, "客服",
                "conv-1", false, 1500L, usage));

        assertThat(registry.counter("agent.turn.count",
                "result", "completed", "streaming", "false").count()).isEqualTo(1.0);
        assertThat(registry.timer("agent.turn.duration", "result", "completed").count()).isEqualTo(1);
        assertThat(registry.summary("agent.turn.tokens").count()).isEqualTo(1);
        assertThat(registry.summary("agent.turn.tokens").max()).isEqualTo(150.0);
    }

    @Test
    void recordsInterruptedWithoutDurationOrTokens() {
        listener.onTurnEvent(AgentTurnEvent.interrupted("assistant", 1L, "客服",
                "conv-1", true, "客户端断开，流式取消"));

        assertThat(registry.counter("agent.turn.count",
                "result", "interrupted", "streaming", "true").count()).isEqualTo(1.0);
        // 中断事件无 duration，不应产生 timer
        assertThat(registry.getMeters().stream()
                .anyMatch(m -> m.getId().getName().equals("agent.turn.duration"))).isFalse();
    }

    @Test
    void recordsFailedWithErrorMessage() {
        listener.onTurnEvent(AgentTurnEvent.failed("assistant", 1L, "客服",
                "conv-1", false, 800L, "LLM 超时"));

        assertThat(registry.counter("agent.turn.count",
                "result", "failed", "streaming", "false").count()).isEqualTo(1.0);
        assertThat(registry.timer("agent.turn.duration", "result", "failed").count()).isEqualTo(1);
    }

    @Test
    void listenerExceptionDoesNotPropagate() {
        // registry 正常工作，此处验证监听器自身对异常事件（null 字段）不抛出
        listener.onTurnEvent(AgentTurnEvent.started(null, null, null, null, false));
        assertThat(registry.counter("agent.turn.count",
                "result", "started", "streaming", "false").count()).isEqualTo(1.0);
    }
}
