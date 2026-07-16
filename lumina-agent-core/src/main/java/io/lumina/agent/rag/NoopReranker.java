package io.lumina.agent.rag;

import io.agentscope.core.rag.model.Document;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 空重排序器（不调模型，仅按原顺序截断）
 *
 * <p>当 lumina.rag.rerank.provider 未配置或为 none 时激活（默认 Bean）。
 *
 * @author Lumina Team
 * @since 3.3.0
 */
@Slf4j
@Component
@ConditionalOnMissingBean(RerankProvider.class)
public class NoopReranker implements RerankProvider {

    @Override
    public List<Document> rerank(String query, List<Document> docs, int topK) {
        if (docs == null || docs.isEmpty()) {
            return docs;
        }
        return docs.size() > topK ? docs.subList(0, topK) : docs;
    }

    @Override
    public String getName() {
        return "none";
    }
}
