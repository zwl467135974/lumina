package io.lumina.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.lumina.agent.api.dto.KnowledgeBaseDTO;
import io.lumina.agent.infrastructure.entity.AgentDO;
import io.lumina.agent.infrastructure.entity.AgentKnowledgeBaseDO;
import io.lumina.agent.infrastructure.entity.KnowledgeBaseDO;
import io.lumina.agent.infrastructure.mapper.AgentKnowledgeBaseMapper;
import io.lumina.agent.infrastructure.mapper.AgentMapper;
import io.lumina.agent.infrastructure.mapper.KnowledgeBaseMapper;
import io.lumina.agent.service.KnowledgeBaseService;
import io.lumina.common.core.BaseContext;
import io.lumina.common.core.ErrorCode;
import io.lumina.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 知识库联邦服务实现（E5）
 *
 * @author Lumina Team
 * @since 2.1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private final KnowledgeBaseMapper kbMapper;
    private final AgentKnowledgeBaseMapper agentKbMapper;
    private final AgentMapper agentMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeBaseDO createKnowledgeBase(KnowledgeBaseDTO dto) {
        KnowledgeBaseDO kb = new KnowledgeBaseDO();
        kb.setName(dto.getName());
        kb.setDescription(dto.getDescription());
        kb.setVisibility(StringUtils.hasText(dto.getVisibility()) ? dto.getVisibility() : "PRIVATE");
        kb.setTenantId(currentTenantId());
        kb.setCreateBy(BaseContext.getUserId());
        kb.setIsDeleted(0);
        kbMapper.insert(kb);
        log.info("创建知识库: id={}, name={}, visibility={}", kb.getId(), kb.getName(), kb.getVisibility());
        return kb;
    }

    @Override
    public KnowledgeBaseDO getKnowledgeBase(Long id) {
        KnowledgeBaseDO kb = getKbOrThrow(id);
        checkTenantAccess(kb);
        return kb;
    }

    @Override
    public List<KnowledgeBaseDO> listKnowledgeBases(String name) {
        LambdaQueryWrapper<KnowledgeBaseDO> wrapper = new LambdaQueryWrapper<KnowledgeBaseDO>()
                .eq(KnowledgeBaseDO::getTenantId, currentTenantId())
                .eq(KnowledgeBaseDO::getIsDeleted, 0)
                .orderByDesc(KnowledgeBaseDO::getCreateTime);
        if (StringUtils.hasText(name)) {
            wrapper.like(KnowledgeBaseDO::getName, name);
        }
        return kbMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteKnowledgeBase(Long id) {
        KnowledgeBaseDO kb = getKbOrThrow(id);
        checkTenantAccess(kb);
        kbMapper.deleteById(id);
        agentKbMapper.delete(new LambdaQueryWrapper<AgentKnowledgeBaseDO>()
                .eq(AgentKnowledgeBaseDO::getKbId, id));
        log.info("删除知识库: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void mountKnowledgeBase(Long agentId, Long kbId) {
        checkAgentOwnership(agentId);
        KnowledgeBaseDO kb = getKbOrThrow(kbId);
        checkTenantAccess(kb);
        Long count = agentKbMapper.selectCount(new LambdaQueryWrapper<AgentKnowledgeBaseDO>()
                .eq(AgentKnowledgeBaseDO::getAgentId, agentId)
                .eq(AgentKnowledgeBaseDO::getKbId, kbId));
        if (count > 0) {
            return;
        }
        AgentKnowledgeBaseDO mount = new AgentKnowledgeBaseDO();
        mount.setAgentId(agentId);
        mount.setKbId(kbId);
        mount.setTenantId(currentTenantId());
        agentKbMapper.insert(mount);
        log.info("挂载知识库: agentId={}, kbId={}", agentId, kbId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unmountKnowledgeBase(Long agentId, Long kbId) {
        checkAgentOwnership(agentId);
        agentKbMapper.delete(new LambdaQueryWrapper<AgentKnowledgeBaseDO>()
                .eq(AgentKnowledgeBaseDO::getAgentId, agentId)
                .eq(AgentKnowledgeBaseDO::getKbId, kbId)
                .eq(AgentKnowledgeBaseDO::getTenantId, currentTenantId()));
        log.info("卸载知识库: agentId={}, kbId={}", agentId, kbId);
    }

    @Override
    public List<Long> getAgentKnowledgeBaseIds(Long agentId) {
        return agentKbMapper.selectList(new LambdaQueryWrapper<AgentKnowledgeBaseDO>()
                        .eq(AgentKnowledgeBaseDO::getAgentId, agentId)
                        .eq(AgentKnowledgeBaseDO::getTenantId, currentTenantId()))
                .stream()
                .map(AgentKnowledgeBaseDO::getKbId)
                .collect(Collectors.toList());
    }

    @Override
    public List<KnowledgeBaseDO> getAccessibleKnowledgeBases(Long agentId) {
        Long tenantId = currentTenantId();
        Set<Long> accessibleIds = new HashSet<>();

        List<Long> mountedIds = getAgentKnowledgeBaseIds(agentId);
        accessibleIds.addAll(mountedIds);

        kbMapper.selectList(new LambdaQueryWrapper<KnowledgeBaseDO>()
                        .eq(KnowledgeBaseDO::getVisibility, "PUBLIC")
                        .eq(KnowledgeBaseDO::getTenantId, tenantId)
                        .eq(KnowledgeBaseDO::getIsDeleted, 0))
                .forEach(kb -> accessibleIds.add(kb.getId()));

        if (accessibleIds.isEmpty()) {
            return List.of();
        }

        return kbMapper.selectBatchIds(accessibleIds).stream()
                .filter(kb -> !Integer.valueOf(1).equals(kb.getIsDeleted()))
                .collect(Collectors.toList());
    }

    private KnowledgeBaseDO getKbOrThrow(Long id) {
        KnowledgeBaseDO kb = kbMapper.selectById(id);
        if (kb == null || Integer.valueOf(1).equals(kb.getIsDeleted())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "知识库不存在");
        }
        return kb;
    }

    private void checkTenantAccess(KnowledgeBaseDO kb) {
        if (!currentTenantId().equals(kb.getTenantId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该知识库");
        }
    }

    private void checkAgentOwnership(Long agentId) {
        AgentDO agent = agentMapper.selectById(agentId);
        if (agent == null || !currentTenantId().equals(agent.getTenantId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作该 Agent");
        }
    }

    private Long currentTenantId() {
        return BaseContext.getTenantId() != null ? BaseContext.getTenantId() : 0L;
    }
}
