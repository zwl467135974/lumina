package io.lumina.agent.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lumina.agent.engine.AgentExecutionEngine;
import io.lumina.agent.evaluation.scorer.ContainsScorer;
import io.lumina.agent.evaluation.scorer.ExactMatchScorer;
import io.lumina.agent.infrastructure.entity.AgentDO;
import io.lumina.agent.infrastructure.entity.EvaluationDatasetDO;
import io.lumina.agent.infrastructure.entity.EvaluationRunDO;
import io.lumina.agent.infrastructure.entity.PromptDO;
import io.lumina.agent.infrastructure.mapper.AgentMapper;
import io.lumina.agent.infrastructure.mapper.EvaluationDatasetMapper;
import io.lumina.agent.infrastructure.mapper.EvaluationRunMapper;
import io.lumina.agent.infrastructure.mapper.PromptMapper;
import io.lumina.agent.model.ExecuteResult;
import io.lumina.common.core.BaseContext;
import io.lumina.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 评估回归功能单元测试
 *
 * <p>验证 markBaseline（基线标记）、comparePromptVersions（版本对比）。
 *
 * @author Lumina Team
 * @since 3.3.0
 */
@ExtendWith(MockitoExtension.class)
class EvaluationRegressionTest {

    @Mock
    private EvaluationDatasetMapper datasetMapper;
    @Mock
    private EvaluationRunMapper runMapper;
    @Mock
    private AgentMapper agentMapper;
    @Mock
    private AgentExecutionEngine agentExecutionEngine;
    @Mock
    private PromptMapper promptMapper;

    private EvaluationServiceImpl service;

    @BeforeEach
    void setUp() {
        BaseContext.setTenantId(1L);
        BaseContext.setUserId(10L);
        java.util.concurrent.Executor directExecutor = Runnable::run;
        service = new EvaluationServiceImpl(datasetMapper, runMapper, agentMapper, agentExecutionEngine,
                List.of(new ExactMatchScorer(), new ContainsScorer()), directExecutor, null, new ObjectMapper());
        // 注入 promptMapper（@Autowired 字段）
        org.springframework.test.util.ReflectionTestUtils.setField(service, "promptMapper", promptMapper);
    }

    @AfterEach
    void tearDown() {
        BaseContext.clear();
    }

    @Test
    void markBaselineSetsFlagAndClearsOthers() {
        EvaluationRunDO run = new EvaluationRunDO();
        run.setId(5L);
        run.setDatasetId(10L);
        run.setIsBaseline(0);
        when(runMapper.selectById(5L)).thenReturn(run);

        service.markBaseline(5L);

        // 验证清除旧基线
        verify(runMapper).update(any(EvaluationRunDO.class), any());
        // 验证标记新基线
        ArgumentCaptor<EvaluationRunDO> captor = ArgumentCaptor.forClass(EvaluationRunDO.class);
        verify(runMapper).updateById(captor.capture());
        assertThat(captor.getValue().getIsBaseline()).isEqualTo(1);
    }

    @Test
    void markBaselineThrowsWhenRunNotFound() {
        when(runMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.markBaseline(999L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void comparePromptVersionsReturnsDiff() {
        PromptDO v1 = new PromptDO();
        v1.setName("test-prompt");
        v1.setVersion(1);
        v1.setContent("line 1\nline 2\nline 3");

        PromptDO v2 = new PromptDO();
        v2.setName("test-prompt");
        v2.setVersion(2);
        v2.setContent("line 1\nline 2 modified\nline 3\nline 4");

        when(promptMapper.selectOne(any())).thenReturn(v1, v2);

        var result = service.comparePromptVersions("test-prompt", 1, 2);

        assertThat(result).containsKey("diffLines");
        assertThat(result).containsKey("totalChanges");
        @SuppressWarnings("unchecked")
        List<?> diffLines = (List<?>) result.get("diffLines");
        // line 2 改了（MODIFIED），line 4 加了（ADDED）
        assertThat(diffLines).hasSizeGreaterThanOrEqualTo(1);
        assertThat((int) result.get("totalChanges")).isGreaterThanOrEqualTo(1);
    }

    @Test
    void comparePromptVersionsThrowsWhenVersionMissing() {
        when(promptMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.comparePromptVersions("missing", 1, 2))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void compareIdenticalVersionsReturnsEmptyDiff() {
        PromptDO v1 = new PromptDO();
        v1.setName("test-prompt");
        v1.setVersion(1);
        v1.setContent("same\ncontent\nhere");

        PromptDO v2 = new PromptDO();
        v2.setName("test-prompt");
        v2.setVersion(2);
        v2.setContent("same\ncontent\nhere");

        when(promptMapper.selectOne(any())).thenReturn(v1, v2);

        var result = service.comparePromptVersions("test-prompt", 1, 2);
        assertThat((int) result.get("totalChanges")).isEqualTo(0);
    }
}
