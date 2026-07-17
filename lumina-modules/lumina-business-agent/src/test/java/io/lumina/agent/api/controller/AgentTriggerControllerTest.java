package io.lumina.agent.api.controller;

import io.lumina.agent.api.dto.CreateAgentTriggerDTO;
import io.lumina.agent.api.vo.AgentTriggerVO;
import io.lumina.agent.service.AgentTriggerService;
import io.lumina.common.core.PageResult;
import io.lumina.common.core.R;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AgentTriggerController 单元测试
 *
 * @author Lumina Team
 * @since 3.5.0
 */
@ExtendWith(MockitoExtension.class)
class AgentTriggerControllerTest {

    @Mock
    private AgentTriggerService agentTriggerService;

    @InjectMocks
    private AgentTriggerController controller;

    @Test
    void createReturnsTrigger() {
        CreateAgentTriggerDTO dto = new CreateAgentTriggerDTO();
        dto.setName("daily-report");
        dto.setAgentId(100L);
        dto.setCronExpr("0 0 9 * * *");
        dto.setInputText("跑日报");
        when(agentTriggerService.createTrigger(dto)).thenReturn(createVO(1L));

        R<AgentTriggerVO> result = controller.createTrigger(dto);

        assertThat(result.getData().getId()).isEqualTo(1L);
    }

    @Test
    void pageReturnsTriggers() {
        PageResult<AgentTriggerVO> page = PageResult.of(List.of(createVO(1L), createVO(2L)), 2L, 1, 20);
        when(agentTriggerService.pageTriggers(1, 20)).thenReturn(page);

        R<PageResult<AgentTriggerVO>> result = controller.pageTriggers(1, 20);

        assertThat(result.getData().getList()).hasSize(2);
        assertThat(result.getData().getTotal()).isEqualTo(2L);
    }

    @Test
    void getReturnsTrigger() {
        when(agentTriggerService.getTrigger(1L)).thenReturn(createVO(1L));

        R<AgentTriggerVO> result = controller.getTrigger(1L);

        assertThat(result.getData().getId()).isEqualTo(1L);
    }

    @Test
    void deleteDelegatesToService() {
        controller.deleteTrigger(1L);
        verify(agentTriggerService).deleteTrigger(1L);
    }

    @Test
    void pauseDelegatesToService() {
        controller.pause(1L);
        verify(agentTriggerService).pause(1L);
    }

    @Test
    void resumeDelegatesToService() {
        controller.resume(1L);
        verify(agentTriggerService).resume(1L);
    }

    @Test
    void triggerNowReturnsSubmitted() {
        when(agentTriggerService.triggerNow(1L)).thenReturn(true);

        R<Map<String, Boolean>> result = controller.triggerNow(1L);

        assertThat(result.getData()).containsEntry("submitted", true);
    }

    private AgentTriggerVO createVO(Long id) {
        AgentTriggerVO vo = new AgentTriggerVO();
        vo.setId(id);
        vo.setName("daily-report");
        vo.setAgentId(100L);
        vo.setCronExpr("0 0 9 * * *");
        vo.setEnabled(1);
        return vo;
    }
}
