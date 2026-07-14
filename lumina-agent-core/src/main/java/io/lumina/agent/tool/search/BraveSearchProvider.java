package io.lumina.agent.tool.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lumina.common.core.ErrorCode;
import io.lumina.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Brave Search API 适配器
 *
 * <p>认证：自定义 Header X-Subscription-Token（非 Authorization）
 * <p>请求：GET，参数 q / count
 * <p>响应：web.results[]，字段 title / url / description
 *
 * @author Lumina Team
 * @since 3.2.0
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "lumina.agent.search", name = "provider", havingValue = "brave")
public class BraveSearchProvider implements SearchProvider {

    private static final String DEFAULT_URL = "https://api.search.brave.com/res/v1/web/search";

    @Value("${lumina.agent.search.api-key:}")
    private String apiKey;

    @Value("${lumina.agent.search.base-url:}")
    private String baseUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public List<SearchResult> search(String query, int count) throws Exception {
        String base = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl : DEFAULT_URL;
        String url = base + "?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&count=" + Math.min(count, 20); // Brave 最大 20

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("X-Subscription-Token", apiKey)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new BusinessException(ErrorCode.SEARCH_FAILED, "Brave 搜索失败: HTTP " + response.statusCode() + ", body=" + response.body());
        }

        return parseResponse(response.body());
    }

    private List<SearchResult> parseResponse(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode results = root.path("web").path("results");

        List<SearchResult> list = new ArrayList<>();
        if (results.isArray()) {
            for (JsonNode item : results) {
                list.add(new SearchResult(
                        item.path("title").asText(""),
                        item.path("url").asText(""),
                        item.path("description").asText(""),
                        "Brave Search"
                ));
            }
        }
        return list;
    }

    @Override
    public String getProviderName() {
        return "brave";
    }
}
