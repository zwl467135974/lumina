package io.lumina.agent.tracing;

import io.agentscope.core.tracing.TracerRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Trace 全局注册配置
 *
 * <p>启动时将 {@link LuminaTraceTracer} 注册到 AgentScope 的 {@link TracerRegistry}，
 * 之后所有 ReActAgent 的 Agent/Model/Tool 调用自动被拦截采集。
 *
 * <p>可通过配置 {@code lumina.agent.trace.enabled=false} 关闭（零开销）。
 *
 * @author Lumina Team
 * @since 3.7.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TraceConfig {

    private final TraceCollector traceCollector;

    @Value("${lumina.agent.trace.enabled:true}")
    private boolean traceEnabled;

    @PostConstruct
    public void init() {
        if (traceEnabled) {
            LuminaTraceTracer tracer = new LuminaTraceTracer(traceCollector);
            TracerRegistry.register(tracer);
            TracerRegistry.enableTracingHook();
            log.info("推理链 Tracer 已注册 + Hook 已启用（lumina.agent.trace.enabled={}）", traceEnabled);
        } else {
            log.info("推理链 Tracer 已禁用（lumina.agent.trace.enabled=false）");
        }
    }
}
