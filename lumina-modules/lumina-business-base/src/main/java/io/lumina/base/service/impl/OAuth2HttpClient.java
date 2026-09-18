package io.lumina.base.service.impl;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * OAuth2 HTTP 客户端（token 交换 / userinfo 拉取）
 *
 * <p>独立小接口便于单测 mock；实现基于 RestClient（项目 web 栈自带）。
 *
 * @author Lumina Team
 * @since 3.12.0
 */
public interface OAuth2HttpClient {

    /** form POST（token 端点），返回响应体文本 */
    String postForm(String url, Map<String, String> form);

    /** Bearer GET（userinfo 端点），返回响应体文本 */
    String getBearer(String url, String accessToken);

    /**
     * 默认实现（RestClient）
     */
    @Component
    class RestClientOAuth2HttpClient implements OAuth2HttpClient {

        private final RestClient restClient = RestClient.builder().build();

        @Override
        public String postForm(String url, Map<String, String> form) {
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            form.forEach(body::add);
            return restClient.post()
                    .uri(url)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        }

        @Override
        public String getBearer(String url, String accessToken) {
            return restClient.get()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .body(String.class);
        }
    }
}
