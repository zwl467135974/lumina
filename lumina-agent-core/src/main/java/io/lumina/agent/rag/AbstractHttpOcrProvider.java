package io.lumina.agent.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lumina.agent.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * OCR Provider HTTP 基类
 *
 * <p>封装通用的 HTTP 调用 + JSON 解析 + 异常处理逻辑，子类只需实现
 * {@link #buildRequest(byte[], String)} 和 {@link #extractText(String)}。
 *
 * @author Lumina Team
 * @since 3.3.1
 */
@Slf4j
public abstract class AbstractHttpOcrProvider implements OcrProvider {

    protected final String apiKey;
    protected final String secretKey;
    protected final HttpClient httpClient;
    protected final ObjectMapper objectMapper = JsonUtils.OBJECT_MAPPER;

    protected AbstractHttpOcrProvider(String apiKey, String secretKey) {
        this.apiKey = apiKey;
        this.secretKey = secretKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Override
    public String recognize(byte[] imageBytes, String language) {
        if (imageBytes == null || imageBytes.length == 0) {
            return "";
        }
        try {
            HttpRequest request = buildRequest(imageBytes, language);
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("{} OCR 调用失败: status={}, body={}", getName(),
                        response.statusCode(),
                        response.body().length() > 500 ? response.body().substring(0, 500) : response.body());
                return "";
            }

            return extractText(response.body());
        } catch (Exception e) {
            log.error("{} OCR 识别异常: {}", getName(), e.getMessage());
            return "";
        }
    }

    /**
     * 构建发送给 OCR 服务的 HTTP 请求
     */
    protected abstract HttpRequest buildRequest(byte[] imageBytes, String language) throws Exception;

    /**
     * 从 OCR 服务响应中提取纯文本
     */
    protected abstract String extractText(String responseBody) throws Exception;

    /**
     * 安全提取 JSON 节点的文本值
     */
    protected String safeText(JsonNode node, String... path) {
        JsonNode current = node;
        for (String key : path) {
            if (current == null) return "";
            current = current.path(key);
        }
        return current != null ? current.asText("") : "";
    }
}
