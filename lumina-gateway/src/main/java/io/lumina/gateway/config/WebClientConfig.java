package io.lumina.gateway.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * WebClient 配置
 *
 * <p>提供负载均衡的 WebClient，用于 Gateway 内部调用下游服务
 * （如 API Token 校验调用 lumina-business-base）。
 *
 * @author Lumina Team
 * @since 3.4.0
 */
@Configuration
public class WebClientConfig {

    /**
     * 负载均衡 WebClient.Builder（通过 Nacos 服务名解析）
     */
    @Bean
    @LoadBalanced
    public WebClient.Builder loadBalancedWebClientBuilder() {
        return WebClient.builder();
    }
}
