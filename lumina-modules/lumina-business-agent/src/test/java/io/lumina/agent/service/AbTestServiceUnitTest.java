package io.lumina.agent.service;

import io.lumina.agent.api.dto.ab.CreateAbExperimentDTO;
import io.lumina.agent.api.dto.ab.CreateAbExperimentDTO.VariantDTO;
import io.lumina.agent.api.vo.AbExperimentVO;
import io.lumina.agent.infrastructure.entity.AbExperimentDO;
import io.lumina.agent.infrastructure.mapper.AbExperimentMapper;
import io.lumina.agent.infrastructure.mapper.AbExposureMapper;
import io.lumina.agent.infrastructure.mapper.AbVariantMapper;
import io.lumina.agent.service.impl.AbTestServiceImpl;
import io.lumina.common.core.BaseContext;
import io.lumina.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AbTestService 单元测试
 *
 * <p>覆盖 A/B 实验创建、状态流转、变体分配逻辑。
 *
 * @author Lumina Team
 * @since 3.6.0
 */
@ExtendWith(MockitoExtension.class)
class AbTestServiceUnitTest {

    @Mock
    private AbExperimentMapper abExperimentMapper;
    @Mock
    private AbVariantMapper abVariantMapper;
    @Mock
    private AbExposureMapper abExposureMapper;

    @InjectMocks
    private AbTestServiceImpl abTestService;

    @BeforeEach
    void setUp() {
        BaseContext.setTenantId(0L);
        BaseContext.setUserId(1L);
    }

    @AfterEach
    void tearDown() {
        BaseContext.clear();
    }

    @Test
    void createExperimentWithValidData() {
        CreateAbExperimentDTO dto = new CreateAbExperimentDTO();
        dto.setName("测试对比");
        dto.setAgentId(100L);
        dto.setTrafficPercent(100);

        VariantDTO v1 = new VariantDTO();
        v1.setName("A");
        v1.setWeight(50);
        v1.setLlmConfig("{\"modelType\":\"openai\"}");

        VariantDTO v2 = new VariantDTO();
        v2.setName("B");
        v2.setWeight(50);
        v2.setLlmConfig("{\"modelType\":\"openai\"}");

        dto.setVariants(List.of(v1, v2));

        // createExperiment 内部 insert 后调用 getExperiment(id) 查回
        AbExperimentDO created = new AbExperimentDO();
        created.setId(1L);
        created.setName("测试对比");
        created.setAgentId(100L);
        created.setStatus("DRAFT");
        created.setTenantId(0L);
        // getExperiment 用 selectById 查回
        when(abExperimentMapper.selectById(any())).thenReturn(created);
        when(abVariantMapper.selectList(any())).thenReturn(List.of());
        when(abExposureMapper.selectList(any())).thenReturn(List.of());

        AbExperimentVO result = abTestService.createExperiment(dto);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("测试对比");
        verify(abExperimentMapper).insert(any(AbExperimentDO.class));
    }

    @Test
    void startExperimentSetsRunningStatus() {
        AbExperimentDO experiment = new AbExperimentDO();
        experiment.setId(1L);
        experiment.setStatus("DRAFT");
        experiment.setTenantId(0L);
        when(abExperimentMapper.selectById(1L)).thenReturn(experiment);

        abTestService.startExperiment(1L);

        assertThat(experiment.getStatus()).isEqualTo("RUNNING");
        verify(abExperimentMapper).updateById(any(AbExperimentDO.class));
    }

    @Test
    void pauseExperimentSetsPausedStatus() {
        AbExperimentDO experiment = new AbExperimentDO();
        experiment.setId(1L);
        experiment.setStatus("RUNNING");
        experiment.setTenantId(0L);
        when(abExperimentMapper.selectById(1L)).thenReturn(experiment);

        abTestService.pauseExperiment(1L);

        assertThat(experiment.getStatus()).isEqualTo("PAUSED");
    }

    @Test
    void completeExperimentSetsCompletedStatus() {
        AbExperimentDO experiment = new AbExperimentDO();
        experiment.setId(1L);
        experiment.setStatus("RUNNING");
        experiment.setTenantId(0L);
        when(abExperimentMapper.selectById(1L)).thenReturn(experiment);

        abTestService.completeExperiment(1L);

        assertThat(experiment.getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void deleteExperimentRemovesRecord() {
        AbExperimentDO experiment = new AbExperimentDO();
        experiment.setId(1L);
        experiment.setTenantId(0L);
        when(abExperimentMapper.selectById(1L)).thenReturn(experiment);

        abTestService.deleteExperiment(1L);

        verify(abExperimentMapper).deleteById(1L);
    }
}
