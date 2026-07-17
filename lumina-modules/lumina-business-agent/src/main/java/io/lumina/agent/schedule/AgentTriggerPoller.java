package io.lumina.agent.schedule;

import io.lumina.agent.service.AgentTriggerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Agent 定时触发器扫描器
 *
 * <p>固定间隔扫描到期触发器（默认 30s），多实例重复触发由
 * {@code AgentTriggerServiceImpl} 内的 Redisson 分布式锁保证幂等。
 *
 * <p>可通过 {@code lumina.trigger.enabled=false} 关闭。
 *
 * @author Lumina Team
 * @since 3.5.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "lumina.trigger", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AgentTriggerPoller {

    private final AgentTriggerService agentTriggerService;

    @Scheduled(fixedDelayString = "${lumina.trigger.poll-interval-ms:30000}")
    public void poll() {
        try {
            agentTriggerService.fireDueTriggers();
        } catch (Throwable t) {
            log.error("定时触发器扫描异常", t);
        }
    }
}
