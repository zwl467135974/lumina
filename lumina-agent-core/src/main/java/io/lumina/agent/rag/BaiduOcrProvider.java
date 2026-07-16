package io.lumina.agent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

/**
 * 百度智能云 OCR（通用文字识别）
 *
 * <p>API 文档：https://ai.baidu.com/ai-doc/OCR/Ck3h7y2ia
 * <p>请求方式：POST base64 图片到 /rest/2.0/ocr/v1/general_basic
 * <p>鉴权方式：access_token（需用 API Key + Secret Key 换取）
 *
 * <p>配置：
 * <pre>
 * lumina.rag.reader.ocr.provider: baidu
 * lumina.rag.reader.ocr.api-key: 百度API Key
 * lumina.rag.reader.ocr.secret-key: 百度Secret Key
 * </pre>
 *
 * @author Lumina Team
 * @since 3.3.1
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "lumina.rag.reader.ocr", name = "provider", havingValue = "baidu")
public class BaiduOcrProvider extends AbstractHttpOcrProvider {

    private static final String OCR_URL = "https://aip.baidubce.com/rest/2.0/ocr/v1/general_basic";
    private static final String TOKEN_URL = "https://aip.baidubce.com/oauth/2.0/token";

    private volatile String cachedAccessToken;
    private volatile long tokenExpireAt = 0;

    public BaiduOcrProvider(
            @Value("${lumina.rag.reader.ocr.api-key:}") String apiKey,
            @Value("${lumina.rag.reader.ocr.secret-key:}") String secretKey) {
        super(apiKey, secretKey);
        log.info("百度 OCR 初始化");
    }

    @Override
    protected HttpRequest buildRequest(byte[] imageBytes, String language) throws Exception {
        String token = getAccessToken();
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);

        // 百度 OCR 使用 form-urlencoded + URL encoded base64
        String body = "image=" + java.net.URLEncoder.encode(base64Image, "UTF-8")
                + "&language_type=" + ("chi_sim".equals(language) ? "CHN_ENG" : "ENG");

        return HttpRequest.newBuilder()
                .uri(URI.create(OCR_URL + "?access_token=" + token))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(30))
                .build();
    }

    @Override
    protected String extractText(String responseBody) throws Exception {
        var root = objectMapper.readTree(responseBody);
        var wordsResult = root.path("words_result");
        if (wordsResult.isMissingNode() || !wordsResult.isArray()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (var item : wordsResult) {
            sb.append(item.path("words").asText("")).append("\n");
        }
        return sb.toString();
    }

    /**
     * 获取百度 access_token（带缓存，有效期默认 30 天）
     */
    private String getAccessToken() throws Exception {
        long now = System.currentTimeMillis();
        if (cachedAccessToken != null && now < tokenExpireAt) {
            return cachedAccessToken;
        }

        String body = "grant_type=client_credentials"
                + "&client_id=" + apiKey
                + "&client_secret=" + secretKey;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        var root = objectMapper.readTree(response.body());
        cachedAccessToken = root.path("access_token").asText("");
        long expiresIn = root.path("expires_in").asLong(2592000L);
        tokenExpireAt = now + (expiresIn - 60) * 1000; // 提前 60 秒过期

        log.info("百度 OCR access_token 已刷新, 有效期={}s", expiresIn);
        return cachedAccessToken;
    }

    @Override
    public String getName() {
        return "baidu";
    }
}
