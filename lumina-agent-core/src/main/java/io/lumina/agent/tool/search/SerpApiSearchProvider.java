package io.lumina.agent.tool.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * SerpAPI 适配器
 *
 * <p>认证：API Key 作为 URL Query Parameter（非 Header）
 * <p>请求：GET，参数 engine / q / api_key / num
 * <p>响应：organic_results[]，字段 title / link / snippet
 *
 * @author Lumina Team
 * @since 3.2.0
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "lumina.agent.search", name = "provider", havingValue = "serpapi")
public class SerpApiSearchProvider implements SearchProvider {

    private static final String DEFAULT_URL = "https://serpapi.com/search";

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
        String url = base + "?engine=google"
                + "&q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&num=" + count
                + "&api_key=" + apiKey;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("SerpAPI 搜索失败: HTTP " + response.statusCode() + ", body=" + response.body());
        }

        return parseResponse(response.body());
    }

    private List<SearchResult> parseResponse(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode results = root.path("organic_results");

        List<SearchResult> list = new ArrayList<>();
        if (results.isArray()) {
            for (JsonNode item : results) {
                list.add(new SearchResult(
                        item.path("title").asText(""),
                        item.path("link").asText(""),
                        item.path("snippet").asText(""),
                        "Google (SerpAPI)"
                ));
            }
        }
        return list;
    }

    @Override
    public String getProviderName() {
        return "serpapi";
    }
}
