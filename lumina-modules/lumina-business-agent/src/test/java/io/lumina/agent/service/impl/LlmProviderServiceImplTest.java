package io.lumina.agent.service.impl;

import io.lumina.agent.api.dto.llm.QueryLlmProviderDTO;
import io.lumina.agent.api.vo.LlmProviderVO;
import io.lumina.agent.infrastructure.entity.LlmProviderDO;
import io.lumina.agent.infrastructure.mapper.LlmProviderMapper;
import io.lumina.agent.model.ChatModelFactory;
import io.lumina.common.core.BaseContext;
import io.lumina.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LlmProviderServiceImpl 单元测试
 *
 * <p>覆盖租户隔离校验：跨租户访问 LlmProvider 时抛出异常。
 *
 * @author Lumina Team
 * @since 1.1.0
 */
@ExtendWith(MockitoExtension.class)
class LlmProviderServiceImplTest {

    @InjectMocks
    private LlmProviderServiceImpl service;

    @Mock
    private LlmProviderMapper llmProviderMapper;

    @Mock
    private ChatModelFactory chatModelFactory;

    @BeforeEach
    void setUp() {
        BaseContext.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        BaseContext.clear();
    }

    @Test
    void getById_wrongTenant_throwsException() {
        LlmProviderDO provider = new LlmProviderDO();
        provider.setId(1L);
        provider.setTenantId(2L);
        when(llmProviderMapper.selectById(1L)).thenReturn(provider);

        assertThatThrownBy(() -> service.getById(1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void getById_sameTenant_success() {
        LlmProviderDO provider = new LlmProviderDO();
        provider.setId(1L);
        provider.setTenantId(1L);
        provider.setName("openai");
        provider.setProvider("openai");
        when(llmProviderMapper.selectById(1L)).thenReturn(provider);

        LlmProviderVO vo = service.getById(1L);

        assertThat(vo).isNotNull();
        assertThat(vo.getName()).isEqualTo("openai");
    }

    @Test
    void getById_notFound_throwsException() {
        when(llmProviderMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void delete_wrongTenant_throwsException() {
        LlmProviderDO provider = new LlmProviderDO();
        provider.setId(1L);
        provider.setTenantId(2L);
        when(llmProviderMapper.selectById(1L)).thenReturn(provider);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void delete_sameTenant_success() {
        LlmProviderDO provider = new LlmProviderDO();
        provider.setId(1L);
        provider.setTenantId(1L);
        when(llmProviderMapper.selectById(1L)).thenReturn(provider);

        service.delete(1L);

        verify(llmProviderMapper).deleteById(1L);
    }

    @Test
    void list_filtersByTenantId() {
        when(llmProviderMapper.selectList(any())).thenReturn(Collections.emptyList());

        service.list(new QueryLlmProviderDTO());

        verify(llmProviderMapper).selectList(any());
    }

    @Test
    void list_wrongTenantResult_isFilteredAtQuery() {
        BaseContext.setTenantId(1L);
        when(llmProviderMapper.selectList(any())).thenReturn(Collections.emptyList());

        var result = service.list(new QueryLlmProviderDTO());

        assertThat(result).isEmpty();
    }
}
