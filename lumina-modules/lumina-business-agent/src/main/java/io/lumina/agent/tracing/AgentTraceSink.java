package io.lumina.agent.tracing;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lumina.agent.infrastructure.entity.AgentTraceDO;
import io.lumina.agent.infrastructure.mapper.AgentTraceMapper;
import io.lumina.common.core.BaseContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * TraceSink 实现——将 TraceContext 持久化到 lumina_agent_trace 表
 *
 * @author Lumina Team
 * @since 3.7.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentTraceSink implements TraceSink {

    private final AgentTraceMapper agentTraceMapper;
    private final ObjectMapper objectMapper;

    @Override
    public void save(TraceContext ctx) {
        try {
            AgentTraceDO trace = new AgentTraceDO();
            trace.setTraceUuid(ctx.getTraceUuid());
            trace.setAgentName(ctx.getAgentName());
            trace.setAgentType(ctx.getAgentType());
            trace.setAgentId(ctx.getAgentId());
            trace.setConversationUuid(ctx.getConversationUuid());
            trace.setTaskUuid(ctx.getTaskUuid());

            trace.setInputText(ctx.getInputText());
            trace.setOutputText(ctx.getOutputText());
            trace.setStatus(ctx.getStatus());

            trace.setPromptTokens(ctx.getTotalPromptTokens());
            trace.setCompletionTokens(ctx.getTotalCompletionTokens());
            trace.setTotalTokens(ctx.getTotalTokens());

            trace.setDurationMs(ctx.getDurationMs());
            trace.setStartedAt(toLocalDateTime(ctx.getStartedAt()));
            trace.setFinishedAt(ctx.getFinishedAt() > 0 ? toLocalDateTime(ctx.getFinishedAt()) : null);

            // 序列化 steps 为 JSON
            if (ctx.getSteps() != null && !ctx.getSteps().isEmpty()) {
                trace.setSteps(objectMapper.writeValueAsString(ctx.getSteps()));
            }

            // 从 BaseContext 补充租户信息
            trace.setTenantId(BaseContext.getTenantId() != null ? BaseContext.getTenantId() : 0L);
            trace.setCreateBy(BaseContext.getUserId());
            trace.setCreateTime(LocalDateTime.now());

            agentTraceMapper.insert(trace);
            log.debug("Trace 已落库: uuid={}, steps={}, tokens={}",
                    ctx.getTraceUuid(), ctx.getSteps().size(), ctx.getTotalTokens());
        } catch (Exception e) {
            log.warn("Trace 落库失败: {}", e.getMessage());
        }
    }

    private LocalDateTime toLocalDateTime(long epochMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
    }
}
