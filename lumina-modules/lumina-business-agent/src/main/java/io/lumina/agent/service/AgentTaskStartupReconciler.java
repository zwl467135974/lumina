package io.lumina.agent.service;

import io.lumina.agent.infrastructure.mapper.AgentTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Agent 异步任务启动对账
 *
 * <p>服务重启后，旧进程的执行线程已消失，RUNNING 任务必然永久卡死（此前会
 * 一直显示"执行中"，token 与结果均未知）。启动时将其标记为 INTERRUPTED——
 * 与 FAILED（明确失败）/ CANCELLED（用户主动）区分，语义为"执行已中断、
 * 结果未知、仅幂等操作可安全重试"（借鉴 DeepSeek Harness 崩溃恢复的
 * 合成闭合思想：不掩盖中断事实，给用户和模型可行动的信息）。
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

    private final AgentTaskMapper agentTaskMapper;

    @Value("${rocketmq.consumer.agent-task.enabled:false}")
    private boolean mqTaskEnabled;

    public AgentTaskStartupReconciler(AgentTaskMapper agentTaskMapper) {
        this.agentTaskMapper = agentTaskMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            int interrupted = agentTaskMapper.markInterruptedOnStartup("RUNNING");
            int queuedLost = 0;
            if (!mqTaskEnabled) {
                // 本地线程池模式：QUEUED 任务的执行请求随进程丢失
                queuedLost = agentTaskMapper.markInterruptedOnStartup("QUEUED");
            }
            if (interrupted + queuedLost > 0) {
                log.warn("启动对账：{} 个 RUNNING + {} 个 QUEUED 任务因服务重启标记为 INTERRUPTED（结果未知）",
                        interrupted, queuedLost);
            } else {
                log.info("启动对账完成：无遗留中断任务");
            }
        } catch (Exception e) {
            // 对账失败不阻断服务启动
            log.warn("启动对账失败（不影响服务启动）: {}", e.getMessage());
        }
    }
}
