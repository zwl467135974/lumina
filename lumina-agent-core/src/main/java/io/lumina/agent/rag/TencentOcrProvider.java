package io.lumina.agent.rag;

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
 * 腾讯云 OCR（通用印刷体识别）
 *
 * <p>API 文档：https://cloud.tencent.com/document/product/866/33526
 * <p>请求方式：POST JSON 到 ocr.tencentcloudapi.com
 * <p>鉴权方式：TC3-HMAC-SHA256 签名（复杂签名，此处简化为 Header 传 SecretId/SecretKey）
 *
 * <p>注意：腾讯云正式签名需要 TC3-HMAC-SHA256 计算，本实现使用简化的 Header 直传方式。
 * 生产环境建议使用腾讯云 SDK（com.tencentcloudapi:tencentcloud-sdk-java-ocr）替换。
 *
 * <p>配置：
 * <pre>
 * lumina.rag.reader.ocr.provider: tencent
 * lumina.rag.reader.ocr.api-key: 腾讯云SecretId
 * lumina.rag.reader.ocr.secret-key: 腾讯云SecretKey
 * </pre>
 *
 * @author Lumina Team
 * @since 3.3.1
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "lumina.rag.reader.ocr", name = "provider", havingValue = "tencent")
public class TencentOcrProvider extends AbstractHttpOcrProvider {

    private static final String ENDPOINT = "https://ocr.tencentcloudapi.com";

    public TencentOcrProvider(
            @Value("${lumina.rag.reader.ocr.api-key:}") String apiKey,
            @Value("${lumina.rag.reader.ocr.secret-key:}") String secretKey) {
        super(apiKey, secretKey);
        log.info("腾讯云 OCR 初始化");
    }

    @Override
    protected HttpRequest buildRequest(byte[] imageBytes, String language) throws Exception {
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);

        // 腾讯云 OCR API 参数（GeneralBasic 通用印刷体识别）
        ObjectNode params = objectMapper.createObjectNode();
        params.put("ImageBase64", base64Image);
        if ("chi_sim".equals(language)) {
            params.put("LanguageType", "auto");
        }

        return HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT))
                .header("Content-Type", "application/json")
                .header("X-TC-Action", "GeneralBasic")
                .header("X-TC-Version", "2018-11-19")
                .header("Authorization", "TC3-HMAC-SHA256 Credential=" + apiKey + "/" + secretKey)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(params)))
                .timeout(Duration.ofSeconds(30))
                .build();
    }

    @Override
    protected String extractText(String responseBody) throws Exception {
        var root = objectMapper.readTree(responseBody);
        var textDetections = root.path("Response").path("TextDetections");
        if (textDetections.isMissingNode() || !textDetections.isArray()) {
            // 检查错误
            var error = root.path("Response").path("Error");
            if (!error.isMissingNode()) {
                log.error("腾讯云 OCR 返回错误: code={}, message={}",
                        error.path("Code").asText(), error.path("Message").asText());
            }
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (var item : textDetections) {
            sb.append(item.path("DetectedText").asText("")).append("\n");
        }
        return sb.toString();
    }

    @Override
    public String getName() {
        return "tencent";
    }
}
