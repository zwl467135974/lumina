package io.lumina.agent.schedule;

import io.lumina.agent.service.AgentTraceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Agent Trace 数据清理定时任务
 *
 * <p>每天凌晨 3 点清理超过保留期的 trace 记录，防止 lumina_agent_trace 表无限增长。
 * 默认保留 30 天，可通过配置调整。
 *
 * <p>配置项：
 * <ul>
 *   <li>{@code lumina.agent.trace.cleanup.enabled} — 是否启用（默认 true）</li>
 *   <li>{@code lumina.agent.trace.cleanup.retention-days} — 保留天数（默认 30）</li>
 *   <li>{@code lumina.agent.trace.cleanup.cron} — 执行时间（默认每天 3:00 AM）</li>
 * </ul>
 *
 * @author Lumina Team
 * @since 3.7.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "lumina.agent.trace.cleanup", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AgentTraceCleanupJob {

    private final AgentTraceService agentTraceService;

    @Value("${lumina.agent.trace.cleanup.retention-days:30}")
    private int retentionDays;

    /**
     * 定时清理过期 Trace
     */
    @Scheduled(cron = "${lumina.agent.trace.cleanup.cron:0 0 3 * * ?}")
    public void cleanup() {
        try {
            int deleted = agentTraceService.cleanupExpired(retentionDays);
            if (deleted > 0) {
                log.info("Trace 定时清理完成: 删除 {} 条过期记录（保留 {} 天）", deleted, retentionDays);
            }
        } catch (Throwable t) {
            log.error("Trace 定时清理异常", t);
        }
    }
}
