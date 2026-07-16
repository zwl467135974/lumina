package io.lumina.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lumina.agent.api.dto.ab.CreateAbExperimentDTO;
import io.lumina.agent.api.vo.AbExperimentVO;
import io.lumina.agent.infrastructure.entity.AbExperimentDO;
import io.lumina.agent.infrastructure.entity.AbExposureDO;
import io.lumina.agent.infrastructure.entity.AbVariantDO;
import io.lumina.agent.infrastructure.mapper.AbExperimentMapper;
import io.lumina.agent.infrastructure.mapper.AbExposureMapper;
import io.lumina.agent.infrastructure.mapper.AbVariantMapper;
import io.lumina.agent.model.AgentConfig;
import io.lumina.agent.service.AbTestService;
import io.lumina.common.core.BaseContext;
import io.lumina.common.core.ErrorCode;
import io.lumina.common.exception.BusinessException;
import io.lumina.agent.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * A/B 测试服务实现
 *
 * @author Lumina Team
 * @since 3.3.1
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AbTestServiceImpl implements AbTestService {

    private final AbExperimentMapper experimentMapper;
    private final AbVariantMapper variantMapper;
    private final AbExposureMapper exposureMapper;
    private final ObjectMapper objectMapper = JsonUtils.OBJECT_MAPPER;

    /**
     * 变体分配粘滞缓存：conversationId → variantId（避免同一会话分配到不同变体）
     */
    private final Map<String, Long> assignmentCache = new ConcurrentHashMap<>();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AbExperimentVO createExperiment(CreateAbExperimentDTO dto) {
        Long tenantId = currentTenantId();

        AbExperimentDO experiment = new AbExperimentDO();
        experiment.setName(dto.getName());
        experiment.setDescription(dto.getDescription());
        experiment.setAgentId(dto.getAgentId());
        experiment.setTrafficPercent(dto.getTrafficPercent() != null ? dto.getTrafficPercent() : 100);
        experiment.setStatus("DRAFT");
        experiment.setStartTime(dto.getStartTime());
        experiment.setEndTime(dto.getEndTime());
        experiment.setTenantId(tenantId);
        experimentMapper.insert(experiment);

        for (CreateAbExperimentDTO.VariantDTO vDTO : dto.getVariants()) {
            AbVariantDO variant = new AbVariantDO();
            variant.setExperimentId(experiment.getId());
            variant.setName(vDTO.getName());
            variant.setWeight(vDTO.getWeight() != null ? vDTO.getWeight() : 50);
            variant.setLlmConfig(vDTO.getLlmConfig());
            variant.setPromptName(vDTO.getPromptName());
            variant.setDescription(vDTO.getDescription());
            variant.setTenantId(tenantId);
            variantMapper.insert(variant);
        }

        log.info("创建 A/B 实验: id={}, name={}, variants={}", experiment.getId(), dto.getName(), dto.getVariants().size());
        return getExperiment(experiment.getId());
    }

    @Override
    public AbExperimentVO getExperiment(Long id) {
        AbExperimentDO experiment = experimentMapper.selectById(id);
        if (experiment == null || !experiment.getTenantId().equals(currentTenantId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "实验不存在");
        }
        return toVO(experiment, true);
    }

    @Override
    public List<AbExperimentVO> listExperiments(Long agentId, String status) {
        LambdaQueryWrapper<AbExperimentDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AbExperimentDO::getTenantId, currentTenantId());
        if (agentId != null) {
            wrapper.eq(AbExperimentDO::getAgentId, agentId);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(AbExperimentDO::getStatus, status);
        }
        wrapper.orderByDesc(AbExperimentDO::getCreateTime);
        return experimentMapper.selectList(wrapper).stream()
                .map(e -> toVO(e, false))
                .collect(Collectors.toList());
    }

    @Override
    public void startExperiment(Long id) {
        updateStatus(id, "RUNNING");
        log.info("启动 A/B 实验: id={}", id);
    }

    @Override
    public void pauseExperiment(Long id) {
        updateStatus(id, "PAUSED");
        log.info("暂停 A/B 实验: id={}", id);
    }

    @Override
    public void completeExperiment(Long id) {
        updateStatus(id, "COMPLETED");
        log.info("完成 A/B 实验: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteExperiment(Long id) {
        AbExperimentDO experiment = experimentMapper.selectById(id);
        if (experiment == null || !experiment.getTenantId().equals(currentTenantId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "实验不存在");
        }
        experimentMapper.deleteById(id);
        // 清理变体和曝光记录
        variantMapper.delete(new LambdaQueryWrapper<AbVariantDO>()
                .eq(AbVariantDO::getExperimentId, id));
        exposureMapper.delete(new LambdaQueryWrapper<AbExposureDO>()
                .eq(AbExposureDO::getExperimentId, id));
        log.info("删除 A/B 实验: id={}", id);
    }

    @Override
    public VariantContext assignVariant(Long agentId, String conversationId) {
        // 查找该 Agent 的活跃实验
        LambdaQueryWrapper<AbExperimentDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AbExperimentDO::getAgentId, agentId)
                .eq(AbExperimentDO::getStatus, "RUNNING")
                .eq(AbExperimentDO::getTenantId, currentTenantId())
                .last("LIMIT 1");
        AbExperimentDO experiment = experimentMapper.selectOne(wrapper);
        if (experiment == null) {
            return null;
        }

        // 流量百分比判断
        int trafficRoll = new Random().nextInt(100) + 1;
        if (trafficRoll > experiment.getTrafficPercent()) {
            return null; // 不在实验流量内
        }

        // 粘滞分配：同一 conversationId 分配到同一变体
        String stickyKey = experiment.getId() + ":" + (conversationId != null ? conversationId : UUID.randomUUID().toString());
        Long cachedVariantId = assignmentCache.get(stickyKey);

        // 加载变体
        List<AbVariantDO> variants = variantMapper.selectList(
                new LambdaQueryWrapper<AbVariantDO>()
                        .eq(AbVariantDO::getExperimentId, experiment.getId()));
        if (variants.isEmpty()) {
            return null;
        }

        AbVariantDO selected;
        if (cachedVariantId != null) {
            selected = variants.stream()
                    .filter(v -> v.getId().equals(cachedVariantId))
                    .findFirst()
                    .orElse(null);
            if (selected == null) {
                assignmentCache.remove(stickyKey);
                selected = selectByWeight(variants);
            }
        } else {
            selected = selectByWeight(variants);
            assignmentCache.put(stickyKey, selected.getId());
        }

        // 解析变体 LLM 配置
        AgentConfig.LLMConfig llmConfig = null;
        if (StringUtils.hasText(selected.getLlmConfig())) {
            try {
                llmConfig = objectMapper.readValue(selected.getLlmConfig(), AgentConfig.LLMConfig.class);
            } catch (Exception e) {
                log.warn("变体 LLM 配置解析失败: variant={}, error={}", selected.getName(), e.getMessage());
            }
        }

        log.debug("A/B 变体分配: experiment={}, variant={}, conversation={}",
                experiment.getId(), selected.getName(), conversationId);

        return new VariantContext(experiment.getId(), selected.getId(), selected.getName(),
                llmConfig, selected.getPromptName());
    }

    @Override
    public void recordExposure(Long experimentId, Long variantId, String variantName,
                                String conversationId, boolean success, long latencyMs,
                                Integer tokens, String errorMsg) {
        try {
            AbExposureDO exposure = new AbExposureDO();
            exposure.setExperimentId(experimentId);
            exposure.setVariantId(variantId);
            exposure.setVariantName(variantName);
            exposure.setConversationId(conversationId);
            exposure.setUserId(BaseContext.getUserId());
            exposure.setSuccess(success ? 1 : 0);
            exposure.setLatencyMs(latencyMs);
            exposure.setTokens(tokens);
            exposure.setErrorMsg(errorMsg != null ? errorMsg.substring(0, Math.min(500, errorMsg.length())) : null);
            exposure.setTenantId(currentTenantId());
            exposureMapper.insert(exposure);
        } catch (Exception e) {
            log.warn("曝光记录写入失败: experiment={}, variant={}", experimentId, variantId, e);
        }
    }

    // ==================== 内部方法 ====================

    private void updateStatus(Long id, String status) {
        AbExperimentDO experiment = experimentMapper.selectById(id);
        if (experiment == null || !experiment.getTenantId().equals(currentTenantId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "实验不存在");
        }
        experiment.setStatus(status);
        experimentMapper.updateById(experiment);
    }

    /**
     * 按权重随机选择变体
     */
    private AbVariantDO selectByWeight(List<AbVariantDO> variants) {
        int totalWeight = variants.stream().mapToInt(v -> v.getWeight() != null ? v.getWeight() : 0).sum();
        if (totalWeight <= 0) {
            return variants.get(0);
        }
        int roll = new Random().nextInt(totalWeight);
        int cumulative = 0;
        for (AbVariantDO v : variants) {
            cumulative += (v.getWeight() != null ? v.getWeight() : 0);
            if (roll < cumulative) {
                return v;
            }
        }
        return variants.get(variants.size() - 1);
    }

    private AbExperimentVO toVO(AbExperimentDO experiment, boolean includeDetails) {
        AbExperimentVO vo = new AbExperimentVO();
        BeanUtils.copyProperties(experiment, vo);

        if (includeDetails || true) {
            // 加载变体
            List<AbVariantDO> variants = variantMapper.selectList(
                    new LambdaQueryWrapper<AbVariantDO>()
                            .eq(AbVariantDO::getExperimentId, experiment.getId()));
            vo.setVariants(variants.stream().map(v -> {
                AbExperimentVO.AbVariantVO vVO = new AbExperimentVO.AbVariantVO();
                BeanUtils.copyProperties(v, vVO);
                return vVO;
            }).collect(Collectors.toList()));

            // 加载报告
            vo.setReport(buildReport(experiment.getId(), variants));
        }
        return vo;
    }

    private AbExperimentVO.AbExperimentReport buildReport(Long experimentId, List<AbVariantDO> variants) {
        List<AbExposureDO> exposures = exposureMapper.selectList(
                new LambdaQueryWrapper<AbExposureDO>()
                        .eq(AbExposureDO::getExperimentId, experimentId));

        AbExperimentVO.AbExperimentReport report = new AbExperimentVO.AbExperimentReport();
        report.setTotalExposures((long) exposures.size());

        Map<Long, List<AbExposureDO>> byVariant = exposures.stream()
                .collect(Collectors.groupingBy(AbExposureDO::getVariantId));

        report.setVariants(variants.stream().map(v -> {
            AbExperimentVO.AbExperimentReport.VariantReport vr = new AbExperimentVO.AbExperimentReport.VariantReport();
            vr.setVariantName(v.getName());
            List<AbExposureDO> variantExposures = byVariant.getOrDefault(v.getId(), Collections.emptyList());
            vr.setExposures((long) variantExposures.size());
            if (!variantExposures.isEmpty()) {
                long successes = variantExposures.stream().filter(e -> e.getSuccess() != null && e.getSuccess() == 1).count();
                vr.setSuccessRate((double) successes / variantExposures.size());
                vr.setAvgLatencyMs(variantExposures.stream()
                        .filter(e -> e.getLatencyMs() != null)
                        .mapToLong(AbExposureDO::getLatencyMs)
                        .average().orElse(0));
                vr.setAvgTokens(variantExposures.stream()
                        .filter(e -> e.getTokens() != null)
                        .mapToInt(AbExposureDO::getTokens)
                        .average().orElse(0));
            } else {
                vr.setSuccessRate(0.0);
                vr.setAvgLatencyMs(0.0);
                vr.setAvgTokens(0.0);
            }
            return vr;
        }).collect(Collectors.toList()));

        return report;
    }

    private Long currentTenantId() {
        return BaseContext.getTenantId() != null ? BaseContext.getTenantId() : 0L;
    }
}
