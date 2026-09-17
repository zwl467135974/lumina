package io.lumina.agent.tracing;

import io.lumina.agent.infrastructure.entity.ToolUsageDO;
import io.lumina.agent.infrastructure.mapper.ToolUsageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * ToolUsageSink 实现——将工具使用明细持久化到 lumina_tool_usage 表
 *
 * <p>append-only：仅插入，不更新不删除；在 TraceCollector 的异步线程中调用。
 *
 * @author Lumina Team
 * @since 3.12.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DbToolUsageSink implements ToolUsageSink {

    private final ToolUsageMapper toolUsageMapper;

    @Override
    public void record(ToolUsageRecord record) {
        ToolUsageDO usage = new ToolUsageDO();
        usage.setAgentId(record.getAgentId());
        usage.setAgentName(record.getAgentName());
        usage.setTenantId(record.getTenantId() != null ? record.getTenantId() : 0L);
        usage.setToolName(record.getToolName());
        usage.setSuccess(record.isSuccess() ? 1 : 0);
        usage.setDurationMs(record.getDurationMs());
        usage.setInputChars(record.getInputChars());
        usage.setResultChars(record.getResultChars());
        usage.setCreateTime(LocalDateTime.ofInstant(
                Instant.ofEpochMilli(record.getOccurredAt()), ZoneId.systemDefault()));
        toolUsageMapper.insert(usage);
    }
}
