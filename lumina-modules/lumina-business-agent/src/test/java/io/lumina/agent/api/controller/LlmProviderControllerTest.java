package io.lumina.agent.api.controller;

import io.lumina.agent.api.dto.llm.CreateLlmProviderDTO;
import io.lumina.agent.api.dto.llm.QueryLlmProviderDTO;
import io.lumina.agent.api.dto.llm.UpdateLlmProviderDTO;
import io.lumina.agent.api.vo.LlmProviderVO;
import io.lumina.agent.service.LlmProviderService;
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
 * LlmProviderController 单元测试
 *
 * @author Lumina Team
 * @since 3.3.1
 */
@ExtendWith(MockitoExtension.class)
class LlmProviderControllerTest {

    @Mock
    private LlmProviderService llmProviderService;

    @InjectMocks
    private LlmProviderController controller;

    @Test
    void listReturnsProviders() {
        QueryLlmProviderDTO query = new QueryLlmProviderDTO();
        when(llmProviderService.list(query)).thenReturn(List.of(createVO(1L)));

        R<List<LlmProviderVO>> result = controller.list(query);

        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0).getId()).isEqualTo(1L);
    }

    @Test
    void getByIdReturnsProvider() {
        when(llmProviderService.getById(1L)).thenReturn(createVO(1L));

        R<LlmProviderVO> result = controller.getById(1L);

        assertThat(result.getData().getId()).isEqualTo(1L);
    }

    @Test
    void createReturnsCreatedProvider() {
        CreateLlmProviderDTO dto = new CreateLlmProviderDTO();
        dto.setName("test");
        dto.setProvider("glm");
        when(llmProviderService.create(dto)).thenReturn(createVO(1L));

        R<LlmProviderVO> result = controller.create(dto);

        assertThat(result.getData().getId()).isEqualTo(1L);
    }

    @Test
    void updateReturnsUpdatedProvider() {
        UpdateLlmProviderDTO dto = new UpdateLlmProviderDTO();
        dto.setName("updated");
        when(llmProviderService.update(eq(1L), eq(dto))).thenReturn(createVO(1L));

        R<LlmProviderVO> result = controller.update(1L, dto);

        assertThat(result.getData().getId()).isEqualTo(1L);
    }

    @Test
    void deleteDelegatesToService() {
        controller.delete(1L);

        verify(llmProviderService).delete(1L);
    }

    @Test
    void testConnectionDelegatesToService() {
        when(llmProviderService.testConnection(1L)).thenReturn(true);

        R<Boolean> result = controller.testConnection(1L);

        assertThat(result.getData()).isTrue();
    }

    private LlmProviderVO createVO(Long id) {
        LlmProviderVO vo = new LlmProviderVO();
        vo.setId(id);
        vo.setName("test-provider");
        vo.setProvider("glm");
        vo.setStatus(1);
        vo.setPriority(100);
        return vo;
    }
}
