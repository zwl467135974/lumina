package io.lumina.gateway.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * API Token 校验客户端
 *
 * <p>Gateway 不直连数据库，Token 校验通过负载均衡 WebClient 调用
 * lumina-business-base 的内部校验接口（base 侧带 Redis 5 分钟缓存）。
 *
 * @author Lumina Team
 * @since 3.4.0
 */
@Slf4j
@Component
public class ApiTokenValidationClient {

    /**
     * base 服务校验接口地址（lb:// 由 @LoadBalanced WebClient 解析服务名）
     */
    private static final String VALIDATE_URI = "/api/v1/base/api-tokens/validate";

    /**
     * 校验调用超时
     */
    private static final Duration VALIDATE_TIMEOUT = Duration.ofSeconds(3);

    private final WebClient webClient;

    public ApiTokenValidationClient(WebClient.Builder loadBalancedWebClientBuilder,
                                    @Value("${lumina.api-token.base-service:http://lumina-business-base}") String baseServiceUrl) {
        this.webClient = loadBalancedWebClientBuilder.baseUrl(baseServiceUrl).build();
    }

    /**
     * 校验 Token 明文
     *
     * @param cleartext Token 明文（sk- 开头）
     * @return 关联用户信息；无效/过期/调用失败时返回空 Mono（fail-closed）
     */
    public Mono<ApiTokenUser> validate(String cleartext) {
        return webClient.post()
                .uri(VALIDATE_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("token", cleartext))
                .retrieve()
                .bodyToMono(ValidateResponse.class)
                .timeout(VALIDATE_TIMEOUT)
                .flatMap(resp -> {
                    if (resp != null && resp.getData() != null && resp.getData().getUserId() != null) {
                        return Mono.just(resp.getData());
                    }
                    return Mono.empty();
                })
                .onErrorResume(e -> {
                    log.warn("API Token 校验调用失败（fail-closed，按无效处理）: {}", e.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * base 服务统一响应结构（R&lt;ApiTokenUserVO&gt;）的最小映射
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ValidateResponse {
        private Integer code;
        private ApiTokenUser data;
    }
}
