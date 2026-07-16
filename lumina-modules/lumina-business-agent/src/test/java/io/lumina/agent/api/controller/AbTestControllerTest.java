package io.lumina.agent.api.controller;

import io.lumina.agent.api.dto.ab.CreateAbExperimentDTO;
import io.lumina.agent.api.vo.AbExperimentVO;
import io.lumina.agent.service.AbTestService;
import io.lumina.common.core.R;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * AbTestController 单元测试
 *
 * @author Lumina Team
 * @since 3.3.1
 */
@ExtendWith(MockitoExtension.class)
class AbTestControllerTest {

    @Mock
    private AbTestService abTestService;

    @InjectMocks
    private AbTestController controller;

    @Test
    void createReturnsExperiment() {
        CreateAbExperimentDTO dto = new CreateAbExperimentDTO();
        dto.setName("test-exp");
        dto.setAgentId(1L);
        when(abTestService.createExperiment(dto)).thenReturn(createVO(1L));

        R<AbExperimentVO> result = controller.create(dto);

        assertThat(result.getData().getId()).isEqualTo(1L);
    }

    @Test
    void getReturnsExperiment() {
        when(abTestService.getExperiment(1L)).thenReturn(createVO(1L));

        R<AbExperimentVO> result = controller.get(1L);

        assertThat(result.getData().getId()).isEqualTo(1L);
    }

    @Test
    void listReturnsExperiments() {
        when(abTestService.listExperiments(1L, "RUNNING"))
                .thenReturn(List.of(createVO(1L), createVO(2L)));

        R<List<AbExperimentVO>> result = controller.list(1L, "RUNNING");

        assertThat(result.getData()).hasSize(2);
    }

    @Test
    void startDelegatesToService() {
        controller.start(1L);
        verify(abTestService).startExperiment(1L);
    }

    @Test
    void pauseDelegatesToService() {
        controller.pause(1L);
        verify(abTestService).pauseExperiment(1L);
    }

    @Test
    void completeDelegatesToService() {
        controller.complete(1L);
        verify(abTestService).completeExperiment(1L);
    }

    @Test
    void deleteDelegatesToService() {
        controller.delete(1L);
        verify(abTestService).deleteExperiment(1L);
    }

    private AbExperimentVO createVO(Long id) {
        AbExperimentVO vo = new AbExperimentVO();
        vo.setId(id);
        vo.setName("test-exp");
        vo.setAgentId(1L);
        vo.setStatus("DRAFT");
        vo.setTrafficPercent(100);
        return vo;
    }
}
