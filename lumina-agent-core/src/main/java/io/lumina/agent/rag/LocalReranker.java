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
 * 本地 Reranker（HTTP 调用自建的 rerank 服务，如 BGE-reranker / Jina Reranker）
 *
 * <p>兼容 SiliconFlow /rerank API 格式，适用于本地部署的同协议服务。
 *
 * @author Lumina Team
 * @since 3.3.0
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "lumina.rag.rerank", name = "provider", havingValue = "local")
public class LocalReranker implements RerankProvider {

    private final String baseUrl;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = JsonUtils.OBJECT_MAPPER;

    public LocalReranker(
            @Value("${lumina.rag.rerank.base-url:http://localhost:8001}") String baseUrl,
            @Value("${lumina.rag.rerank.model:bge-reranker-v2-m3}") String model) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
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
            requestBody.put("top_n", Math.min(topK, docs.size()));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/rerank"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("本地 rerank 请求失败: status={}", response.statusCode());
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
            return reranked;

        } catch (Exception e) {
            log.warn("本地 rerank 异常，降级到原顺序: {}", e.getMessage());
            return docs.size() > topK ? docs.subList(0, topK) : docs;
        }
    }

    @Override
    public String getName() {
        return "local";
    }
}
