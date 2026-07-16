package io.lumina.agent.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.agentscope.core.embedding.EmbeddingModel;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import io.lumina.common.core.ErrorCode;
import io.lumina.common.exception.BusinessException;
import io.lumina.agent.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * OpenAI 兼容 Embedding 模型（轻量 HTTP 实现）
 *
 * <p>直接调用 OpenAI 兼容 /embeddings 端点，不依赖 OpenAI Java SDK。
 * <p>适用于硅基流动 SiliconFlow 等不支持 dimensions 参数的兼容服务。
 *
 * @author Lumina Team
 * @since 1.2.0
 */
@Slf4j
public class OpenAICompatibleEmbeddingModel implements EmbeddingModel {

    private final String apiKey;
    private final String modelName;
    private final int dimensions;
    private final String embeddingsUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = JsonUtils.OBJECT_MAPPER;

    private final boolean sendDimensions;

    /**
     * @param apiKey     API Key
     * @param modelName  模型名称（如 BAAI/bge-large-zh-v1.5）
     * @param baseUrl    服务地址（如 https://api.siliconflow.cn/v1）
     * @param dimensions 向量维度
     * @param sendDimensions 是否将 dimensions 发送给 API（true 适用于 OpenAI embedding-3 等支持缩减维度的模型，
     *                       false 适用于 SiliconFlow 等不支持该参数的服务）
     */
    public OpenAICompatibleEmbeddingModel(String apiKey, String modelName, String baseUrl, int dimensions, boolean sendDimensions) {
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.dimensions = dimensions;
        this.sendDimensions = sendDimensions;
        this.embeddingsUrl = baseUrl.endsWith("/")
                ? baseUrl + "embeddings"
                : baseUrl + "/embeddings";
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        log.info("OpenAI 兼容 Embedding 初始化: url={}, model={}, dims={}, sendDimensions={}",
                embeddingsUrl, modelName, dimensions, sendDimensions);
    }

    /**
     * 向后兼容构造器（默认不发 dimensions）
     */
    public OpenAICompatibleEmbeddingModel(String apiKey, String modelName, String baseUrl, int dimensions) {
        this(apiKey, modelName, baseUrl, dimensions, false);
    }

    @Override
    public Mono<double[]> embed(ContentBlock content) {
        if (content == null) {
            return Mono.error(new IllegalArgumentException("ContentBlock cannot be null"));
        }

        String text;
        if (content instanceof TextBlock textBlock) {
            text = textBlock.getText();
        } else {
            text = content.toString();
        }

        if (text == null || text.isBlank()) {
            return Mono.error(new IllegalArgumentException("文本内容为空"));
        }

        return Mono.fromCallable(() -> doEmbed(text))
                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    private double[] doEmbed(String text) throws Exception {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", modelName);
        requestBody.put("input", text);
        requestBody.put("encoding_format", "float");
        // 按需发送 dimensions 参数（OpenAI embedding-3 / 智谱 embedding-3 支持，SiliconFlow 不支持）
        if (sendDimensions && dimensions > 0) {
            requestBody.put("dimensions", dimensions);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(embeddingsUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                .timeout(Duration.ofSeconds(60))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.error("Embedding API 调用失败: status={}, body={}", response.statusCode(),
                    response.body().length() > 500 ? response.body().substring(0, 500) : response.body());
            throw new BusinessException(ErrorCode.RAG_EMBEDDING_FAILED, "Embedding API 错误: HTTP " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        ArrayNode embeddings = (ArrayNode) root.path("data");
        if (embeddings.isEmpty()) {
            throw new BusinessException(ErrorCode.RAG_EMBEDDING_FAILED, "Embedding 响应无数据");
        }

        ArrayNode vector = (ArrayNode) embeddings.get(0).path("embedding");
        double[] result = new double[vector.size()];
        for (int i = 0; i < vector.size(); i++) {
            result[i] = vector.get(i).asDouble();
        }
        return result;
    }

    @Override
    public String getModelName() {
        return modelName;
    }

    @Override
    public int getDimensions() {
        return dimensions;
    }
}
