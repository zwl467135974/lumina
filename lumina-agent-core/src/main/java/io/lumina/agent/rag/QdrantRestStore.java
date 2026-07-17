package io.lumina.agent.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.model.DocumentMetadata;
import io.agentscope.core.rag.store.VDBStoreBase;
import io.agentscope.core.rag.store.dto.SearchDocumentDto;
import io.lumina.agent.util.JsonUtils;
import io.lumina.common.core.ErrorCode;
import io.lumina.common.exception.BusinessException;
import io.micrometer.core.instrument.Metrics;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Qdrant 向量存储（REST API 实现）
 *
 * <p>使用 Qdrant REST API（:6333）替代 gRPC 客户端，
 * 彻底绕过 AgentScope QdrantStore 与 io.qdrant:client 的 gRPC 类签名不兼容问题。
 *
 * <p>REST API 文档: https://qdrant.github.io/qdrant/redoc/index.html
 *
 * @author Lumina Team
 * @since 1.2.0
 */
@Slf4j
public class QdrantRestStore implements VDBStoreBase {

    private final String collectionName;
    private final int dimensions;
    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = JsonUtils.OBJECT_MAPPER;

    /**
     * @param host           Qdrant 地址（如 localhost:6333）
     * @param collectionName 集合名
     * @param dimensions     向量维度
     */
    public QdrantRestStore(String host, String collectionName, int dimensions) {
        this.baseUrl = host.startsWith("http") ? host : "http://" + host;
        this.collectionName = collectionName;
        this.dimensions = dimensions;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        ensureCollection();
        log.info("QdrantRestStore 初始化: baseUrl={}, collection={}, dims={}", baseUrl, collectionName, dimensions);
    }

    // ==================== VDBStoreBase 实现 ====================

    @Override
    public Mono<Void> add(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return Mono.empty();
        }
        return Mono.fromRunnable(() -> doAdd(documents)).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    public Mono<List<Document>> search(SearchDocumentDto dto) {
        return Mono.fromCallable(() -> doSearch(dto)).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Boolean> delete(String id) {
        if (id == null || id.isBlank()) {
            return Mono.just(false);
        }
        return Mono.fromCallable(() -> doDelete(id)).subscribeOn(Schedulers.boundedElastic());
    }

    // ==================== 内部实现 ====================

    /**
     * 确保集合存在（不存在则创建）
     */
    private void ensureCollection() {
        try {
            HttpResponse<String> resp = sendGet("/collections/" + collectionName);
            if (resp.statusCode() == 200) {
                log.info("Qdrant 集合已存在: {}", collectionName);
                return;
            }
            createCollection();
        } catch (Exception e) {
            log.warn("检查集合失败，尝试创建: {}", e.getMessage());
            try {
                createCollection();
            } catch (Exception ex) {
                throw new BusinessException(ErrorCode.RAG_STORE_ERROR, "创建 Qdrant 集合失败: " + collectionName, ex);
            }
        }
    }

    private void createCollection() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        ObjectNode vectors = objectMapper.createObjectNode();
        vectors.put("size", dimensions);
        vectors.put("distance", "Cosine");
        body.set("vectors", vectors);

        HttpResponse<String> resp = sendPut("/collections/" + collectionName, body);
        if (resp.statusCode() == 200) {
            log.info("Qdrant 集合创建成功: {}, dims={}", collectionName, dimensions);
        } else {
            throw new BusinessException(ErrorCode.RAG_STORE_ERROR, "创建集合失败: HTTP " + resp.statusCode());
        }
    }

    /**
     * 批量写入向量
     */
    private void doAdd(List<Document> documents) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            ArrayNode points = body.putArray("points");

            for (Document doc : documents) {
                ObjectNode point = points.addObject();
                point.put("id", doc.getId());

                ArrayNode vector = point.putArray("vector");
                for (double v : doc.getEmbedding()) {
                    vector.add(v);
                }

                ObjectNode payload = point.putObject("payload");
                String contentText = extractContentText(doc);
                payload.put("content", contentText);
                payload.put("docId", doc.getMetadata().getDocId());
                payload.put("chunkId", doc.getMetadata().getChunkId());

                Map<String, Object> extra = doc.getMetadata().getPayload();
                if (extra != null) {
                    for (Map.Entry<String, Object> e : extra.entrySet()) {
                        if (!"content".equals(e.getKey()) && e.getValue() != null) {
                            payload.putPOJO(e.getKey(), e.getValue());
                        }
                    }
                }
            }

            HttpResponse<String> resp = sendPut("/collections/" + collectionName + "/points?wait=true", body);
            if (resp.statusCode() != 200) {
                throw new BusinessException(ErrorCode.RAG_STORE_ERROR, "写入向量失败: HTTP " + resp.statusCode());
            }
            log.info("向量写入成功: count={}", documents.size());
        } catch (BusinessException e) {
            Metrics.counter("qdrant.error.count", "operation", "add").increment();
            log.error("向量入库失败: collection={}, count={}", collectionName, documents.size(), e);
            throw e;
        } catch (Exception e) {
            Metrics.counter("qdrant.error.count", "operation", "add").increment();
            log.error("向量入库失败: collection={}, count={}", collectionName, documents.size(), e);
            throw new BusinessException(ErrorCode.RAG_EMBEDDING_FAILED, "向量入库失败", e);
        }
    }

    /**
     * 向量相似度检索
     */
    private List<Document> doSearch(SearchDocumentDto dto) {
        try {
            ObjectNode body = objectMapper.createObjectNode();

            ArrayNode vector = body.putArray("vector");
            for (double v : dto.getQueryEmbedding()) {
                vector.add(v);
            }
            body.put("limit", dto.getLimit());
            if (dto.getScoreThreshold() != null) {
                body.put("score_threshold", dto.getScoreThreshold());
            }
            body.put("with_payload", true);

            HttpResponse<String> resp = sendPost("/collections/" + collectionName + "/points/search", body);
            if (resp.statusCode() != 200) {
                log.error("向量检索失败: status={}, body={}", resp.statusCode(), resp.body());
                return List.of();
            }

            JsonNode root = objectMapper.readTree(resp.body());
            JsonNode results = root.path("result");
            if (!results.isArray()) {
                return List.of();
            }

            List<Document> docs = new ArrayList<>();
            for (JsonNode item : results) {
                double score = item.path("score").asDouble();
                JsonNode payload = item.path("payload");

                String contentText = payload.path("content").asText("");
                String docId = payload.path("docId").asText(null);
                String chunkId = payload.path("chunkId").asText(null);

                // 提取额外 payload 字段（排除 content/docId/chunkId 等保留字段）
                Map<String, Object> extraPayload = null;
                java.util.Iterator<Map.Entry<String, JsonNode>> fields = payload.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> field = fields.next();
                    String key = field.getKey();
                    if (!"content".equals(key) && !"docId".equals(key) && !"chunkId".equals(key)) {
                        if (extraPayload == null) {
                            extraPayload = new java.util.HashMap<>();
                        }
                        extraPayload.put(key, convertJsonValue(field.getValue()));
                    }
                }

                DocumentMetadata.Builder metadataBuilder = DocumentMetadata.builder()
                        .content(TextBlock.builder().text(contentText).build())
                        .docId(docId)
                        .chunkId(chunkId);
                if (extraPayload != null) {
                    metadataBuilder.payload(extraPayload);
                }
                DocumentMetadata metadata = metadataBuilder.build();

                Document doc = new Document(metadata);
                doc.setScore(score);
                docs.add(doc);
            }

            log.debug("向量检索完成: query_dims={}, results={}", dto.getQueryEmbedding().length, docs.size());
            return docs;
        } catch (Exception e) {
            // 检索失败不阻断 Agent 执行，返回空结果，但记录足够定位问题的上下文
            log.error("向量检索异常，返回空结果: collection={}, baseUrl={}, limit={}, error={}",
                    collectionName, baseUrl, dto.getLimit(), e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * 按 ID 删除向量
     */
    private Boolean doDelete(String id) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            ArrayNode points = body.putArray("points");
            points.add(id);

            HttpResponse<String> resp = sendPost("/collections/" + collectionName + "/points/delete?wait=true", body);
            boolean ok = resp.statusCode() == 200;
            if (ok) {
                log.info("向量删除成功: id={}", id);
            } else {
                log.warn("向量删除失败: id={}, status={}, body={}", id, resp.statusCode(), resp.body());
            }
            return ok;
        } catch (Exception e) {
            // 删除失败不应静默（会导致孤儿向量数据），rethrow 让调用者感知
            Metrics.counter("qdrant.error.count", "operation", "delete").increment();
            log.error("向量删除异常: id={}", id, e);
            throw new BusinessException(ErrorCode.RAG_STORE_ERROR, "向量删除失败: id=" + id, e);
        }
    }

    // ==================== HTTP 工具方法 ====================

    private HttpResponse<String> sendGet(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> sendPut(String path, JsonNode body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .timeout(Duration.ofSeconds(30))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> sendPost(String path, JsonNode body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .timeout(Duration.ofSeconds(30))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String extractContentText(Document doc) {
        if (doc.getMetadata() != null && doc.getMetadata().getContent() != null) {
            ContentBlock content = doc.getMetadata().getContent();
            if (content instanceof TextBlock tb) {
                return tb.getText();
            }
            return content.toString();
        }
        return "";
    }

    /**
     * 将 JsonNode 转换为 Java 原生对象
     *
     * <p>用于从 Qdrant payload 恢复额外字段时，把 JsonNode 转成 Map 可接受的类型。
     */
    private Object convertJsonValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isInt() || node.isLong()) {
            return node.asLong();
        }
        if (node.isDouble() || node.isFloat()) {
            return node.asDouble();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        // 复杂类型（object/array）直接用 toString 兜底
        return node.toString();
    }
}
