package io.lumina.agent.service;

import io.lumina.agent.infrastructure.mapper.AgentTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AgentTaskStartupReconciler} 实例隔离对账单测
 *
 * <p>核心性质：只回收心跳消失的死亡实例任务；存活实例任务不动；
 * 周期对账不碰存量 NULL 行；优雅停机标记本实例。
 *
 * @author Lumina Team
 * @since 3.12.1
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgentTaskStartupReconcilerTest {

    @Mock
    private AgentTaskMapper agentTaskMapper;

    @Mock
    private AgentInstanceRegistry instanceRegistry;

    private AgentTaskStartupReconciler reconciler;

    @BeforeEach
    void setUp() {
        reconciler = new AgentTaskStartupReconciler(agentTaskMapper, instanceRegistry);
        setField("mqTaskEnabled", false);
        setField("interruptNullInstance", true);
        setField("reconcileIntervalSeconds", 0L);
        when(instanceRegistry.selfId()).thenReturn("self000000000001");
    }

    @Test
    void deadInstanceTasksMarkedInterrupted() {
        when(agentTaskMapper.selectDistinctInstances("RUNNING")).thenReturn(List.of("dead01"));
        when(agentTaskMapper.selectDistinctInstances("QUEUED")).thenReturn(List.of());
        when(instanceRegistry.isAlive("dead01")).thenReturn(false);
        when(agentTaskMapper.markInterruptedForInstance(eq("RUNNING"), eq("dead01"), anyString()))
                .thenReturn(3);

        reconciler.reconcileDeadInstances(true);

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(agentTaskMapper).markInterruptedForInstance(eq("RUNNING"), eq("dead01"),
                message.capture());
        assertThat(message.getValue()).contains("心跳消失").contains("结果未知");
    }

    @Test
    void aliveInstanceTasksUntouched() {
        when(agentTaskMapper.selectDistinctInstances("RUNNING")).thenReturn(List.of("alive01"));
        when(agentTaskMapper.selectDistinctInstances("QUEUED")).thenReturn(List.of());
        when(instanceRegistry.isAlive("alive01")).thenReturn(true);

        reconciler.reconcileDeadInstances(true);

        // 存活实例：实例级标记绝不触发（存量 NULL 行回收与之独立，另行测试）
        verify(agentTaskMapper, never()).markInterruptedForInstance(anyString(), anyString(), anyString());
    }

    @Test
    void periodicReconcileSkipsLegacyNullRows() {
        when(agentTaskMapper.selectDistinctInstances("RUNNING")).thenReturn(List.of());
        when(agentTaskMapper.selectDistinctInstances("QUEUED")).thenReturn(List.of());

        reconciler.reconcileDeadInstances(false);

        verify(agentTaskMapper, never()).markInterruptedLegacyNull(anyString(), anyString());
    }

    @Test
    void legacyNullOnlyWhenEnabled() {
        when(agentTaskMapper.selectDistinctInstances("RUNNING")).thenReturn(List.of());
        when(agentTaskMapper.selectDistinctInstances("QUEUED")).thenReturn(List.of());
        setField("interruptNullInstance", false);

        reconciler.reconcileDeadInstances(true);

        verify(agentTaskMapper, never()).markInterruptedLegacyNull(anyString(), anyString());
    }

    @Test
    void mqModeSkipsQueuedTasks() {
        setField("mqTaskEnabled", true);
        when(agentTaskMapper.selectDistinctInstances("RUNNING")).thenReturn(List.of());

        reconciler.reconcileDeadInstances(true);

        verify(agentTaskMapper, never()).selectDistinctInstances("QUEUED");
    }

    @Test
    void shutdownMarksOwnInstanceTasks() {
        when(agentTaskMapper.markInterruptedForInstance(anyString(), anyString(), anyString()))
                .thenReturn(2);

        reconciler.shutdownReconcile();

        // 本地线程池模式：RUNNING 与 QUEUED 都标记为本实例停机
        verify(agentTaskMapper).markInterruptedForInstance(eq("RUNNING"), eq("self000000000001"), anyString());
        verify(agentTaskMapper).markInterruptedForInstance(eq("QUEUED"), eq("self000000000001"), anyString());
    }

    private void setField(String name, Object value) {
        try {
            java.lang.reflect.Field field = AgentTaskStartupReconciler.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(reconciler, value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
