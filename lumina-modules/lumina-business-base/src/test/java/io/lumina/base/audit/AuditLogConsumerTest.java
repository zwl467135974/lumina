package io.lumina.base.audit;

import io.lumina.base.infrastructure.mapper.AuditLogMapper;
import io.lumina.framework.audit.event.AuditEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * AuditLogConsumer 单元测试
 *
 * <p>验证 MQ 消费者正确将 AuditEvent 持久化为 AuditLogDO。
 *
 * @author Lumina Team
 * @since 3.3.1
 */
@ExtendWith(MockitoExtension.class)
class AuditLogConsumerTest {

    @Mock
    private AuditLogMapper auditLogMapper;

    @InjectMocks
    private AuditLogConsumer consumer;

    @Test
    void onMessagePersistsAuditLog() {
        AuditEvent event = AuditEvent.builder()
                .module("agent")
                .action("CREATE")
                .description("创建 Agent")
                .targetType("AgentController")
                .targetId("42")
                .success(true)
                .durationMs(150L)
                .tenantId(100L)
                .userId(200L)
                .username("admin")
                .requestMethod("POST")
                .requestUrl("/api/v1/agents")
                .requestIp("192.168.1.1")
                .build();

        consumer.onMessage(event);

        ArgumentCaptor<io.lumina.base.infrastructure.entity.AuditLogDO> captor =
                ArgumentCaptor.forClass(io.lumina.base.infrastructure.entity.AuditLogDO.class);
        verify(auditLogMapper).insert(captor.capture());

        io.lumina.base.infrastructure.entity.AuditLogDO saved = captor.getValue();
        assertThat(saved.getModule()).isEqualTo("agent");
        assertThat(saved.getAction()).isEqualTo("CREATE");
        assertThat(saved.getTargetId()).isEqualTo("42");
        assertThat(saved.getStatus()).isEqualTo(1); // success
        assertThat(saved.getDurationMs()).isEqualTo(150L);
        assertThat(saved.getTenantId()).isEqualTo(100L);
        assertThat(saved.getUserId()).isEqualTo(200L);
        assertThat(saved.getUsername()).isEqualTo("admin");
    }

    @Test
    void failedEventRecordsStatusZero() {
        AuditEvent event = AuditEvent.builder()
                .module("user")
                .action("DELETE")
                .success(false)
                .errorMsg("权限不足")
                .durationMs(50L)
                .tenantId(1L)
                .build();

        consumer.onMessage(event);

        ArgumentCaptor<io.lumina.base.infrastructure.entity.AuditLogDO> captor =
                ArgumentCaptor.forClass(io.lumina.base.infrastructure.entity.AuditLogDO.class);
        verify(auditLogMapper).insert(captor.capture());

        assertThat(captor.getValue().getStatus()).isEqualTo(0); // failure
        assertThat(captor.getValue().getErrorMsg()).isEqualTo("权限不足");
    }

    @Test
    void persistenceFailureDoesNotPropagate() {
        AuditEvent event = AuditEvent.builder()
                .module("test")
                .action("TEST")
                .success(true)
                .build();

        doThrow(new RuntimeException("DB connection lost"))
                .when(auditLogMapper).insert(any(io.lumina.base.infrastructure.entity.AuditLogDO.class));

        // 持久化失败不应传播异常（Helper 内部 catch）
        org.assertj.core.api.Assertions.assertThatCode(() -> consumer.onMessage(event))
                .doesNotThrowAnyException();

        verify(auditLogMapper).insert(any(io.lumina.base.infrastructure.entity.AuditLogDO.class));
    }
}
