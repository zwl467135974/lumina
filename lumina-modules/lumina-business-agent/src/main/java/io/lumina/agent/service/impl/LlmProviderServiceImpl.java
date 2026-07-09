package io.lumina.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.lumina.agent.api.dto.llm.CreateLlmProviderDTO;
import io.lumina.agent.api.dto.llm.QueryLlmProviderDTO;
import io.lumina.agent.api.vo.LlmProviderVO;
import io.lumina.agent.infrastructure.entity.LlmProviderDO;
import io.lumina.agent.infrastructure.mapper.LlmProviderMapper;
import io.lumina.agent.service.LlmProviderService;
import io.lumina.common.util.CryptoUtil;
import io.lumina.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmProviderServiceImpl implements LlmProviderService {

    private final LlmProviderMapper llmProviderMapper;

    @Override
    public LlmProviderVO getById(Long id) {
        LlmProviderDO entity = llmProviderMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("LLM Provider 不存在");
        }
        return toVO(entity);
    }

    @Override
    public List<LlmProviderVO> list(QueryLlmProviderDTO query) {
        LambdaQueryWrapper<LlmProviderDO> wrapper = new LambdaQueryWrapper<>();
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
        return llmProviderMapper.selectList(wrapper).stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public LlmProviderVO create(CreateLlmProviderDTO dto) {
        LlmProviderDO entity = new LlmProviderDO();
        BeanUtils.copyProperties(dto, entity);
        if (StringUtils.hasText(dto.getApiKey())) {
            entity.setApiKeyEnc(CryptoUtil.encrypt(dto.getApiKey()));
        }
        if (dto.getStatus() == null) {
            entity.setStatus(1);
        }
        entity.setTenantId(0L);
        llmProviderMapper.insert(entity);
        return toVO(entity);
    }

    @Override
    public LlmProviderVO update(Long id, CreateLlmProviderDTO dto) {
        LlmProviderDO entity = llmProviderMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("LLM Provider 不存在");
        }
        entity.setName(dto.getName());
        entity.setProvider(dto.getProvider());
        entity.setBaseUrl(dto.getBaseUrl());
        entity.setDefaultModel(dto.getDefaultModel());
        entity.setDefaultParams(dto.getDefaultParams());
        if (dto.getStatus() != null) {
            entity.setStatus(dto.getStatus());
        }
        if (StringUtils.hasText(dto.getApiKey())) {
            entity.setApiKeyEnc(CryptoUtil.encrypt(dto.getApiKey()));
        }
        llmProviderMapper.updateById(entity);
        return toVO(entity);
    }

    @Override
    public void delete(Long id) {
        LlmProviderDO entity = llmProviderMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("LLM Provider 不存在");
        }
        llmProviderMapper.deleteById(id);
    }

    @Override
    public boolean testConnection(Long id) {
        LlmProviderDO entity = llmProviderMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("LLM Provider 不存在");
        }
        String apiKey = getDecryptedApiKey(id);
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException("该 Provider 未配置 API Key");
        }
        try {
            io.lumina.agent.model.AgentConfig.LLMConfig config = new io.lumina.agent.model.AgentConfig.LLMConfig();
            config.setModelType(entity.getProvider());
            config.setApiKey(apiKey);
            config.setBaseUrl(entity.getBaseUrl());
            config.setModelName(entity.getDefaultModel());
            io.lumina.agent.config.LuminaAgentProperties.LLMConfig defaults = new io.lumina.agent.config.LuminaAgentProperties.LLMConfig();
            defaults.setType(entity.getProvider());
            new io.lumina.agent.model.ChatModelFactory().create(config, defaults, apiKey);
            return true;
        } catch (Exception e) {
            log.error("LLM Provider 连通性测试失败: provider={}, error={}", entity.getProvider(), e.getMessage());
            throw new BusinessException("连通性测试失败: " + e.getMessage());
        }
    }

    @Override
    public String getDecryptedApiKey(Long id) {
        LlmProviderDO entity = llmProviderMapper.selectById(id);
        if (entity == null || !StringUtils.hasText(entity.getApiKeyEnc())) {
            return null;
        }
        return CryptoUtil.decrypt(entity.getApiKeyEnc());
    }

    private LlmProviderVO toVO(LlmProviderDO entity) {
        LlmProviderVO vo = new LlmProviderVO();
        BeanUtils.copyProperties(entity, vo);
        boolean hasKey = StringUtils.hasText(entity.getApiKeyEnc());
        vo.setHasApiKey(hasKey);
        if (hasKey) {
            try {
                vo.setApiKeyMasked(CryptoUtil.mask(CryptoUtil.decrypt(entity.getApiKeyEnc())));
            } catch (Exception e) {
                vo.setApiKeyMasked("****");
            }
        }
        return vo;
    }
}
