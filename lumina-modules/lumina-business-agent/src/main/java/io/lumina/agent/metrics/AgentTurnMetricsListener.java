package io.lumina.agent.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import io.lumina.agent.event.AgentTurnEvent;

import java.util.concurrent.TimeUnit;

/**
 * Agent 轮次事件指标监听器（观测事件总线的第一个消费者）
 *
 * <p>消费引擎发布的 {@link AgentTurnEvent}，输出 Micrometer 指标：
 * <ul>
 *   <li>{@code agent.turn.count}{result} —— 轮次计数（completed/failed/interrupted/started）</li>
 *   <li>{@code agent.turn.duration}{result} —— 轮次耗时（有 duration 时）</li>
 *   <li>{@code agent.turn.tokens} —— token 消耗分布（有用量时）</li>
 * </ul>
 * 指标需求自此不再修改引擎代码——新增观测只加监听器。
 *
 * @author Lumina Team
 * @since 3.11.0
 */
@Slf4j
@Component
public class AgentTurnMetricsListener {

    private final MeterRegistry meterRegistry;

    public AgentTurnMetricsListener(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @EventListener
    public void onTurnEvent(AgentTurnEvent event) {
        try {
            String result = event.phase().name().toLowerCase();
            meterRegistry.counter("agent.turn.count",
                    Tags.of("result", result, "streaming", String.valueOf(event.streaming())))
                    .increment();

            if (event.durationMs() != null) {
                meterRegistry.timer("agent.turn.duration", Tags.of("result", result))
                        .record(event.durationMs(), TimeUnit.MILLISECONDS);
            }
            if (event.totalTokens() != null && event.totalTokens() > 0) {
                meterRegistry.summary("agent.turn.tokens").record(event.totalTokens());
            }
            if (event.phase() == AgentTurnEvent.Phase.INTERRUPTED) {
                log.info("轮次中断: agentId={}, conversationId={}, reason={}",
                        event.agentId(), event.conversationId(), event.errorMessage());
            }
        } catch (Exception e) {
            log.debug("轮次指标记录失败（不影响执行）: {}", e.getMessage());
        }
    }
}
