package io.lumina.base.tool.search;

import java.util.List;

/**
 * 搜索 API 适配层策略接口
 *
 * <p>各搜索引擎（智谱/Tavily/SerpAPI/Brave）实现此接口，
 * 通过 {@code @ConditionalOnProperty} 按 {@code lumina.agent.search.provider} 配置注入。
 *
 * @author Lumina Team
 * @since 3.2.0
 */
public interface SearchProvider {

    /**
     * 执行搜索
     *
     * @param query 搜索关键词
     * @param count 期望返回结果数（各 Provider 尽力满足，可能少于）
     * @return 统一格式的搜索结果列表
     * @throws Exception 搜索失败时抛出
     */
    List<SearchResult> search(String query, int count) throws Exception;

    /**
     * Provider 标识（如 zhipu/tavily/serpapi/brave）
     *
     * @return provider 名称
     */
    String getProviderName();
}
