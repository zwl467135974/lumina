package io.lumina.agent.service;

import io.lumina.agent.infrastructure.mapper.AgentTaskMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Agent 异步任务对账（实例隔离版，ADR-002）
 *
 * <p>旧进程的执行线程消失后，其 RUNNING 任务必然永久卡死（此前会一直显示
 * "执行中"，token 与结果均未知）。将其标记为 INTERRUPTED——与 FAILED
 * （明确失败）/ CANCELLED（用户主动）区分，语义为"执行已中断、结果未知、
 * 仅幂等操作可安全重试"（借鉴 DeepSeek Harness 崩溃恢复的合成闭合思想：
 * 不掩盖中断事实，给用户和模型可行动的信息）。
 *
 * <p>实例隔离（v3.12.1）：任务提交时写入 {@code instance_id}，对账按实例
 * 判活（{@link AgentInstanceRegistry} 心跳）——只回收心跳已消失的死亡实例
 * 任务，滚动发布时后启动实例不再误杀先启动实例的在跑任务。此外：
 * <ul>
 *   <li>周期对账（默认 300s，0 关闭）：实例崩溃后无需等其他实例重启，
 *       存活实例的下一个周期即可回收其任务；</li>
 *   <li>优雅停机 @PreDestroy：本实例 RUNNING/QUEUED（本地线程池模式）
 *       任务直接标记，不等心跳过期。</li>
 * </ul>
 *
 * <p>存量 NULL 行（本特性上线前的遗留任务）默认按无主回收（等价旧行为，
 * 单实例重启语义正确）；混合版本滚动升级窗口期可配置
 * {@code lumina.agent.task.reconcile.interrupt-null-instance=false} 暂缓。
 *
 * <p>QUEUED 处理：RocketMQ 模式下未确认消息会被重新投递，不标记；
 * 本地线程池模式下随进程丢失，一并标记。
 *
 * @author Lumina Team
 * @since 3.11.0
 */
@Slf4j
@Component
public class AgentTaskStartupReconciler implements ApplicationRunner {

    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_QUEUED = "QUEUED";

    private static final String MESSAGE_INSTANCE_DEAD =
            "执行实例心跳消失（重启/崩溃），任务中断（执行结果未知：仅幂等操作可安全重试）";
    private static final String MESSAGE_INSTANCE_SHUTDOWN =
            "服务停机，任务中断（执行结果未知：仅幂等操作可安全重试）";

    private final AgentTaskMapper agentTaskMapper;
    private final AgentInstanceRegistry instanceRegistry;

    @Value("${rocketmq.consumer.agent-task.enabled:false}")
    private boolean mqTaskEnabled;

    /** 是否回收 instance_id 为 NULL 的存量遗留行（混合版本滚动升级窗口期可关闭） */
    @Value("${lumina.agent.task.reconcile.interrupt-null-instance:true}")
    private boolean interruptNullInstance;

    /** 周期对账间隔（秒），0 表示仅启动时对账 */
    @Value("${lumina.agent.task.reconcile.interval-seconds:300}")
    private long reconcileIntervalSeconds;

    private ScheduledExecutorService periodicReconciler;

    public AgentTaskStartupReconciler(AgentTaskMapper agentTaskMapper,
                                      AgentInstanceRegistry instanceRegistry) {
        this.agentTaskMapper = agentTaskMapper;
        this.instanceRegistry = instanceRegistry;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            reconcileDeadInstances(true);
            schedulePeriodic();
        } catch (Exception e) {
            // 对账失败不阻断服务启动
            log.warn("启动对账失败（不影响服务启动）: {}", e.getMessage());
        }
    }

    /**
     * 回收死亡实例（心跳消失）的任务
     *
     * @param includeLegacyNull 启动对账时附带处理存量 NULL 行；周期对账
     *                          不处理（避免与混合版本滚动升级窗口期的
     *                          旧版实例任务竞争）
     */
    void reconcileDeadInstances(boolean includeLegacyNull) {
        for (String status : reconcilableStatuses()) {
            List<String> instances = agentTaskMapper.selectDistinctInstances(status);
            for (String instanceId : instances) {
                if (instanceRegistry.isAlive(instanceId)) {
                    continue;
                }
                int interrupted = agentTaskMapper.markInterruptedForInstance(
                        status, instanceId, MESSAGE_INSTANCE_DEAD);
                if (interrupted > 0) {
                    log.warn("任务对账：实例 {} 心跳消失，{} 个 {} 任务标记为 INTERRUPTED（结果未知）",
                            instanceId, interrupted, status);
                }
            }
            if (includeLegacyNull && interruptNullInstance) {
                int legacy = agentTaskMapper.markInterruptedLegacyNull(status, MESSAGE_INSTANCE_DEAD);
                if (legacy > 0) {
                    log.warn("任务对账：{} 个无主（存量 NULL 实例）{} 任务标记为 INTERRUPTED（结果未知）",
                            legacy, status);
                }
            }
        }
    }

    private void schedulePeriodic() {
        if (reconcileIntervalSeconds <= 0) {
            return;
        }
        periodicReconciler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "agent-task-reconciler");
            t.setDaemon(true);
            return t;
        });
        // 首次触发提前到 ≤60s：覆盖"崩溃后心跳 TTL 内快速重启"的场景
        // （启动对账时旧心跳可能仍在，过期后的首个周期能尽快回收）
        long initialDelay = Math.min(reconcileIntervalSeconds, 60);
        periodicReconciler.scheduleAtFixedRate(() -> {
            try {
                reconcileDeadInstances(false);
            } catch (Exception e) {
                log.warn("周期任务对账失败（下个周期重试）: {}", e.getMessage());
            }
        }, initialDelay, reconcileIntervalSeconds, TimeUnit.SECONDS);
    }

    /** 优雅停机：本实例在跑任务直接标记（不等心跳过期，给用户确定性） */
    @PreDestroy
    void shutdownReconcile() {
        if (periodicReconciler != null) {
            periodicReconciler.shutdownNow();
        }
        try {
            String selfId = instanceRegistry.selfId();
            for (String status : reconcilableStatuses()) {
                int interrupted = agentTaskMapper.markInterruptedForInstance(
                        status, selfId, MESSAGE_INSTANCE_SHUTDOWN);
                if (interrupted > 0) {
                    log.warn("停机对账：本实例 {} 个 {} 任务标记为 INTERRUPTED（结果未知）",
                            interrupted, status);
                }
            }
        } catch (Exception e) {
            // 停机对账失败由其他实例的心跳过期回收兜底
            log.warn("停机对账失败（将由兄弟实例心跳过期后回收）: {}", e.getMessage());
        }
    }

    private List<String> reconcilableStatuses() {
        // MQ 模式下 QUEUED 会被重投递，不标记；本地线程池模式随进程丢失
        return mqTaskEnabled ? List.of(STATUS_RUNNING) : List.of(STATUS_RUNNING, STATUS_QUEUED);
    }
}
