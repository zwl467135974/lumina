package io.lumina.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.lumina.agent.api.dto.llm.CreateLlmProviderDTO;
import io.lumina.agent.api.dto.llm.QueryLlmProviderDTO;
import io.lumina.agent.api.dto.llm.UpdateLlmProviderDTO;
import io.lumina.agent.api.vo.LlmProviderVO;
import io.lumina.agent.config.LuminaAgentProperties;
import io.lumina.agent.domain.model.LlmProvider;
import io.lumina.agent.infrastructure.entity.LlmProviderDO;
import io.lumina.agent.infrastructure.mapper.LlmProviderMapper;
import io.lumina.agent.model.AgentConfig;
import io.lumina.agent.model.ChatModelFactory;
import io.lumina.agent.service.LlmProviderService;
import io.lumina.common.core.BaseContext;
import io.lumina.common.core.ErrorCode;
import io.lumina.common.exception.BusinessException;
import io.lumina.common.util.CryptoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmProviderServiceImpl implements LlmProviderService {

    private final LlmProviderMapper llmProviderMapper;

    private final ChatModelFactory chatModelFactory;

    @Override
    public LlmProviderVO getById(Long id) {
        LlmProvider provider = getDomainById(id);
        return toVO(provider);
    }

    @Override
    public List<LlmProviderVO> list(QueryLlmProviderDTO query) {
        LambdaQueryWrapper<LlmProviderDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LlmProviderDO::getTenantId, currentTenantId());
        if (StringUtils.hasText(query.getName())) {
            wrapper.like(LlmProviderDO::getName, query.getName());
        }
        if (StringUtils.hasText(query.getProvider())) {
            wrapper.eq(LlmProviderDO::getProvider, query.getProvider());
        }
        if (query.getStatus() != null) {
            wrapper.eq(LlmProviderDO::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(LlmProviderDO::getCreateTime);
        return llmProviderMapper.selectList(wrapper).stream()
                .map(this::toDomain)
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LlmProviderVO create(CreateLlmProviderDTO dto) {
        LlmProvider provider = new LlmProvider();
        BeanUtils.copyProperties(dto, provider);
        provider.setApiKeyFromPlain(dto.getApiKey());
        if (dto.getStatus() == null) {
            provider.setStatus(1);
        }
        provider.setTenantId(currentTenantId());

        provider.validateName();
        provider.validateProvider();

        LlmProviderDO entity = toDO(provider);
        llmProviderMapper.insert(entity);
        provider = toDomain(entity);
        return toVO(provider);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LlmProviderVO update(Long id, UpdateLlmProviderDTO dto) {
        LlmProvider provider = getDomainById(id);

        provider.setName(dto.getName());
        provider.setBaseUrl(dto.getBaseUrl());
        provider.setDefaultModel(dto.getDefaultModel());
        provider.setDefaultParams(dto.getDefaultParams());
        if (dto.getStatus() != null) {
            provider.setStatus(dto.getStatus());
        }
        if (StringUtils.hasText(dto.getApiKey())) {
            provider.setApiKeyFromPlain(dto.getApiKey());
        }

        provider.validateName();

        LlmProviderDO entity = toDO(provider);
        llmProviderMapper.updateById(entity);
        return toVO(provider);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        getDomainById(id);
        llmProviderMapper.deleteById(id);
    }

    @Override
    public boolean testConnection(Long id) {
        LlmProvider provider = getDomainById(id);
        String apiKey = provider.getDecryptedApiKey();
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException(ErrorCode.LLM_PROVIDER_API_KEY_MISSING);
        }
        try {
            AgentConfig.LLMConfig config = new AgentConfig.LLMConfig();
            config.setModelType(provider.getProvider());
            config.setApiKey(apiKey);
            config.setBaseUrl(provider.getBaseUrl());
            config.setModelName(provider.getDefaultModel());
            LuminaAgentProperties.LLMConfig defaults = new LuminaAgentProperties.LLMConfig();
            defaults.setType(provider.getProvider());
            chatModelFactory.create(config, defaults, apiKey);
            return true;
        } catch (Exception e) {
            log.error("LLM Provider 连通性测试失败: provider={}, error={}", provider.getProvider(), e.getMessage());
            throw new BusinessException(ErrorCode.LLM_PROVIDER_TEST_FAILED, "连通性测试失败: " + e.getMessage());
        }
    }

    @Override
    public String getDecryptedApiKey(Long id) {
        LlmProvider provider = getDomainById(id);
        return provider.getDecryptedApiKey();
    }

    private LlmProvider getDomainById(Long id) {
        LlmProviderDO entity = llmProviderMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.LLM_PROVIDER_NOT_FOUND);
        }
        if (!currentTenantId().equals(entity.getTenantId())) {
            throw new BusinessException(ErrorCode.LLM_PROVIDER_NOT_FOUND);
        }
        return toDomain(entity);
    }

    private Long currentTenantId() {
        return BaseContext.getTenantId() != null ? BaseContext.getTenantId() : 0L;
    }

    private LlmProviderDO toDO(LlmProvider provider) {
        LlmProviderDO entity = new LlmProviderDO();
        BeanUtils.copyProperties(provider, entity);
        return entity;
    }

    private LlmProvider toDomain(LlmProviderDO entity) {
        LlmProvider provider = new LlmProvider();
        BeanUtils.copyProperties(entity, provider);
        return provider;
    }

    private LlmProviderVO toVO(LlmProvider provider) {
        LlmProviderVO vo = new LlmProviderVO();
        BeanUtils.copyProperties(provider, vo);
        vo.setHasApiKey(provider.hasApiKey());
        vo.setApiKeyMasked(provider.getMaskedApiKey());
        return vo;
    }

    @Override
    public List<LlmProvider> listActiveByPriority() {
        LambdaQueryWrapper<LlmProviderDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LlmProviderDO::getTenantId, currentTenantId())
                .eq(LlmProviderDO::getStatus, 1)
                .orderByAsc(LlmProviderDO::getPriority);
        return llmProviderMapper.selectList(wrapper).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }
}
