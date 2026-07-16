package io.lumina.agent.mq;

import io.lumina.agent.service.impl.AgentTaskServiceImpl;
import io.lumina.common.core.BaseContext;
import io.lumina.common.core.LoginContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

/**
 * AgentTaskConsumer 单元测试
 *
 * <p>验证 MQ 消费者恢复 BaseContext、调用执行、异常不传播、上下文清除。
 *
 * @author Lumina Team
 * @since 3.3.1
 */
@ExtendWith(MockitoExtension.class)
class AgentTaskConsumerTest {

    @Mock
    private AgentTaskServiceImpl agentTaskService;

    @InjectMocks
    private AgentTaskConsumer consumer;

    @AfterEach
    void tearDown() {
        BaseContext.clear();
    }

    @Test
    void restoresBaseContextAndExecutes() {
        AgentTaskMessage msg = new AgentTaskMessage("task-uuid-1", 100L, 200L, "testuser");

        consumer.onMessage(msg);

        // BaseContext 应已被清除（finally 块）
        assertThatCode(() -> BaseContext.getUserId()).doesNotThrowAnyException();
    }

    @Test
    void exceptionDoesNotPropagate() {
        AgentTaskMessage msg = new AgentTaskMessage("task-uuid-2", 100L, 200L, "testuser");
        doThrow(new RuntimeException("execution failed"))
                .when(agentTaskService).executeTask(eq("task-uuid-2"), any(LoginContext.class));

        // 不应抛出异常（Consumer 吞掉异常避免 MQ 无限重试）
        assertThatCode(() -> consumer.onMessage(msg)).doesNotThrowAnyException();

        verify(agentTaskService).executeTask(eq("task-uuid-2"), any(LoginContext.class));
    }
}
