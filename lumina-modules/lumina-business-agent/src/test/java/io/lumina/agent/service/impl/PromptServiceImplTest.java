package io.lumina.agent.service.impl;

import io.lumina.agent.infrastructure.entity.PromptDO;
import io.lumina.agent.infrastructure.mapper.PromptMapper;
import io.lumina.common.core.BaseContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PromptServiceImpl 单元测试
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@ExtendWith(MockitoExtension.class)
class PromptServiceImplTest {

    @InjectMocks
    private PromptServiceImpl promptService;

    @Mock
    private PromptMapper promptMapper;

    @AfterEach
    void tearDown() {
        BaseContext.clear();
    }

    @Test
    void getActiveFallsBackToGlobalPrompt() {
        BaseContext.setTenantId(1L);
        PromptDO globalPrompt = new PromptDO();
        globalPrompt.setName("react");
        globalPrompt.setTenantId(0L);
        globalPrompt.setContent("global prompt");

        when(promptMapper.selectOne(any()))
                .thenReturn(null)
                .thenReturn(globalPrompt);

        PromptDO result = promptService.getActive("react");

        assertThat(result).isSameAs(globalPrompt);
        verify(promptMapper, times(2)).selectOne(any());
    }

    @Test
    void getActivePrefersTenantPrompt() {
        BaseContext.setTenantId(1L);
        PromptDO tenantPrompt = new PromptDO();
        tenantPrompt.setName("react");
        tenantPrompt.setTenantId(1L);
        tenantPrompt.setContent("tenant prompt");

        when(promptMapper.selectOne(any())).thenReturn(tenantPrompt);

        PromptDO result = promptService.getActive("react");

        assertThat(result).isSameAs(tenantPrompt);
        verify(promptMapper, times(1)).selectOne(any());
    }
}
