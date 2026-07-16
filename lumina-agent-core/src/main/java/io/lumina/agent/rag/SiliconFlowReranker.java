package io.lumina.agent.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.agentscope.core.rag.model.Document;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 硅基流动（SiliconFlow）Reranker
 *
 * <p>调用 SiliconFlow /rerank API（免费额度），支持 BGE-Reranker 等模型。
 * API 文档：https://docs.siliconflow.cn/api-reference/rerank/create-rerank
 *
 * @author Lumina Team
 * @since 3.3.0
 */
@Slf4j
public class SiliconFlowReranker implements RerankProvider {

    private static final String DEFAULT_BASE_URL = "https://api.siliconflow.cn/v1";
    private static final String DEFAULT_MODEL = "BAAI/bge-reranker-v2-m3";

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SiliconFlowReranker(String apiKey, String baseUrl, String model) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl != null && !baseUrl.isBlank() ? baseUrl : DEFAULT_BASE_URL;
        this.model = model != null && !model.isBlank() ? model : DEFAULT_MODEL;
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

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/rerank"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("SiliconFlow rerank 请求失败: status={}, body={}", response.statusCode(),
                        response.body().length() > 200 ? response.body().substring(0, 200) : response.body());
                // 降级：返回原顺序截断
                return docs.size() > topK ? docs.subList(0, topK) : docs;
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode results = root.path("results");
            if (!results.isArray() || results.isEmpty()) {
                return docs.size() > topK ? docs.subList(0, topK) : docs;
            }

            List<Document> reranked = new ArrayList<>();
            for (JsonNode item : results) {
                int index = item.path("index").asInt();
                double score = item.path("relevance_score").asDouble();
                if (index >= 0 && index < docs.size()) {
                    Document doc = docs.get(index);
                    // 更新 rerank 分数
                    doc.setScore(score);
                    reranked.add(doc);
                }
            }
            log.debug("SiliconFlow rerank 完成: input={}, output={}", docs.size(), reranked.size());
            // 客户端截断到 topK（API 不支持 top_n 参数）
            return reranked.size() > topK ? reranked.subList(0, topK) : reranked;

        } catch (Exception e) {
            log.warn("SiliconFlow rerank 异常，降级到原顺序: {}", e.getMessage());
            return docs.size() > topK ? docs.subList(0, topK) : docs;
        }
    }

    @Override
    public String getName() {
        return "siliconflow";
    }
}
