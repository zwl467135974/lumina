package io.lumina.agent.service;

import io.lumina.agent.infrastructure.entity.KnowledgeDocumentDO;
import io.lumina.common.core.PageResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface KnowledgeService {
    String uploadDocument(MultipartFile file, Long agentId, Long kbId);

    /**
     * 纯文本直接入库（知识沉淀审核通过后的入库路径，同步执行）
     *
     * <p>复用与文件上传一致的分块/向量化/双写管线；返回文档 uuid。
     *
     * @param title   文档标题
     * @param content 文本内容
     * @param kbId    目标知识库（分块配置按 KB 级优先）
     * @since 3.12.0
     */
    String ingestText(String title, String content, Long kbId);

    KnowledgeDocumentDO getDocumentStatus(String uuid);
    PageResult<KnowledgeDocumentDO> listDocuments(Long agentId, Long kbId, Integer pageNum, Integer pageSize);
    void deleteDocument(String uuid);
    List<Map<String, Object>> search(String query, int limit);
}
