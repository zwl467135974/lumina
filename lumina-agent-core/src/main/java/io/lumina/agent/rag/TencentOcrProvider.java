package io.lumina.agent.rag;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 腾讯云 OCR（通用印刷体识别 GeneralBasicOCR）
 *
 * <p>API 文档：https://cloud.tencent.com/document/product/866/33526
 * <p>鉴权方式：TC3-HMAC-SHA256 签名（完整实现，按官方规范计算签名）
 * <p>API 参考：https://cloud.tencent.com/document/api/866/33518
 *
 * <p>配置：
 * <pre>
 * lumina.rag.reader.ocr.provider: tencent
 * lumina.rag.reader.ocr.api-key: 腾讯云 SecretId
 * lumina.rag.reader.ocr.secret-key: 腾讯云 SecretKey
 * </pre>
 *
 * @author Lumina Team
 * @since 3.3.1
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "lumina.rag.reader.ocr", name = "provider", havingValue = "tencent")
public class TencentOcrProvider extends AbstractHttpOcrProvider {

    private static final String SERVICE = "ocr";
    private static final String VERSION = "2018-11-19";
    private static final String ACTION = "GeneralBasicOCR";
    private static final String ENDPOINT = "ocr.tencentcloudapi.com";

    public TencentOcrProvider(
            @Value("${lumina.rag.reader.ocr.api-key:}") String apiKey,
            @Value("${lumina.rag.reader.ocr.secret-key:}") String secretKey) {
        super(apiKey, secretKey);
        log.info("腾讯云 OCR 初始化（TC3-HMAC-SHA256 签名）");
    }

    @Override
    protected HttpRequest buildRequest(byte[] imageBytes, String language) throws Exception {
        // 请求体：ImageBase64 + LanguageType
        ObjectNode params = objectMapper.createObjectNode();
        params.put("ImageBase64",
                java.util.Base64.getEncoder().encodeToString(imageBytes));
        if ("chi_sim".equals(language)) {
            params.put("LanguageType", "auto");
        }
        String payload = objectMapper.writeValueAsString(params);
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);

        // 时间戳
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        String timestamp = String.valueOf(now.toEpochSecond());
        String date = now.format(DateTimeFormatter.ISO_LOCAL_DATE);

        // 1. 计算请求体 SHA-256
        String hashedPayload = sha256Hex(payloadBytes);

        // 2. 构造 CanonicalRequest
        String canonicalRequest = String.join("\n",
                "POST",
                "/",
                "",
                "content-type:application/json; charset=utf-8\n" +
                        "host:" + ENDPOINT,
                "",
                "content-type;host",
                hashedPayload);

        // 3. 计算 CanonicalRequest SHA-256
        String hashedCanonicalRequest = sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));

        // 4. 构造 StringToSign
        String credentialScope = date + "/" + SERVICE + "/tc3_request";
        String stringToSign = String.join("\n",
                "TC3-HMAC-SHA256",
                timestamp,
                credentialScope,
                hashedCanonicalRequest);

        // 5. 计算签名（派生密钥链）
        byte[] secretDate = hmacSha256(("TC3" + secretKey).getBytes(StandardCharsets.UTF_8), date);
        byte[] secretService = hmacSha256(secretDate, SERVICE);
        byte[] secretSigning = hmacSha256(secretService, "tc3_request");
        String signature = bytesToHex(hmacSha256(secretSigning, stringToSign));

        // 6. 构造 Authorization header
        String authorization = "TC3-HMAC-SHA256 " +
                "Credential=" + apiKey + "/" + credentialScope +
                ", SignedHeaders=content-type;host" +
                ", Signature=" + signature;

        return HttpRequest.newBuilder()
                .uri(URI.create("https://" + ENDPOINT))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Host", ENDPOINT)
                .header("X-TC-Action", ACTION)
                .header("X-TC-Version", VERSION)
                .header("X-TC-Timestamp", timestamp)
                .header("Authorization", authorization)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .timeout(Duration.ofSeconds(30))
                .build();
    }

    @Override
    protected String extractText(String responseBody) throws Exception {
        var root = objectMapper.readTree(responseBody);
        var response = root.path("Response");

        // 检查错误
        var error = response.path("Error");
        if (!error.isMissingNode()) {
            log.error("腾讯云 OCR 返回错误: code={}, message={}",
                    error.path("Code").asText(), error.path("Message").asText());
            return "";
        }

        var textDetections = response.path("TextDetections");
        if (!textDetections.isArray()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (var item : textDetections) {
            sb.append(item.path("DetectedText").asText("")).append("\n");
        }
        return sb.toString();
    }

    // ==================== HMAC/SHA256 工具 ====================

    private static String sha256Hex(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return bytesToHex(md.digest(data));
    }

    private static byte[] hmacSha256(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @Override
    public String getName() {
        return "tencent";
    }
}
