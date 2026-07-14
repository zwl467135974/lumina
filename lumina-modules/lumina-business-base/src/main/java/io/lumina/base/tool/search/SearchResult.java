package io.lumina.base.tool.search;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一搜索结果
 *
 * <p>各搜索 API Provider 将原生响应归一化为此结构。
 *
 * @author Lumina Team
 * @since 3.2.0
 */
@Data
public class SearchResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 结果标题
     */
    private String title;

    /**
     * 结果链接
     */
    private String url;

    /**
     * 摘要内容
     */
    private String snippet;

    /**
     * 来源（网站名/搜索引擎）
     */
    private String source;

    public SearchResult() {
    }

    public SearchResult(String title, String url, String snippet, String source) {
        this.title = title;
        this.url = url;
        this.snippet = snippet;
        this.source = source;
    }
}
