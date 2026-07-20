package io.lumina.agent.rag;

import io.agentscope.core.rag.model.Document;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 空重排序器（不调模型，仅按原顺序截断）
 *
 * <p>当 {@code lumina.rag.rerank.provider=none}（或缺省）时激活。
 *
 * <p>注册策略：和 {@link NoopOcrProvider} 一致，使用 {@code @ConditionalOnProperty(matchIfMissing=true)}
 * 而非 {@code @ConditionalOnMissingBean}。后者在 Spring 的 bean 注册顺序不确定时会失效
 * （{@code NoopReranker} 自己也是 {@link RerankProvider} 候选，求值时机有竞态），
 * 导致 standalone 模式下未配 reranker 时直接启动失败。
 *
 * @author Lumina Team
 * @since 3.3.0
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "lumina.rag.rerank", name = "provider",
        havingValue = "none", matchIfMissing = true)
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
