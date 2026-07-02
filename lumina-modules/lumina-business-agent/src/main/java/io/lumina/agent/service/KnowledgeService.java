package io.lumina.agent.service;

import io.lumina.agent.infrastructure.entity.KnowledgeDocumentDO;
import io.lumina.common.core.PageResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface KnowledgeService {
    String uploadDocument(MultipartFile file, Long agentId);
    KnowledgeDocumentDO getDocumentStatus(String uuid);
    PageResult<KnowledgeDocumentDO> listDocuments(Long agentId, Integer pageNum, Integer pageSize);
    void deleteDocument(String uuid);
    List<Map<String, Object>> search(String query, int limit);
}
