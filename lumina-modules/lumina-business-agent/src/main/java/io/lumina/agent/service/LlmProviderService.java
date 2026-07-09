package io.lumina.agent.service;

import io.lumina.agent.api.dto.llm.CreateLlmProviderDTO;
import io.lumina.agent.api.dto.llm.QueryLlmProviderDTO;
import io.lumina.agent.api.vo.LlmProviderVO;

import java.util.List;

public interface LlmProviderService {

    LlmProviderVO getById(Long id);

    List<LlmProviderVO> list(QueryLlmProviderDTO query);

    LlmProviderVO create(CreateLlmProviderDTO dto);

    LlmProviderVO update(Long id, CreateLlmProviderDTO dto);

    void delete(Long id);

    boolean testConnection(Long id);

    /**
     * 解密获取 Provider 的 API Key（供内部调用）
     */
    String getDecryptedApiKey(Long id);
}
