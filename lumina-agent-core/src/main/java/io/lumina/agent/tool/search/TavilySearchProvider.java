package io.lumina.agent.tool.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Tavily Search API 适配器
 *
 * <p>认证：Bearer Token（Header）
 * <p>请求：POST JSON，字段 query / max_results
 * <p>响应：results[]，字段 title / url / content
 *
 * @author Lumina Team
 * @since 3.2.0
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "lumina.agent.search", name = "provider", havingValue = "tavily")
public class TavilySearchProvider implements SearchProvider {

    private static final String DEFAULT_URL = "https://api.tavily.com/search";

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
        String url = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl : DEFAULT_URL;

        String requestBody = objectMapper.writeValueAsString(new LinkedHashMap<>() {{
            put("query", query);
            put("max_results", count);
            put("include_answer", false);
            put("search_depth", "basic");
        }});

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Tavily 搜索失败: HTTP " + response.statusCode() + ", body=" + response.body());
        }

        return parseResponse(response.body());
    }

    private List<SearchResult> parseResponse(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode results = root.path("results");

        List<SearchResult> list = new ArrayList<>();
        if (results.isArray()) {
            for (JsonNode item : results) {
                list.add(new SearchResult(
                        item.path("title").asText(""),
                        item.path("url").asText(""),
                        item.path("content").asText(""),
                        "Tavily"
                ));
            }
        }
        return list;
    }

    @Override
    public String getProviderName() {
        return "tavily";
    }
}
