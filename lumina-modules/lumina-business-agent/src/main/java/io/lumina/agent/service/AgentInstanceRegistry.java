package io.lumina.agent.service;

import io.lumina.framework.cache.RedisCacheManager;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Agent 执行实例注册中心（心跳 + 判活）
 *
 * <p>为多实例部署下的任务对账提供实例隔离（ADR-002）：每个实例启动时生成
 * 唯一 instanceId 并在 Redis 维持心跳（TTL 内周期续期）；任务提交时写入
 * instanceId；对账器只把"心跳已消失"的实例任务标记 INTERRUPTED——
 * 滚动发布时后启动的实例不再误杀先启动实例正在执行的任务。
 *
 * <p>判活 fail-safe：Redis 异常时视为存活（宁可漏对账，不可误杀在跑任务；
 * 心跳消失的兜底由下一次对账周期补上）。
 *
 * <p>优雅停机时主动删除心跳键，让兄弟实例的最快下一个对账周期即可回收
 * 本实例任务；本实例自己的残留任务由对账器 @PreDestroy 直接标记。
 *
 * @author Lumina Team
 * @since 3.12.1
 */
@Slf4j
@Component
public class AgentInstanceRegistry {

    private static final String HEARTBEAT_KEY_PREFIX = "agent:instance:heartbeat:";

    /** 本实例唯一标识（进程生命周期内不变） */
    private final String instanceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

    private final RedisCacheManager redisCacheManager;

    @Value("${lumina.agent.instance.heartbeat-ttl-seconds:90}")
    private long heartbeatTtlSeconds;

    private ScheduledExecutorService renewer;

    private volatile String hostname = "unknown";

    public AgentInstanceRegistry(RedisCacheManager redisCacheManager) {
        this.redisCacheManager = redisCacheManager;
    }

    @PostConstruct
    void start() {
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            // 主机名仅用于可观测，取不到不影响功能
        }
        beat();
        long period = Math.max(heartbeatTtlSeconds / 3, 5);
        renewer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "agent-instance-heartbeat");
            t.setDaemon(true);
            return t;
        });
        renewer.scheduleAtFixedRate(this::beat, period, period, TimeUnit.SECONDS);
        log.info("Agent 实例心跳已启动: instanceId={}, host={}, ttl={}s", instanceId, hostname, heartbeatTtlSeconds);
    }

    /** 本实例标识（任务提交时写入 instance_id） */
    public String selfId() {
        return instanceId;
    }

    /**
     * 实例是否存活（心跳键存在）
     *
     * <p>fail-safe：Redis 异常返回 true——判活失败宁可不回收，不可误杀。
     */
    public boolean isAlive(String otherInstanceId) {
        if (otherInstanceId == null || otherInstanceId.isBlank()) {
            return false;
        }
        try {
            return redisCacheManager.exists(HEARTBEAT_KEY_PREFIX + otherInstanceId);
        } catch (Exception e) {
            log.warn("实例判活失败（按存活处理，不回收其任务）: instanceId={}, error={}",
                    otherInstanceId, e.getMessage());
            return true;
        }
    }

    private void beat() {
        try {
            redisCacheManager.set(HEARTBEAT_KEY_PREFIX + instanceId, hostname,
                    Duration.ofSeconds(heartbeatTtlSeconds));
        } catch (Exception e) {
            // 续期失败不中断调度：偶发 Redis 抖动由 TTL 内的下一次续期覆盖；
            // 持续失败会在日志中持续暴露
            log.warn("实例心跳续期失败: instanceId={}, error={}", instanceId, e.getMessage());
        }
    }

    @PreDestroy
    void stop() {
        if (renewer != null) {
            renewer.shutdownNow();
        }
        try {
            redisCacheManager.delete(HEARTBEAT_KEY_PREFIX + instanceId);
        } catch (Exception e) {
            // 键带 TTL，删除失败最终自然过期
            log.debug("停机清理心跳键失败（将由 TTL 自然过期）: {}", e.getMessage());
        }
    }
}
