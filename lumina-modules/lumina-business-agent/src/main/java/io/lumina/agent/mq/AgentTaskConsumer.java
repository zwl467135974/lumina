package io.lumina.agent.mq;

import io.lumina.agent.service.impl.AgentTaskServiceImpl;
import io.lumina.common.core.BaseContext;
import io.lumina.common.core.LoginContext;
import io.lumina.framework.config.RocketMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Agent 异步任务消费者
 *
 * <p>接收 {@link AgentTaskMessage}，恢复登录上下文后调用 {@link AgentTaskServiceImpl#executeTask} 执行。
 *
 * <p>仅在 {@code rocketmq.consumer.agent-task.enabled=true} 时注册。
 * MQ 不可用时 AgentTaskServiceImpl 自动回退到本地线程池执行。
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "rocketmq.consumer.agent-task", name = "enabled", havingValue = "true", matchIfMissing = false)
@RocketMQMessageListener(
        consumerGroup = RocketMQConfig.GROUP_AGENT_TASK,
        topic = RocketMQConfig.TOPIC_AGENT_TASK
)
@RequiredArgsConstructor
public class AgentTaskConsumer implements RocketMQListener<AgentTaskMessage> {

    private final AgentTaskServiceImpl agentTaskService;

    @Override
    public void onMessage(AgentTaskMessage message) {
        log.info("消费 Agent 异步任务: {}", message);

        LoginContext loginContext = new LoginContext(
                message.getTenantId(),
                message.getUserId(),
                message.getUsername(),
                null,
                null
        );
        BaseContext.setCurrent(loginContext);

        try {
            agentTaskService.executeTask(message.getTaskUuid(), loginContext);
        } catch (Exception e) {
            log.error("Agent 异步任务消费失败: {}", message.getTaskUuid(), e);
        } finally {
            BaseContext.clear();
        }
    }
}
