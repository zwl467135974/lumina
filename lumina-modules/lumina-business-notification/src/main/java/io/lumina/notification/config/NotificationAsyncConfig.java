package io.lumina.notification.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 通知模块异步配置
 *
 * <p>提供 webhook 分发专用线程池，与审计/Agent 任务线程池隔离，
 * 避免外部 webhook 端点响应慢时拖垮其他异步链路。
 * {@code @EnableAsync} 已由 framework 的 AsyncConfig 全局启用。
 *
 * @author Lumina Team
 * @since 3.4.0
 */
@Slf4j
@Configuration
public class NotificationAsyncConfig {

    /**
     * Webhook 分发线程池
     *
     * <p>外发 HTTP 请求 IO 密集且可能重试退避（单条最长约 13s），
     * 队列打满时由 CallerRunsPolicy 降级为调用线程执行。
     */
    @Bean("webhookExecutor")
    public Executor webhookExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("webhook-dispatch-");
        executor.setDaemon(true);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        log.info("Webhook 分发线程池初始化完成: core=2, max=4, queue=200");
        return executor;
    }
}
