package io.lumina.agent.rag;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.UUID;

/**
 * 阿里云 OCR（通用文字识别 RecognizeBasic）
 *
 * <p>API 文档：https://help.aliyun.com/zh/ocr/developer-reference/api-ocr-api-2021-07-07-recognizebasic
 * <p>鉴权方式：ACS3-HMAC-SHA256 签名（阿里云 OpenAPI V3 签名规范，完整实现）
 * <p>请求方式：POST 原始图片字节到 HTTP body（官方 API 接受 body 传图，非 base64）
 * <p>响应：RequestId + Data.content（文字块汇总文本）
 *
 * <p>配置：
 * <pre>
 * lumina.rag.reader.ocr.provider: alibaba
 * lumina.rag.reader.ocr.api-key: 阿里云 AccessKeyId
 * lumina.rag.reader.ocr.secret-key: 阿里云 AccessKeySecret
 * </pre>
 *
 * @author Lumina Team
 * @since 3.3.1
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "lumina.rag.reader.ocr", name = "provider", havingValue = "alibaba")
public class AlibabaOcrProvider extends AbstractHttpOcrProvider {

    private static final String HOST = "ocr-api.cn-hangzhou.aliyuncs.com";
    private static final String SERVICE = "ocr-api";
    private static final String VERSION = "2021-07-07";
    private static final String ACTION = "RecognizeBasic";

    public AlibabaOcrProvider(
            @Value("${lumina.rag.reader.ocr.api-key:}") String apiKey,
            @Value("${lumina.rag.reader.ocr.secret-key:}") String secretKey) {
        super(apiKey, secretKey);
        log.info("阿里云 OCR 初始化（ACS3-HMAC-SHA256 签名, RecognizeBasic）");
    }

    @Override
    protected HttpRequest buildRequest(byte[] imageBytes, String language) throws Exception {
        // ACS3 签名需要 payload 的 SHA-256
        String hashedPayload = sha256Hex(imageBytes);
        String nonce = UUID.randomUUID().toString();

        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        String timestamp = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));
        String date = now.format(DateTimeFormatter.ISO_LOCAL_DATE);

        // 1. CanonicalRequest
        String canonicalRequest = String.join("\n",
                "POST",
                "/",
                "",
                "host:" + HOST + "\n" +
                        "x-acs-action:" + ACTION + "\n" +
                        "x-acs-content-sha256:" + hashedPayload + "\n" +
                        "x-acs-date:" + timestamp + "\n" +
                        "x-acs-signature-nonce:" + nonce + "\n" +
                        "x-acs-version:" + VERSION,
                "",
                "host;x-acs-action;x-acs-content-sha256;x-acs-date;x-acs-signature-nonce;x-acs-version",
                hashedPayload);

        // 2. StringToSign
        String credentialScope = date + "/" + SERVICE;
        String hashedCanonicalRequest = sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));
        String stringToSign = "ACS3-HMAC-SHA256\n" + hashedCanonicalRequest;

        // 3. 签名
        String signature = bytesToHex(hmacSha256(
                secretKey.getBytes(StandardCharsets.UTF_8), stringToSign));

        // 4. Authorization
        String authorization = "ACS3-HMAC-SHA256 Credential=" + apiKey + "/" + credentialScope +
                ",SignedHeaders=host;x-acs-action;x-acs-content-sha256;x-acs-date;x-acs-signature-nonce;x-acs-version" +
                ",Signature=" + signature;

        return HttpRequest.newBuilder()
                .uri(URI.create("https://" + HOST))
                .header("Content-Type", "application/octet-stream")
                .header("Host", HOST)
                .header("x-acs-action", ACTION)
                .header("x-acs-version", VERSION)
                .header("x-acs-date", timestamp)
                .header("x-acs-signature-nonce", nonce)
                .header("x-acs-content-sha256", hashedPayload)
                .header("Authorization", authorization)
                .POST(HttpRequest.BodyPublishers.ofByteArray(imageBytes))
                .timeout(Duration.ofSeconds(30))
                .build();
    }

    @Override
    protected String extractText(String responseBody) throws Exception {
        var root = objectMapper.readTree(responseBody);

        // 检查错误
        String code = root.path("Code").asText("");
        if (!code.isBlank() && !code.equals("0") && !code.equals("null")) {
            log.error("阿里云 OCR 返回错误: Code={}, Message={}",
                    code, root.path("Message").asText(""));
            return "";
        }

        // Data.content 是识别出的文字块汇总
        var data = root.path("Data");
        if (!data.isMissingNode()) {
            String content = data.path("content").asText("");
            if (!content.isBlank()) {
                return content;
            }
            // 备用：Data.prism_wordsInfo[].word
            var wordsInfo = data.path("prism_wordsInfo");
            if (wordsInfo.isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode w : wordsInfo) {
                    sb.append(w.path("word").asText("")).append("\n");
                }
                return sb.toString();
            }
        }

        return "";
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
        return "alibaba";
    }
}
