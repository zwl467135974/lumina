package io.lumina.agent.service;

import io.lumina.agent.api.dto.KnowledgeBaseDTO;
import io.lumina.agent.infrastructure.entity.KnowledgeBaseDO;

import java.util.List;

/**
 * 知识库联邦服务（E5）
 *
 * @author Lumina Team
 * @since 2.1.0
 */
public interface KnowledgeBaseService {

    KnowledgeBaseDO createKnowledgeBase(KnowledgeBaseDTO dto);

    KnowledgeBaseDO getKnowledgeBase(Long id);

    List<KnowledgeBaseDO> listKnowledgeBases(String name);

    void deleteKnowledgeBase(Long id);

    void mountKnowledgeBase(Long agentId, Long kbId);

    void unmountKnowledgeBase(Long agentId, Long kbId);

    List<Long> getAgentKnowledgeBaseIds(Long agentId);

    /**
     * 查询指定 Agent 可访问的所有知识库（含挂载的 + PUBLIC 的）
     */
    List<KnowledgeBaseDO> getAccessibleKnowledgeBases(Long agentId);
}
