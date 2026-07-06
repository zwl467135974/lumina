package io.lumina.agent.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 异步任务进度注册中心
 *
 * <p>基于 Reactor {@link Sinks.Many} 管理每个任务的进度事件流，
 * 连接后台线程池执行与 SSE 响应式推送。
 *
 * <p>使用 {@code replay().limit(16)} 策略，确保 SSE 客户端晚连接时也能收到历史事件。
 * 终态事件（COMPLETED / FAILED / CANCELLED）后自动 complete 并清理 sink。
 * 超时清理（30 分钟）防止泄漏。
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Slf4j
@Component
public class TaskProgressRegistry {

    private static final int REPLAY_LIMIT = 16;
    private static final long CLEANUP_DELAY_MINUTES = 30;

    private final ConcurrentHashMap<String, Sinks.Many<Map<String, Object>>> sinks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> timestamps = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "task-progress-cleanup");
        t.setDaemon(true);
        return t;
    });

    /**
     * 注册任务进度流（在 submitTask 时调用）
     */
    public Sinks.Many<Map<String, Object>> register(String taskUuid) {
        Sinks.Many<Map<String, Object>> sink = Sinks.many().replay().limit(REPLAY_LIMIT);
        sinks.put(taskUuid, sink);
        timestamps.put(taskUuid, System.currentTimeMillis());
        scheduleCleanup(taskUuid);
        return sink;
    }

    /**
     * 发送进度事件
     */
    public void emit(String taskUuid, Map<String, Object> event) {
        Sinks.Many<Map<String, Object>> sink = sinks.get(taskUuid);
        if (sink != null) {
            sink.tryEmitNext(event);
            String status = (String) event.get("status");
            if (isTerminal(status)) {
                sink.tryEmitComplete();
                sinks.remove(taskUuid);
                timestamps.remove(taskUuid);
            }
        }
    }

    /**
     * 获取已注册的进度流（SSE 端点订阅时调用）
     *
     * @return 进度流，若任务已结束则返回 null
     */
    public Sinks.Many<Map<String, Object>> getSink(String taskUuid) {
        return sinks.get(taskUuid);
    }

    private boolean isTerminal(String status) {
        return "COMPLETED".equals(status) || "FAILED".equals(status) || "CANCELLED".equals(status);
    }

    private void scheduleCleanup(String taskUuid) {
        scheduler.schedule(() -> {
            Sinks.Many<Map<String, Object>> sink = sinks.remove(taskUuid);
            timestamps.remove(taskUuid);
            if (sink != null) {
                sink.tryEmitComplete();
                log.debug("任务进度流超时清理: {}", taskUuid);
            }
        }, CLEANUP_DELAY_MINUTES, TimeUnit.MINUTES);
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
        sinks.values().forEach(Sinks.Many::tryEmitComplete);
        sinks.clear();
        timestamps.clear();
    }
}
