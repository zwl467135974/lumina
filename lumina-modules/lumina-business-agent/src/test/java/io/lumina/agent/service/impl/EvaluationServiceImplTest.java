package io.lumina.agent.service.impl;

import io.lumina.agent.api.dto.EvaluationDatasetDTO;
import io.lumina.agent.api.dto.EvaluationRunDTO;
import io.lumina.agent.engine.AgentExecutionEngine;
import io.lumina.agent.evaluation.model.EvaluationDataset;
import io.lumina.agent.evaluation.model.RunReport;
import io.lumina.agent.evaluation.model.ScoringMethod;
import io.lumina.agent.evaluation.scorer.ContainsScorer;
import io.lumina.agent.evaluation.scorer.ExactMatchScorer;
import io.lumina.agent.infrastructure.entity.AgentDO;
import io.lumina.agent.infrastructure.entity.EvaluationDatasetDO;
import io.lumina.agent.infrastructure.mapper.AgentMapper;
import io.lumina.agent.infrastructure.mapper.EvaluationDatasetMapper;
import io.lumina.agent.infrastructure.mapper.EvaluationRunMapper;
import io.lumina.agent.model.ExecuteResult;
import io.lumina.common.core.BaseContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * EvaluationServiceImpl 单元测试
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@ExtendWith(MockitoExtension.class)
class EvaluationServiceImplTest {

    @Mock
    private EvaluationDatasetMapper datasetMapper;

    @Mock
    private EvaluationRunMapper runMapper;

    @Mock
    private AgentMapper agentMapper;

    @Mock
    private AgentExecutionEngine agentExecutionEngine;

    private EvaluationServiceImpl evaluationService;

    @BeforeEach
    void setUp() {
        BaseContext.setTenantId(1L);
        BaseContext.setUserId(10L);
        java.util.concurrent.Executor directExecutor = Runnable::run;
        evaluationService = new EvaluationServiceImpl(datasetMapper, runMapper, agentMapper, agentExecutionEngine,
                List.of(new ExactMatchScorer(), new ContainsScorer()), directExecutor);
    }

    @AfterEach
    void tearDown() {
        BaseContext.clear();
    }

    @Test
    void createDatasetParsesYamlAndPersistsCaseCount() {
        EvaluationDatasetDTO dto = new EvaluationDatasetDTO();
        dto.setName("基础评估");
        dto.setCasesYaml("cases:\n  - id: c1\n    input: hello\n    expected: hi\n  - id: c2\n    input: ping\n    expected: pong\n");

        EvaluationDataset dataset = evaluationService.createDataset(dto);

        ArgumentCaptor<EvaluationDatasetDO> captor = ArgumentCaptor.forClass(EvaluationDatasetDO.class);
        org.mockito.Mockito.verify(datasetMapper).insert(captor.capture());
        assertThat(captor.getValue().getCaseCount()).isEqualTo(2);
        assertThat(captor.getValue().getTenantId()).isEqualTo(1L);
        assertThat(dataset.getCases()).hasSize(2);
    }

    @Test
    void runEvaluationExecutesCasesAndStoresReport() {
        EvaluationDatasetDO dataset = new EvaluationDatasetDO();
        dataset.setId(1L);
        dataset.setName("基础评估");
        dataset.setTenantId(1L);
        dataset.setIsDeleted(0);
        dataset.setCasesYaml("cases:\n  - id: c1\n    input: hello\n    expected: Lumina\n    category: basic\n");
        when(datasetMapper.selectById(1L)).thenReturn(dataset);

        AgentDO agent = new AgentDO();
        agent.setAgentId(2L);
        agent.setAgentType("assistant");
        when(agentMapper.selectById(2L)).thenReturn(agent);
        when(agentExecutionEngine.executeSync(eq("assistant"), eq("hello"), any(), isNull()))
                .thenReturn(ExecuteResult.success("Lumina response"));

        EvaluationRunDTO dto = new EvaluationRunDTO();
        dto.setAgentId(2L);
        dto.setScoringMethod(ScoringMethod.CONTAINS);
        dto.setThreshold(0.7);

        RunReport report = evaluationService.runEvaluation(1L, dto);

        assertThat(report.getTotalCases()).isEqualTo(1);
        assertThat(report.getPassedCases()).isEqualTo(1);
        assertThat(report.getResults().get(0).getScore()).isEqualTo(1.0);
        org.mockito.Mockito.verify(runMapper).insert(any(io.lumina.agent.infrastructure.entity.EvaluationRunDO.class));
    }
}
