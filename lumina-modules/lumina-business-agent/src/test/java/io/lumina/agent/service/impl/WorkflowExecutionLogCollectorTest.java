package io.lumina.agent.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lumina.agent.infrastructure.entity.WorkflowExecutionLogDO;
import io.lumina.agent.infrastructure.mapper.WorkflowExecutionLogMapper;
import io.lumina.agent.orchestration.model.AutonomyNode;
import io.lumina.agent.orchestration.model.WorkflowContext;
import io.lumina.agent.orchestration.model.WorkflowDefinition;
import io.lumina.agent.orchestration.model.WorkflowNode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

/**
 * 执行日志收集器的审批投影/报告行接线测试（v3.14 批次 3.3）
 *
 * <p>覆盖：autonomy 节点启动落 PLAN 行（含阶段序列与子调用面）、
 * 非 autonomy 节点不落、无定义不落、REPORT 行状态与数据、RUNNING 未闭合转 SKIPPED。
 *
 * @author Lumina Team
 * @since 3.14.0
 */
class WorkflowExecutionLogCollectorTest {

    private final WorkflowExecutionLogMapper logMapper = Mockito.mock(WorkflowExecutionLogMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private WorkflowDefinition definitionWithAutonomy() {
        AutonomyNode autonomy = new AutonomyNode();
        autonomy.setId("auto1");
        autonomy.setName("自主编排");
        autonomy.setAgentId(9L);
        autonomy.setScript("phase('检索资料');\nphase('汇总产出');\nconst r = agent('查一下');\nreturn r");
        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setName("wf");
        definition.setNodes(List.of(autonomy));
        return definition;
    }

    @Test
    void autonomyNodeStartWritesPlanRowWithStages() {
        WorkflowServiceImpl.ExecutionLogCollector collector =
                new WorkflowServiceImpl.ExecutionLogCollector(1L, logMapper, objectMapper, definitionWithAutonomy());

        collector.onNodeStarted("auto1", "自主编排", new WorkflowContext());
        collector.onNodeCompleted("auto1", "完成", 50L);
        collector.flush();

        ArgumentCaptor<WorkflowExecutionLogDO> captor = ArgumentCaptor.forClass(WorkflowExecutionLogDO.class);
        verify(logMapper, Mockito.times(2)).insert(captor.capture());
        List<WorkflowExecutionLogDO> rows = captor.getAllValues();
        assertThat(rows).extracting(WorkflowExecutionLogDO::getStatus)
                .containsExactly("COMPLETED", "PLAN");
        WorkflowExecutionLogDO planRow = rows.get(1);
        assertThat(planRow.getNodeName()).isEqualTo("执行计划");
        assertThat(planRow.getOutput()).contains("检索资料", "汇总产出", "查一下");
    }

    @Test
    void nonAutonomyNodeWritesNoPlanRow() {
        io.lumina.agent.orchestration.model.AgentNode agentNode = new io.lumina.agent.orchestration.model.AgentNode();
        agentNode.setId("a1");
        agentNode.setName("普通节点");
        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setNodes(List.of(agentNode));
        WorkflowServiceImpl.ExecutionLogCollector collector =
                new WorkflowServiceImpl.ExecutionLogCollector(1L, logMapper, objectMapper, definition);

        collector.onNodeStarted("a1", "普通节点", new WorkflowContext());
        collector.onNodeCompleted("a1", "完成", 10L);
        collector.flush();

        ArgumentCaptor<WorkflowExecutionLogDO> captor = ArgumentCaptor.forClass(WorkflowExecutionLogDO.class);
        verify(logMapper, Mockito.times(1)).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void nullDefinitionWritesNoPlanRow() {
        WorkflowServiceImpl.ExecutionLogCollector collector =
                new WorkflowServiceImpl.ExecutionLogCollector(1L, logMapper, objectMapper);

        collector.onNodeStarted("auto1", "自主编排", new WorkflowContext());
        collector.flush();

        verify(logMapper, Mockito.times(1)).insert(any(WorkflowExecutionLogDO.class));
    }

    @Test
    void autonomyReportWritesTypedRow() {
        WorkflowServiceImpl.ExecutionLogCollector collector =
                new WorkflowServiceImpl.ExecutionLogCollector(1L, logMapper, objectMapper, null);

        collector.onAutonomyReport(new io.lumina.agent.orchestration.model.AutonomyReportEvent(
                "auto1", "metrics", "核心指标", "[{\"label\":\"qps\",\"value\":12}]", 2, 1L));
        collector.flush();

        ArgumentCaptor<WorkflowExecutionLogDO> captor = ArgumentCaptor.forClass(WorkflowExecutionLogDO.class);
        verify(logMapper).insert(captor.capture());
        WorkflowExecutionLogDO row = captor.getValue();
        assertThat(row.getStatus()).isEqualTo("REPORT:metrics#2");
        assertThat(row.getNodeName()).isEqualTo("核心指标");
        assertThat(row.getOutput()).contains("qps");
        assertThat(row.getNodeId()).isEqualTo("auto1");
    }

    @Test
    void phaseAndReportRowsDoNotDisturbNodeCompletion() {
        WorkflowServiceImpl.ExecutionLogCollector collector =
                new WorkflowServiceImpl.ExecutionLogCollector(1L, logMapper, objectMapper, null);
        collector.onNodeStarted("auto1", "自主编排", new WorkflowContext());
        collector.onAutonomyPhase(new io.lumina.agent.orchestration.model.AutonomyPhaseEvent(
                "auto1", "检索资料", 1, 1L));
        collector.onAutonomyReport(new io.lumina.agent.orchestration.model.AutonomyReportEvent(
                "auto1", "table", "结果", "{\"columns\":[]}", 1, 2L));
        collector.onNodeCompleted("auto1", "完成", 100L);
        collector.flush();

        ArgumentCaptor<WorkflowExecutionLogDO> captor = ArgumentCaptor.forClass(WorkflowExecutionLogDO.class);
        verify(logMapper, Mockito.times(3)).insert(captor.capture());
        // RUNNING 行被正确回填为 COMPLETED（PHASE/REPORT 行不干扰 findPending 匹配）
        assertThat(captor.getAllValues()).extracting(WorkflowExecutionLogDO::getStatus)
                .containsExactly("COMPLETED", "PHASE", "REPORT:table#1");
        assertThat(captor.getAllValues().get(0).getOutput()).contains("完成");
    }

    @Test
    void unfinishedRunningRowFlushedAsSkipped() {
        WorkflowServiceImpl.ExecutionLogCollector collector =
                new WorkflowServiceImpl.ExecutionLogCollector(1L, logMapper, objectMapper, null);
        WorkflowNode node = new io.lumina.agent.orchestration.model.AgentNode();
        node.setId("a1");
        collector.onNodeStarted("a1", "节点", new WorkflowContext());
        collector.flush();

        ArgumentCaptor<WorkflowExecutionLogDO> captor = ArgumentCaptor.forClass(WorkflowExecutionLogDO.class);
        verify(logMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("SKIPPED");
    }
}
