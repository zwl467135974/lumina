package io.lumina.agent.monitor;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 工具调用记录器
 *
 * <p>收集工具调用记录并维护按工具名维度的统计（调用次数、成功率、耗时分布）。
 * 内存缓冲，保留最近 {@link #MAX_RECORDS} 条记录。
 *
 * @author Lumina Team
 * @since 1.1.0
 */
@Slf4j
@Component
public class ToolInvocationRecorder {

    /**
     * 最大保留记录数（内存环形缓冲）
     */
    private static final int MAX_RECORDS = 1000;

    private final Deque<ToolInvocationRecord> records = new ConcurrentLinkedDeque<>();
    private final Map<String, ToolStats> statsMap = new ConcurrentHashMap<>();

    /**
     * 记录一次工具调用
     */
    public void record(ToolInvocationRecord r) {
        records.addLast(r);
        while (records.size() > MAX_RECORDS) {
            records.pollFirst();
        }
        statsMap.computeIfAbsent(r.toolName(), k -> {
            ToolStats s = new ToolStats();
            s.setToolName(k);
            return s;
        }).accumulate(r);

        if (r.success()) {
            log.info("工具调用成功: name={}, duration={}ms", r.toolName(), r.durationMs());
        } else {
            log.warn("工具调用失败: name={}, duration={}ms, error={}", r.toolName(), r.durationMs(), r.error());
        }
    }

    /**
     * 获取最近的 N 条调用记录
     */
    public List<ToolInvocationRecord> getRecent(int n) {
        List<ToolInvocationRecord> all = new ArrayList<>(records);
        int size = all.size();
        if (size <= n) {
            return all;
        }
        return all.subList(size - n, size);
    }

    /**
     * 获取指定工具的统计
     */
    public ToolStats getStats(String toolName) {
        return statsMap.get(toolName);
    }

    /**
     * 获取所有工具的统计（基本类型 Map，便于序列化）
     */
    public Map<String, Map<String, Object>> getAllStats() {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        statsMap.forEach((name, s) -> result.put(name, toStatsMap(s)));
        return result;
    }

    /**
     * 获取指定工具的统计
     */
    public Map<String, Object> getStatsMap(String toolName) {
        ToolStats s = statsMap.get(toolName);
        return s != null ? toStatsMap(s) : null;
    }

    /**
     * 将 ToolStats 转为基本类型 Map（避免 AtomicLong 序列化问题）
     */
    private Map<String, Object> toStatsMap(ToolStats s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("toolName", s.getToolName());
        m.put("totalInvocations", s.getTotalInvocations().get());
        m.put("successCount", s.getSuccessCount().get());
        m.put("failureCount", s.getFailureCount().get());
        m.put("successRate", s.getSuccessRate());
        m.put("totalDurationMs", s.getTotalDurationMs().get());
        m.put("maxDurationMs", s.getMaxDurationMs());
        m.put("minDurationMs", s.getMinDurationMs() == Long.MAX_VALUE ? 0 : s.getMinDurationMs());
        m.put("avgDurationMs", s.getAvgDurationMs());
        m.put("lastInvocationTime", s.getLastInvocationTime());
        return m;
    }

    /**
     * 清空所有记录与统计
     */
    public void clear() {
        records.clear();
        statsMap.clear();
    }

    /**
     * 工具调用统计（按工具名维度）
     */
    @Data
    public static class ToolStats {

        private String toolName;
        private AtomicLong totalInvocations = new AtomicLong();
        private AtomicLong successCount = new AtomicLong();
        private AtomicLong failureCount = new AtomicLong();
        private AtomicLong totalDurationMs = new AtomicLong();
        private volatile long maxDurationMs;
        private volatile long minDurationMs = Long.MAX_VALUE;
        private volatile long lastInvocationTime;

        void accumulate(ToolInvocationRecord r) {
            totalInvocations.incrementAndGet();
            if (r.success()) {
                successCount.incrementAndGet();
            } else {
                failureCount.incrementAndGet();
            }
            totalDurationMs.addAndGet(r.durationMs());
            if (r.durationMs() > maxDurationMs) {
                maxDurationMs = r.durationMs();
            }
            if (r.durationMs() < minDurationMs) {
                minDurationMs = r.durationMs();
            }
            lastInvocationTime = r.timestamp();
        }

        /**
         * 成功率（0-1）
         */
        public double getSuccessRate() {
            long total = totalInvocations.get();
            return total == 0 ? 0 : (double) successCount.get() / total;
        }

        /**
         * 平均耗时（毫秒）
         */
        public double getAvgDurationMs() {
            long total = totalInvocations.get();
            return total == 0 ? 0 : (double) totalDurationMs.get() / total;
        }
    }
}
