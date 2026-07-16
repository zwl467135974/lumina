package io.lumina.agent.service;

import io.lumina.agent.api.dto.llm.CreateLlmProviderDTO;
import io.lumina.agent.api.dto.llm.QueryLlmProviderDTO;
import io.lumina.agent.api.dto.llm.UpdateLlmProviderDTO;
import io.lumina.agent.api.vo.LlmProviderVO;

import java.util.List;

/**
 * LLM 供应商配置管理 Service
 *
 * @author Lumina Team
 * @since 1.0.0
 */
public interface LlmProviderService {

    LlmProviderVO getById(Long id);

    List<LlmProviderVO> list(QueryLlmProviderDTO query);

    LlmProviderVO create(CreateLlmProviderDTO dto);

    LlmProviderVO update(Long id, UpdateLlmProviderDTO dto);

    void delete(Long id);

    boolean testConnection(Long id);

    /**
     * 解密获取 Provider 的 API Key（供内部调用）
     */
    String getDecryptedApiKey(Long id);

    /**
     * 按优先级查询当前租户的活跃 Provider 列表（用于 failover 链）
     *
     * @return 按 priority ASC 排序的活跃 Provider 领域实体列表
     * @since 3.3.1
     */
    List<io.lumina.agent.domain.model.LlmProvider> listActiveByPriority();
}
