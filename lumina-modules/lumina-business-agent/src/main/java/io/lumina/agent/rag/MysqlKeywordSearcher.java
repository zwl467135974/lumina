package io.lumina.agent.rag;

import io.agentscope.core.message.TextBlock;
import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.model.DocumentMetadata;
import io.lumina.agent.infrastructure.entity.KnowledgeChunkDO;
import io.lumina.agent.infrastructure.mapper.KnowledgeChunkMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * MySQL FULLTEXT 关键词检索器（混合检索关键词路实现）
 *
 * <p>使用 MySQL 8.0 ngram 全文索引，对 lumina_knowledge_chunk 表做关键词匹配。
 * 当 lumina.rag.hybrid.enabled=true 时自动激活。
 *
 * @author Lumina Team
 * @since 3.3.0
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "lumina.rag.hybrid", name = "enabled", havingValue = "true")
public class MysqlKeywordSearcher implements KeywordSearcher {

    @Autowired
    private KnowledgeChunkMapper chunkMapper;

    @Override
    public List<Document> search(String query, Long tenantId, int limit) {
        if (query == null || query.isBlank() || tenantId == null) {
            return List.of();
        }

        try {
            List<KnowledgeChunkDO> chunks = chunkMapper.fulltextSearch(query, tenantId, null, limit);
            if (chunks == null || chunks.isEmpty()) {
                log.debug("MySQL 全文检索无结果: query={}", query);
                return List.of();
            }

            List<Document> docs = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                KnowledgeChunkDO chunk = chunks.get(i);
                // MySQL FULLTEXT 不返回精确相关度分数，用排名位置近似归一化
                double score = 1.0 / (i + 1);

                DocumentMetadata meta = DocumentMetadata.builder()
                        .content(TextBlock.builder().text(chunk.getContent()).build())
                        .docId(chunk.getDocUuid())
                        .chunkId(chunk.getChunkId())
                        .addPayload("tenantId", chunk.getTenantId())
                        .build();
                if (chunk.getKbId() != null) {
                    meta.getPayload().put("kbId", chunk.getKbId());
                }

                Document doc = new Document(meta);
                doc.setScore(score);
                docs.add(doc);
            }

            log.debug("MySQL 全文检索: query={}, results={}", query, docs.size());
            return docs;

        } catch (Exception e) {
            log.warn("MySQL 全文检索异常，返回空结果: query={}, error={}", query, e.getMessage());
            return List.of();
        }
    }
}
