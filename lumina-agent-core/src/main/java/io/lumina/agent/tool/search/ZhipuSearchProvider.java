package io.lumina.agent.tool.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lumina.common.core.ErrorCode;
import io.lumina.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
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
 * 智谱 Web Search Pro 适配器
 *
 * <p>认证：Bearer Token（Header）
 * <p>请求：POST JSON，字段 search_query / count
 * <p>响应：search_result[]，字段 title / link / content / media
 *
 * @author Lumina Team
 * @since 3.2.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "lumina.agent.search", name = "provider", havingValue = "zhipu")
public class ZhipuSearchProvider implements SearchProvider {

    private static final String DEFAULT_URL = "https://open.bigmodel.cn/api/paas/v4/tools/web-search-pro";

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
            put("search_engine", "search_std");
            put("search_query", query);
            put("count", count);
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
            throw new BusinessException(ErrorCode.SEARCH_FAILED, "智谱搜索失败: HTTP " + response.statusCode() + ", body=" + response.body());
        }

        return parseResponse(response.body());
    }

    private List<SearchResult> parseResponse(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode results = root.path("search_result");

        List<SearchResult> list = new ArrayList<>();
        if (results.isArray()) {
            for (JsonNode item : results) {
                list.add(new SearchResult(
                        item.path("title").asText(""),
                        item.path("link").asText(""),
                        item.path("content").asText(""),
                        item.path("media").asText("智谱搜索")
                ));
            }
        }
        return list;
    }

    @Override
    public String getProviderName() {
        return "zhipu";
    }
}
