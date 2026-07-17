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
import io.lumina.common.core.BaseContext;
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
 * <p>多租户隔离：所有租户共享同一 collection，写入时把 {@code tenant_id}（可选 {@code kb_id}）
 * 写入 Qdrant payload，检索时下推 {@code filter.must[].tenant_id} 条件，并在 collection
 * 创建后建立 payload 索引以加速 pre-filter。tenant_id 取自 {@link BaseContext#getTenantId()}，
 * 与关键词检索路（{@code HybridKnowledge}）的取值方式一致。
 *
 * @author Lumina Team
 * @since 1.2.0
 */
@Slf4j
public class QdrantRestStore implements VDBStoreBase {

    /** payload 中租户字段的 key（写入 + 检索 filter 必须保持一致） */
    private static final String PAYLOAD_KEY_TENANT_ID = "tenant_id";
    /** payload 中知识库字段的 key（可选，用于按 kbId 二次过滤） */
    private static final String PAYLOAD_KEY_KB_ID = "kb_id";

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
            // 建 tenant_id payload 索引：让 Qdrant 在 ANN 之前先按租户 pre-filter，
            // 否则 filter 仍会扫所有 point 再事后剔除，多租户隔离形同虚设。
            createPayloadIndex(PAYLOAD_KEY_TENANT_ID, "keyword");
            createPayloadIndex(PAYLOAD_KEY_KB_ID, "keyword");
        } else {
            throw new BusinessException(ErrorCode.RAG_STORE_ERROR, "创建集合失败: HTTP " + resp.statusCode());
        }
    }

    /**
     * 在 collection 上建立 payload 字段索引
     *
     * <p>Qdrant 的 {@code PUT /collections/{name}/index}，{@code field_schema} 用 "keyword"
     * 适合等值过滤（tenant_id 是整数但取值离散，keyword 索引即可）。
     */
    private void createPayloadIndex(String fieldName, String schema) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("field_name", fieldName);
            body.put("field_schema", schema);
            HttpResponse<String> resp = sendPut("/collections/" + collectionName + "/index?wait=true", body);
            if (resp.statusCode() == 200) {
                log.info("Qdrant payload 索引创建成功: collection={}, field={}", collectionName, fieldName);
            } else {
                log.warn("Qdrant payload 索引创建返回非 200: collection={}, field={}, status={}, body={}",
                        collectionName, fieldName, resp.statusCode(), resp.body());
            }
        } catch (Exception e) {
            // 索引创建失败不中断启动——filter 仍能正确隔离，只是性能差（post-filter）
            log.warn("Qdrant payload 索引创建异常（隔离仍生效，仅性能退化）: collection={}, field={}, error={}",
                    collectionName, fieldName, e.getMessage());
        }
    }

    /**
     * 批量写入向量
     *
     * <p>每个 point 的 payload 必须含 {@code tenant_id}（必填）和 {@code kb_id}（可选），
     * 否则 {@link #doSearch} 的租户过滤会把所有租户的数据都搜出来——这是多租户隔离的根基。
     *
     * <p>tenant_id 优先取自 doc 已有的 payload（由调用方在 ingest 时 stamp），
     * 其次 fallback 到 {@link BaseContext#getTenantId()}（与 keyword 路一致，依赖
     * Reactor Context Propagation 跨线程传递）。
     */
    private void doAdd(List<Document> documents) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            ArrayNode points = body.putArray("points");

            // BaseContext 在 reactor.boundedElastic 上仍可读（Context Propagation 已配置），
            // 作为 doc payload 缺失时的兜底，确保 tenant_id 一定写入。
            Long contextTenantId = BaseContext.getTenantId();

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

                // 多租户隔离关键：强制写入 tenant_id（取值优先级：doc payload > BaseContext > 0）
                Long tenantId = resolveTenantId(doc, contextTenantId);
                payload.put(PAYLOAD_KEY_TENANT_ID, tenantId);

                Long kbId = resolveKbId(doc);
                if (kbId != null) {
                    payload.put(PAYLOAD_KEY_KB_ID, kbId);
                }

                Map<String, Object> extra = doc.getMetadata().getPayload();
                if (extra != null) {
                    for (Map.Entry<String, Object> e : extra.entrySet()) {
                        // 跳过保留字段（避免覆盖 tenant_id/kb_id/content/docId/chunkId）
                        if (isReservedPayloadKey(e.getKey()) || e.getValue() == null) {
                            continue;
                        }
                        payload.putPOJO(e.getKey(), e.getValue());
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
     * 解析 doc 的 tenant_id：优先 doc payload（ingest 时 stamp），fallback BaseContext，最后 0
     */
    private Long resolveTenantId(Document doc, Long contextTenantId) {
        Object fromPayload = doc.getPayloadValue(PAYLOAD_KEY_TENANT_ID);
        if (fromPayload != null) {
            return toLong(fromPayload);
        }
        // 也兼容历史调用方用驼峰 key 写入的情况
        Object fromPayloadCamel = doc.getPayloadValue("tenantId");
        if (fromPayloadCamel != null) {
            return toLong(fromPayloadCamel);
        }
        return contextTenantId != null ? contextTenantId : 0L;
    }

    /**
     * 解析 doc 的 kb_id：优先 doc payload（snake_case），其次驼峰 key，都没有返回 null
     */
    private Long resolveKbId(Document doc) {
        Object fromPayload = doc.getPayloadValue(PAYLOAD_KEY_KB_ID);
        if (fromPayload != null) {
            return toLong(fromPayload);
        }
        Object fromPayloadCamel = doc.getPayloadValue("kbId");
        return fromPayloadCamel != null ? toLong(fromPayloadCamel) : null;
    }

    private static Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(v)); } catch (NumberFormatException e) { return null; }
    }

    private static boolean isReservedPayloadKey(String key) {
        return "content".equals(key) || "docId".equals(key) || "chunkId".equals(key)
                || PAYLOAD_KEY_TENANT_ID.equals(key) || "tenantId".equals(key)
                || PAYLOAD_KEY_KB_ID.equals(key) || "kbId".equals(key);
    }

    /**
     * 向量相似度检索
     *
     * <p>下推 {@code filter.must[].tenant_id} 条件到 Qdrant：只有当前租户的向量才参与 ANN。
     * 这是多租户隔离的核心防线——之前所有租户向量都被搜出来，仅靠应用层事后过滤，
     * 而 ingest 时根本没写 tenant_id，事后过滤恒为 null 失效，等于卖点自反。
     *
     * <p>tenant_id 取自 {@link BaseContext#getTenantId()}。检索路径 reactor.boundedElastic 上
     * BaseContext 仍可读（与 keyword 路一致）。
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

            // 多租户隔离：下推 tenant_id filter 到 Qdrant（必填，缺值用 0 兜底以避免误命中其他租户）
            Long tenantId = BaseContext.getTenantId();
            long effectiveTenantId = tenantId != null ? tenantId : 0L;
            ObjectNode filter = body.putObject("filter");
            ArrayNode must = filter.putArray("must");
            ObjectNode tenantCond = must.addObject();
            tenantCond.put("key", PAYLOAD_KEY_TENANT_ID);
            tenantCond.putObject("match").put("value", effectiveTenantId);

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

            log.debug("向量检索完成: tenant={}, query_dims={}, results={}",
                    effectiveTenantId, dto.getQueryEmbedding().length, docs.size());
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
