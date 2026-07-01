package io.lumina.agent.monitor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * ToolInvocationRecorder 单元测试
 *
 * @author Lumina Team
 * @since 1.1.0
 */
class ToolInvocationRecorderTest {

    private ToolInvocationRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder = new ToolInvocationRecorder();
    }

    @Test
    void recordSuccessUpdatesStats() {
        recorder.record(ToolInvocationRecord.success("search", "web", "{}", "result", 100, null));

        Map<String, Map<String, Object>> stats = recorder.getAllStats();
        assertThat(stats).containsKey("search");
        assertThat(stats.get("search").get("totalInvocations")).isEqualTo(1L);
        assertThat(stats.get("search").get("successCount")).isEqualTo(1L);
        assertThat((double) stats.get("search").get("successRate")).isEqualTo(1.0);
    }

    @Test
    void recordFailureUpdatesStats() {
        recorder.record(ToolInvocationRecord.failure("search", "web", "{}", "timeout", 200, null));

        Map<String, Object> stats = recorder.getStatsMap("search");
        assertThat(stats.get("failureCount")).isEqualTo(1L);
        assertThat(stats.get("totalInvocations")).isEqualTo(1L);
        assertThat((double) stats.get("successRate")).isEqualTo(0.0);
    }

    @Test
    void successRateAndDurationStats() {
        recorder.record(ToolInvocationRecord.success("t", "cat", "", "", 50, null));
        recorder.record(ToolInvocationRecord.success("t", "cat", "", "", 100, null));
        recorder.record(ToolInvocationRecord.failure("t", "cat", "", "err", 200, null));

        Map<String, Object> stats = recorder.getStatsMap("t");
        assertThat(stats.get("totalInvocations")).isEqualTo(3L);
        assertThat(stats.get("successCount")).isEqualTo(2L);
        assertThat(stats.get("failureCount")).isEqualTo(1L);
        assertThat((double) stats.get("successRate")).isCloseTo(0.667, within(0.01));
        assertThat((double) stats.get("avgDurationMs")).isCloseTo(116.67, within(0.1));
        assertThat(stats.get("maxDurationMs")).isEqualTo(200L);
        assertThat(stats.get("minDurationMs")).isEqualTo(50L);
    }

    @Test
    void getRecentReturnsLatestRecords() {
        for (int i = 0; i < 5; i++) {
            recorder.record(ToolInvocationRecord.success("t", "cat", "", "r" + i, 10, null));
        }

        List<ToolInvocationRecord> recent = recorder.getRecent(3);
        assertThat(recent).hasSize(3);
        assertThat(recent.get(2).output()).isEqualTo("r4");
    }

    @Test
    void isolatedToolStats() {
        recorder.record(ToolInvocationRecord.success("t1", "cat", "", "", 10, null));
        recorder.record(ToolInvocationRecord.success("t2", "cat", "", "", 20, null));

        assertThat(recorder.getAllStats()).hasSize(2);
        assertThat(recorder.getStatsMap("t1").get("totalInvocations")).isEqualTo(1L);
        assertThat(recorder.getStatsMap("t2").get("totalInvocations")).isEqualTo(1L);
    }

    @Test
    void clearResetsAll() {
        recorder.record(ToolInvocationRecord.success("t", "cat", "", "", 10, null));
        recorder.clear();

        assertThat(recorder.getAllStats()).isEmpty();
        assertThat(recorder.getRecent(10)).isEmpty();
    }

    @Test
    void getStatsForUnknownToolReturnsNull() {
        assertThat(recorder.getStatsMap("nonexistent")).isNull();
    }
}
