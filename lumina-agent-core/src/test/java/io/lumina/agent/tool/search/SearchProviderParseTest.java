package io.lumina.agent.tool.search;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 搜索 Provider 响应解析单元测试
 *
 * <p>通过反射测试各 Provider 的 parseResponse 方法，
 * 验证不同搜索引擎的 JSON 响应被正确归一化为 SearchResult。
 *
 * @author Lumina Team
 * @since 3.2.0
 */
class SearchProviderParseTest {

    // ==================== ZhipuSearchProvider ====================

    @Test
    void zhipuParseResponseExtractsFields() throws Exception {
        String json = """
            {
              "search_result": [
                {"title": "智谱AI", "link": "https://zhipu.ai", "content": "智谱AI官网", "media": "智谱"},
                {"title": "GLM模型", "link": "https://zhipu.ai/glm", "content": "GLM模型介绍", "media": "智谱"}
              ]
            }""";

        ZhipuSearchProvider provider = new ZhipuSearchProvider();
        List<SearchResult> results = invokeParse(provider, json);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getTitle()).isEqualTo("智谱AI");
        assertThat(results.get(0).getUrl()).isEqualTo("https://zhipu.ai");
        assertThat(results.get(0).getSnippet()).isEqualTo("智谱AI官网");
        assertThat(results.get(0).getSource()).isEqualTo("智谱");
    }

    @Test
    void zhipuParseEmptyResults() throws Exception {
        String json = "{\"search_result\": []}";

        ZhipuSearchProvider provider = new ZhipuSearchProvider();
        List<SearchResult> results = invokeParse(provider, json);

        assertThat(results).isEmpty();
    }

    @Test
    void zhipuParseMissingResultsArray() throws Exception {
        String json = "{\"error\": \"something went wrong\"}";

        ZhipuSearchProvider provider = new ZhipuSearchProvider();
        List<SearchResult> results = invokeParse(provider, json);

        assertThat(results).isEmpty();
    }

    @Test
    void zhipuParseMissingFieldsDefaultToEmpty() throws Exception {
        String json = "{\"search_result\": [{}]}";

        ZhipuSearchProvider provider = new ZhipuSearchProvider();
        List<SearchResult> results = invokeParse(provider, json);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTitle()).isEmpty();
        assertThat(results.get(0).getUrl()).isEmpty();
    }

    // ==================== TavilySearchProvider ====================

    @Test
    void tavilyParseResponseExtractsFields() throws Exception {
        String json = """
            {
              "results": [
                {"title": "Tavily", "url": "https://tavily.com", "content": "搜索API服务"}
              ]
            }""";

        TavilySearchProvider provider = new TavilySearchProvider();
        List<SearchResult> results = invokeParse(provider, json);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTitle()).isEqualTo("Tavily");
        assertThat(results.get(0).getUrl()).isEqualTo("https://tavily.com");
        assertThat(results.get(0).getSnippet()).isEqualTo("搜索API服务");
        assertThat(results.get(0).getSource()).isEqualTo("Tavily");
    }

    @Test
    void tavilyParseEmptyResults() throws Exception {
        TavilySearchProvider provider = new TavilySearchProvider();
        List<SearchResult> results = invokeParse(provider, "{\"results\": []}");

        assertThat(results).isEmpty();
    }

    // ==================== SerpApiSearchProvider ====================

    @Test
    void serpApiParseResponseExtractsFields() throws Exception {
        String json = """
            {
              "organic_results": [
                {"title": "Google搜索", "link": "https://google.com", "snippet": "搜索引擎"}
              ]
            }""";

        SerpApiSearchProvider provider = new SerpApiSearchProvider();
        List<SearchResult> results = invokeParse(provider, json);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTitle()).isEqualTo("Google搜索");
        assertThat(results.get(0).getUrl()).isEqualTo("https://google.com");
        assertThat(results.get(0).getSnippet()).isEqualTo("搜索引擎");
        assertThat(results.get(0).getSource()).isEqualTo("Google (SerpAPI)");
    }

    @Test
    void serpApiParseNoOrganicResults() throws Exception {
        SerpApiSearchProvider provider = new SerpApiSearchProvider();
        List<SearchResult> results = invokeParse(provider, "{\"search_metadata\": {}}");

        assertThat(results).isEmpty();
    }

    // ==================== BraveSearchProvider ====================

    @Test
    void braveParseResponseExtractsFields() throws Exception {
        String json = """
            {
              "web": {
                "results": [
                  {"title": "Brave Search", "url": "https://brave.com", "description": "隐私搜索引擎"}
                ]
              }
            }""";

        BraveSearchProvider provider = new BraveSearchProvider();
        List<SearchResult> results = invokeParse(provider, json);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTitle()).isEqualTo("Brave Search");
        assertThat(results.get(0).getUrl()).isEqualTo("https://brave.com");
        assertThat(results.get(0).getSnippet()).isEqualTo("隐私搜索引擎");
        assertThat(results.get(0).getSource()).isEqualTo("Brave Search");
    }

    @Test
    void braveParseMissingWebObject() throws Exception {
        BraveSearchProvider provider = new BraveSearchProvider();
        List<SearchResult> results = invokeParse(provider, "{\"query\": \"test\"}");

        assertThat(results).isEmpty();
    }

    @Test
    void braveParseMultipleResults() throws Exception {
        String json = """
            {
              "web": {
                "results": [
                  {"title": "结果1", "url": "https://1.com", "description": "描述1"},
                  {"title": "结果2", "url": "https://2.com", "description": "描述2"},
                  {"title": "结果3", "url": "https://3.com", "description": "描述3"}
                ]
              }
            }""";

        BraveSearchProvider provider = new BraveSearchProvider();
        List<SearchResult> results = invokeParse(provider, json);

        assertThat(results).hasSize(3);
        assertThat(results.get(2).getUrl()).isEqualTo("https://3.com");
    }

    // ==================== ProviderName ====================

    @Test
    void providerNamesAreCorrect() {
        assertThat(new ZhipuSearchProvider().getProviderName()).isEqualTo("zhipu");
        assertThat(new TavilySearchProvider().getProviderName()).isEqualTo("tavily");
        assertThat(new SerpApiSearchProvider().getProviderName()).isEqualTo("serpapi");
        assertThat(new BraveSearchProvider().getProviderName()).isEqualTo("brave");
    }

    // ==================== SearchResult DTO ====================

    @Test
    void searchResultConstructorSetsAllFields() {
        SearchResult result = new SearchResult("标题", "url", "摘要", "来源");

        assertThat(result.getTitle()).isEqualTo("标题");
        assertThat(result.getUrl()).isEqualTo("url");
        assertThat(result.getSnippet()).isEqualTo("摘要");
        assertThat(result.getSource()).isEqualTo("来源");
    }

    @Test
    void searchResultDefaultConstructor() {
        SearchResult result = new SearchResult();
        result.setTitle("test");

        assertThat(result.getTitle()).isEqualTo("test");
        assertThat(result.getUrl()).isNull();
    }

    // ==================== 反射辅助 ====================

    @SuppressWarnings("unchecked")
    private List<SearchResult> invokeParse(Object provider, String json) throws Exception {
        Method method = provider.getClass().getDeclaredMethod("parseResponse", String.class);
        method.setAccessible(true);
        return (List<SearchResult>) method.invoke(provider, json);
    }
}
