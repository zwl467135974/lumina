package io.lumina.agent.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lumina.agent.util.JsonUtils;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.agentscope.core.rag.model.Document;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Jina Reranker
 *
 * <p>调用 Jina AI /rerank API，协议与 SiliconFlow 兼容（Cohere 风格），
 * 但支持服务端 top_n 截断。
 * API 文档：https://jina.ai/reranker/
 *
 * @author Lumina Team
 * @since 3.3.0
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "lumina.rag.rerank", name = "provider", havingValue = "jina")
public class JinaReranker implements RerankProvider {

    private static final String DEFAULT_BASE_URL = "https://api.jina.ai/v1";
    private static final String DEFAULT_MODEL = "jina-reranker-v2-base-multilingual";

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = JsonUtils.OBJECT_MAPPER;

    public JinaReranker(
            @Value("${lumina.rag.rerank.api-key:}") String apiKey,
            @Value("${lumina.rag.rerank.base-url:}") String baseUrl,
            @Value("${lumina.rag.rerank.model:}") String model) {
        this.apiKey = apiKey;
        this.baseUrl = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl : DEFAULT_BASE_URL;
        this.model = (model != null && !model.isBlank()) ? model : DEFAULT_MODEL;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public List<Document> rerank(String query, List<Document> docs, int topK) {
        if (docs == null || docs.isEmpty()) {
            return docs;
        }

        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", model);
            requestBody.put("query", query);
            ArrayNode documentsArray = requestBody.putArray("documents");
            for (Document doc : docs) {
                String content = doc.getMetadata() != null ? doc.getMetadata().getContentText() : "";
                documentsArray.add(content != null ? content : "");
            }
            // Jina 支持服务端 top_n 截断（优于 SiliconFlow 的客户端截断）
            requestBody.put("top_n", Math.min(topK, docs.size()));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/rerank"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Jina rerank 请求失败: status={}", response.statusCode());
                return docs.size() > topK ? docs.subList(0, topK) : docs;
            }

            JsonNode results = objectMapper.readTree(response.body()).path("results");
            if (!results.isArray() || results.isEmpty()) {
                return docs.size() > topK ? docs.subList(0, topK) : docs;
            }

            List<Document> reranked = new ArrayList<>();
            for (JsonNode item : results) {
                int index = item.path("index").asInt();
                double score = item.path("relevance_score").asDouble();
                if (index >= 0 && index < docs.size()) {
                    Document doc = docs.get(index);
                    doc.setScore(score);
                    reranked.add(doc);
                }
            }
            log.debug("Jina rerank 完成: input={}, output={}", docs.size(), reranked.size());
            return reranked;

        } catch (Exception e) {
            log.warn("Jina rerank 异常，降级到原顺序: {}", e.getMessage());
            return docs.size() > topK ? docs.subList(0, topK) : docs;
        }
    }

    @Override
    public String getName() {
        return "jina";
    }
}
