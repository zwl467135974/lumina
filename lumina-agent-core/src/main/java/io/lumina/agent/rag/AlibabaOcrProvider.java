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
 * 阿里云 OCR（通用文字识别）
 *
 * <p>API 文档：https://help.aliyun.com/zh/ocr/developer-reference/api-ocr-api-2021-07-07-recognizebasic
 * <p>请求方式：POST JSON 到 ocr-api.cn-hangzhou.aliyuncs.com
 * <p>鉴权方式：阿里云 ACS 签名（此处使用简化的 Bearer Token 方式 — AppCode）
 *
 * <p>注意：阿里云正式签名需要 ACS3-HMAC-SHA256 计算。本实现使用更简单的 AppCode 方式
 * （在阿里云市场购买 OCR 服务后获得 AppCode），适合快速集成。
 * 生产环境如需精细控制，建议使用阿里云 SDK（com.aliyun:ocr_api20210707）。
 *
 * <p>配置：
 * <pre>
 * lumina.rag.reader.ocr.provider: alibaba
 * lumina.rag.reader.ocr.api-key: 阿里云AppCode
 * </pre>
 *
 * @author Lumina Team
 * @since 3.3.1
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "lumina.rag.reader.ocr", name = "provider", havingValue = "alibaba")
public class AlibabaOcrProvider extends AbstractHttpOcrProvider {

    /**
     * 阿里云市场 OCR 接口（通用文字识别 - AppCode 模式）
     */
    private static final String ENDPOINT =
            "https://ocrapi-recognizedata.taobao.com/ocrservice/ocr";

    public AlibabaOcrProvider(
            @Value("${lumina.rag.reader.ocr.api-key:}") String apiKey,
            @Value("${lumina.rag.reader.ocr.secret-key:}") String secretKey) {
        super(apiKey, secretKey);
        log.info("阿里云 OCR 初始化（AppCode 模式）");
    }

    @Override
    protected HttpRequest buildRequest(byte[] imageBytes, String language) throws Exception {
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);

        ObjectNode body = objectMapper.createObjectNode();
        body.put("img", base64Image);
        body.put("prob", false);
        if ("chi_sim".equals(language)) {
            body.put("languageType", "CHN_ENG");
        }

        return HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT))
                .header("Content-Type", "application/json")
                .header("Authorization", "APPCODE " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .timeout(Duration.ofSeconds(30))
                .build();
    }

    @Override
    protected String extractText(String responseBody) throws Exception {
        var root = objectMapper.readTree(responseBody);

        // 检查错误
        var code = root.path("code");
        if (!code.isMissingNode() && code.asInt() != 200) {
            log.error("阿里云 OCR 返回错误: code={}, msg={}",
                    code.asText(), root.path("msg").asText());
            return "";
        }

        // content 字段包含识别结果
        var content = root.path("content");
        if (!content.isMissingNode()) {
            return content.asText("");
        }

        // 备用：words 数组
        var words = root.path("words");
        if (words.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (var w : words) {
                sb.append(w.asText("")).append("\n");
            }
            return sb.toString();
        }

        return "";
    }

    @Override
    public String getName() {
        return "alibaba";
    }
}
