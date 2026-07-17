package io.lumina.agent.service.impl;

import io.lumina.agent.api.dto.CreateAgentTriggerDTO;
import io.lumina.agent.api.vo.AgentTriggerVO;
import io.lumina.agent.domain.model.Agent;
import io.lumina.agent.infrastructure.entity.AgentTaskDO;
import io.lumina.agent.infrastructure.entity.AgentTriggerDO;
import io.lumina.agent.infrastructure.mapper.AgentTaskMapper;
import io.lumina.agent.infrastructure.mapper.AgentTriggerMapper;
import io.lumina.agent.service.AgentService;
import io.lumina.agent.service.AgentTaskService;
import io.lumina.common.core.BaseContext;
import io.lumina.common.core.LoginContext;
import io.lumina.common.exception.BusinessException;
import io.lumina.notification.event.NotificationEvent;
import io.lumina.notification.event.NotificationEventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AgentTriggerServiceImpl 单元测试
 *
 * @author Lumina Team
 * @since 3.5.0
 */
@ExtendWith(MockitoExtension.class)
class AgentTriggerServiceImplTest {

    @Mock
    private AgentTriggerMapper agentTriggerMapper;

    @Mock
    private AgentTaskMapper agentTaskMapper;

    @Mock
    private AgentTaskService agentTaskService;

    @Mock
    private AgentService agentService;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock lock;

    @Mock
    private NotificationEventPublisher notificationEventPublisher;

    private AgentTriggerServiceImpl triggerService;

    /**
     * 直接同步执行的 Executor，便于断言 triggerNow 行为
     */
    private final Executor directExecutor = Runnable::run;

    @BeforeEach
    void setUp() {
        triggerService = new AgentTriggerServiceImpl(
                agentTriggerMapper, agentTaskMapper, agentTaskService, agentService,
                redissonClient, notificationEventPublisher, directExecutor);
    }

    @AfterEach
    void tearDown() {
        BaseContext.clear();
    }

    // ==================== createTrigger ====================

    @Test
    void createTriggerComputesNextFireAt() {
        BaseContext.setTenantId(1L);
        BaseContext.setUserId(10L);
        Agent agent = new Agent();
        agent.setStatus(1);
        when(agentService.getAgentById(100L)).thenReturn(agent);
        when(agentTriggerMapper.insert(any(AgentTriggerDO.class))).thenAnswer(inv -> {
            ((AgentTriggerDO) inv.getArgument(0)).setId(1L);
            return 1;
        });

        CreateAgentTriggerDTO dto = new CreateAgentTriggerDTO();
        dto.setName("daily-report");
        dto.setAgentId(100L);
        dto.setCronExpr("0 0 9 * * *");
        dto.setInputText("跑日报");

        AgentTriggerVO vo = triggerService.createTrigger(dto);

        ArgumentCaptor<AgentTriggerDO> captor = ArgumentCaptor.forClass(AgentTriggerDO.class);
        verify(agentTriggerMapper).insert(captor.capture());
        AgentTriggerDO saved = captor.getValue();
        assertThat(saved.getEnabled()).isEqualTo(1);
        assertThat(saved.getMisfirePolicy()).isEqualTo("FIRE_ONCE");
        assertThat(saved.getNextFireAt()).isAfter(LocalDateTime.now());
        assertThat(saved.getTenantId()).isEqualTo(1L);
        assertThat(saved.getCreateBy()).isEqualTo(10L);
        assertThat(vo.getNextFireAtDescription()).startsWith("下次：");
    }

    @Test
    void createTriggerRejectsInvalidCron() {
        CreateAgentTriggerDTO dto = new CreateAgentTriggerDTO();
        dto.setName("bad");
        dto.setAgentId(100L);
        // Quartz 7 字段格式，Spring CronExpression 不接受
        dto.setCronExpr("0 0 9 * * ? *");
        dto.setInputText("x");

        assertThatThrownBy(() -> triggerService.createTrigger(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cron");
        verify(agentTriggerMapper, never()).insert(any(AgentTriggerDO.class));
    }

    @Test
    void createTriggerRejectsInactiveAgent() {
        Agent agent = new Agent();
        agent.setStatus(0);
        when(agentService.getAgentById(100L)).thenReturn(agent);

        CreateAgentTriggerDTO dto = new CreateAgentTriggerDTO();
        dto.setName("t");
        dto.setAgentId(100L);
        dto.setCronExpr("0 0 9 * * *");
        dto.setInputText("x");

        assertThatThrownBy(() -> triggerService.createTrigger(dto))
                .isInstanceOf(BusinessException.class);
        verify(agentTriggerMapper, never()).insert(any(AgentTriggerDO.class));
    }

    // ==================== fireDueTriggers ====================

    @Test
    void fireDueTriggersOnlyScansDue() {
        when(agentTriggerMapper.selectDueTriggers()).thenReturn(List.of());

        triggerService.fireDueTriggers();

        verify(agentTriggerMapper).selectDueTriggers();
        verify(redissonClient, never()).getLock(anyString());
    }

    @Test
    void fireDueTriggersSuccessUpdatesFireTimes() throws Exception {
        AgentTriggerDO trigger = dueTrigger();
        when(agentTriggerMapper.selectDueTriggers()).thenReturn(List.of(trigger));
        when(redissonClient.getLock("lumina:trigger:fire:1")).thenReturn(lock);
        when(lock.tryLock(0, 60, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        stubTaskCompleted("COMPLETED");

        triggerService.fireDueTriggers();

        verify(agentTaskService).executeTask(anyString(), any(LoginContext.class));
        verify(agentTriggerMapper).updateFired(eq(1L), eq("SUCCESS"), any(LocalDateTime.class));
        verify(lock).unlock();
    }

    @Test
    void fireDueTriggersSkipsWhenLockHeldByOtherInstance() throws Exception {
        AgentTriggerDO trigger = dueTrigger();
        when(agentTriggerMapper.selectDueTriggers()).thenReturn(List.of(trigger));
        when(redissonClient.getLock("lumina:trigger:fire:1")).thenReturn(lock);
        when(lock.tryLock(0, 60, TimeUnit.SECONDS)).thenReturn(false);

        triggerService.fireDueTriggers();

        verify(agentTaskService, never()).executeTask(anyString(), any());
        verify(agentTaskMapper, never()).insert(any(AgentTaskDO.class));
        verify(lock, never()).unlock();
    }

    @Test
    void fireFailureIncrementsFailCount() throws Exception {
        AgentTriggerDO trigger = dueTrigger();
        trigger.setFailCount(2);
        when(agentTriggerMapper.selectDueTriggers()).thenReturn(List.of(trigger));
        when(redissonClient.getLock("lumina:trigger:fire:1")).thenReturn(lock);
        when(lock.tryLock(0, 60, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        stubTaskCompleted("FAILED");

        triggerService.fireDueTriggers();

        // failCount 2 -> 3，未达 5 保持 enabled=1
        verify(agentTriggerMapper).updateFireFailed(eq(1L), eq(3), any(), any(), eq(1));
        verify(notificationEventPublisher, never()).publish(any(NotificationEvent.class));
    }

    @Test
    void fiveConsecutiveFailuresDisableTrigger() throws Exception {
        AgentTriggerDO trigger = dueTrigger();
        trigger.setFailCount(4);
        when(agentTriggerMapper.selectDueTriggers()).thenReturn(List.of(trigger));
        when(redissonClient.getLock("lumina:trigger:fire:1")).thenReturn(lock);
        when(lock.tryLock(0, 60, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        stubTaskCompleted("FAILED");

        triggerService.fireDueTriggers();

        // failCount 4 -> 5，自动禁用并发通知
        verify(agentTriggerMapper).updateFireFailed(eq(1L), eq(5), any(), any(), eq(0));
        verify(notificationEventPublisher).publish(any(NotificationEvent.class));
    }

    @Test
    void misfireSkipPolicyAdvancesWithoutFiring() {
        AgentTriggerDO trigger = dueTrigger();
        trigger.setMisfirePolicy("SKIP");
        // 错过超过 1 小时阈值
        trigger.setNextFireAt(LocalDateTime.now().minusHours(2));
        when(agentTriggerMapper.selectDueTriggers()).thenReturn(List.of(trigger));

        triggerService.fireDueTriggers();

        verify(agentTriggerMapper).updateNextFireAt(eq(1L), any(LocalDateTime.class));
        verify(redissonClient, never()).getLock(anyString());
        verify(agentTaskService, never()).executeTask(anyString(), any());
    }

    @Test
    void misfireFireOncePolicyStillFires() throws Exception {
        AgentTriggerDO trigger = dueTrigger();
        trigger.setMisfirePolicy("FIRE_ONCE");
        trigger.setNextFireAt(LocalDateTime.now().minusHours(2));
        when(agentTriggerMapper.selectDueTriggers()).thenReturn(List.of(trigger));
        when(redissonClient.getLock("lumina:trigger:fire:1")).thenReturn(lock);
        when(lock.tryLock(0, 60, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        stubTaskCompleted("COMPLETED");

        triggerService.fireDueTriggers();

        verify(agentTaskService).executeTask(anyString(), any(LoginContext.class));
        verify(agentTriggerMapper).updateFired(eq(1L), eq("SUCCESS"), any(LocalDateTime.class));
    }

    @Test
    void fireInsertsTaskWithTriggerIdAndSystemContext() throws Exception {
        AgentTriggerDO trigger = dueTrigger();
        when(agentTriggerMapper.selectDueTriggers()).thenReturn(List.of(trigger));
        when(redissonClient.getLock("lumina:trigger:fire:1")).thenReturn(lock);
        when(lock.tryLock(0, 60, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        stubTaskCompleted("COMPLETED");

        triggerService.fireDueTriggers();

        ArgumentCaptor<AgentTaskDO> taskCaptor = ArgumentCaptor.forClass(AgentTaskDO.class);
        verify(agentTaskMapper).insert(taskCaptor.capture());
        AgentTaskDO task = taskCaptor.getValue();
        assertThat(task.getTriggerId()).isEqualTo(1L);
        assertThat(task.getAgentId()).isEqualTo(100L);
        assertThat(task.getStatus()).isEqualTo("QUEUED");
        assertThat(task.getTenantId()).isEqualTo(2L);

        ArgumentCaptor<LoginContext> ctxCaptor = ArgumentCaptor.forClass(LoginContext.class);
        verify(agentTaskService).executeTask(anyString(), ctxCaptor.capture());
        assertThat(ctxCaptor.getValue().username()).isEqualTo("system");
        assertThat(ctxCaptor.getValue().tenantId()).isEqualTo(2L);
        // fireInternal finally 中清空上下文
        assertThat(BaseContext.getTenantId()).isNull();
    }

    // ==================== pause / resume / triggerNow ====================

    @Test
    void pauseDisablesTrigger() {
        AgentTriggerDO trigger = dueTrigger();
        when(agentTriggerMapper.selectById(1L)).thenReturn(trigger);

        triggerService.pause(1L);

        ArgumentCaptor<AgentTriggerDO> captor = ArgumentCaptor.forClass(AgentTriggerDO.class);
        verify(agentTriggerMapper).updateById(captor.capture());
        assertThat(captor.getValue().getEnabled()).isEqualTo(0);
    }

    @Test
    void resumeRecomputesNextFireAt() {
        AgentTriggerDO trigger = dueTrigger();
        trigger.setEnabled(0);
        trigger.setNextFireAt(LocalDateTime.now().minusDays(3));
        when(agentTriggerMapper.selectById(1L)).thenReturn(trigger);

        triggerService.resume(1L);

        ArgumentCaptor<AgentTriggerDO> captor = ArgumentCaptor.forClass(AgentTriggerDO.class);
        verify(agentTriggerMapper).updateById(captor.capture());
        assertThat(captor.getValue().getEnabled()).isEqualTo(1);
        assertThat(captor.getValue().getNextFireAt()).isAfter(LocalDateTime.now());
        assertThat(captor.getValue().getFailCount()).isZero();
    }

    @Test
    void triggerNowBypassesLockAndFires() {
        AgentTriggerDO trigger = dueTrigger();
        when(agentTriggerMapper.selectById(1L)).thenReturn(trigger);
        stubTaskCompleted("COMPLETED");

        boolean submitted = triggerService.triggerNow(1L);

        assertThat(submitted).isTrue();
        // 手动触发不走 Redisson 锁
        verify(redissonClient, never()).getLock(anyString());
        verify(agentTaskService).executeTask(anyString(), any(LoginContext.class));
    }

    @Test
    void triggerNowNotFoundThrows() {
        when(agentTriggerMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> triggerService.triggerNow(99L))
                .isInstanceOf(BusinessException.class);
    }

    // ==================== 辅助 ====================

    private AgentTriggerDO dueTrigger() {
        AgentTriggerDO trigger = new AgentTriggerDO();
        trigger.setId(1L);
        trigger.setName("daily-report");
        trigger.setAgentId(100L);
        trigger.setCronExpr("0 0 9 * * *");
        trigger.setInputText("跑日报");
        trigger.setMisfirePolicy("FIRE_ONCE");
        trigger.setEnabled(1);
        trigger.setNextFireAt(LocalDateTime.now().minusSeconds(10));
        trigger.setFailCount(0);
        trigger.setTenantId(2L);
        trigger.setCreateBy(10L);
        trigger.setIsDeleted(0);
        return trigger;
    }

    /**
     * stub executeTask 后任务终态查询
     */
    private void stubTaskCompleted(String finalStatus) {
        lenient().when(agentTaskMapper.insert(any(AgentTaskDO.class))).thenAnswer(inv -> {
            ((AgentTaskDO) inv.getArgument(0)).setId(9L);
            return 1;
        });
        lenient().when(agentTaskMapper.selectOne(any())).thenAnswer(inv -> {
            AgentTaskDO task = new AgentTaskDO();
            task.setId(9L);
            task.setStatus(finalStatus);
            if ("FAILED".equals(finalStatus)) {
                task.setErrorMessage("LLM error");
            }
            return task;
        });
    }
}
