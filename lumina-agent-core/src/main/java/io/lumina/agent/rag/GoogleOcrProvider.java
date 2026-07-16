package io.lumina.agent.rag;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.Base64;

/**
 * Google Cloud Vision OCR（DOCUMENT_TEXT_DETECTION）
 *
 * <p>API 文档：https://cloud.google.com/vision/docs/ocr
 * <p>请求方式：POST JSON 到 vision.googleapis.com/v1/images:annotate
 * <p>鉴权方式：API Key（query 参数 key=xxx）
 *
 * <p>配置：
 * <pre>
 * lumina.rag.reader.ocr.provider: google
 * lumina.rag.reader.ocr.api-key: Google Cloud API Key
 * </pre>
 *
 * @author Lumina Team
 * @since 3.3.1
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "lumina.rag.reader.ocr", name = "provider", havingValue = "google")
public class GoogleOcrProvider extends AbstractHttpOcrProvider {

    private static final String ENDPOINT = "https://vision.googleapis.com/v1/images:annotate";

    public GoogleOcrProvider(
            @Value("${lumina.rag.reader.ocr.api-key:}") String apiKey,
            @Value("${lumina.rag.reader.ocr.secret-key:}") String secretKey) {
        super(apiKey, secretKey);
        log.info("Google Cloud Vision OCR 初始化");
    }

    @Override
    protected HttpRequest buildRequest(byte[] imageBytes, String language) throws Exception {
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);

        // Google Vision API: DOCUMENT_TEXT_DETECTION
        ObjectNode request = objectMapper.createObjectNode();
        ObjectNode feature = request.putObject("features").putArray("features").addObject();
        feature.put("type", "DOCUMENT_TEXT_DETECTION");

        ObjectNode image = request.putObject("image");
        image.put("content", base64Image);

        if (language != null && !language.isBlank()) {
            ArrayNode hints = request.putObject("imageContext")
                    .putArray("languageHints");
            // chi_sim → zh, eng → en
            hints.add("chi_sim".equals(language) ? "zh-Hans" : "en");
        }

        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode requests = body.putArray("requests");
        requests.add(request);

        return HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT + "?key=" + apiKey))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .timeout(Duration.ofSeconds(30))
                .build();
    }

    @Override
    protected String extractText(String responseBody) throws Exception {
        var root = objectMapper.readTree(responseBody);
        var responses = root.path("responses");
        if (!responses.isArray() || responses.isEmpty()) {
            return "";
        }

        // 检查错误
        var error = responses.get(0).path("error");
        if (!error.isMissingNode()) {
            log.error("Google Vision OCR 返回错误: {}", error.path("message").asText());
            return "";
        }

        // fullTextAnnotation.text 包含完整识别文本
        var fullText = responses.get(0).path("fullTextAnnotation").path("text");
        return fullText.asText("");
    }

    @Override
    public String getName() {
        return "google";
    }
}
