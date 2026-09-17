package io.lumina.agent.tracing;

import io.lumina.agent.infrastructure.entity.ToolUsageDO;
import io.lumina.agent.infrastructure.mapper.ToolUsageMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DbToolUsageSink 单元测试（字段映射与租户兜底）
 *
 * @author Lumina Team
 * @since 3.12.0
 */
class DbToolUsageSinkTest {

    private final ToolUsageMapper toolUsageMapper = Mockito.mock(ToolUsageMapper.class);

    private final DbToolUsageSink sink = new DbToolUsageSink(toolUsageMapper);

    @Test
    void recordMapsAllFields() {
        ToolUsageRecord record = new ToolUsageRecord(3L, "客服助手", 7L, "search-api",
                true, 250L, 120, 3000, 1700000000000L);

        sink.record(record);

        Mockito.verify(toolUsageMapper).insert(Mockito.<ToolUsageDO>argThat(usage ->
                usage.getAgentId().equals(3L)
                        && "客服助手".equals(usage.getAgentName())
                        && usage.getTenantId().equals(7L)
                        && "search-api".equals(usage.getToolName())
                        && usage.getSuccess() == 1
                        && usage.getDurationMs().equals(250L)
                        && usage.getInputChars().equals(120)
                        && usage.getResultChars().equals(3000)
                        && usage.getCreateTime() != null));
    }

    @Test
    void recordFallsBackTenantToZero() {
        sink.record(new ToolUsageRecord(null, "a", null, "t", false, 10L, 0, 0, 1700000000000L));

        Mockito.verify(toolUsageMapper).insert(Mockito.<ToolUsageDO>argThat(usage ->
                usage.getTenantId().equals(0L) && usage.getSuccess() == 0));
    }
}
