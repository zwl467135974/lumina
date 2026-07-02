package io.lumina.framework.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务配置
 *
 * <p>启用 {@link EnableAsync}，提供审计日志等异步场景的线程池。
 *
 * <p>线程池策略：
 * <ul>
 *   <li>核心线程数 8，最大线程数 32</li>
 *   <li>队列容量 200，超出后由 CallerRunsPolicy 降级为同步执行</li>
 *   <li>线程名前缀 lumina-async-，便于日志排查</li>
 * </ul>
 *
 * @author Lumina Team
 * @since 1.3.0
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 审计日志专用线程池
     *
     * <p>审计日志写入失败不影响主流程，使用独立线程池避免影响业务线程。
     */
    @Bean("auditExecutor")
    public Executor auditExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("audit-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        log.info("审计日志异步线程池初始化完成: core=4, max=16, queue=200");
        return executor;
    }
}
