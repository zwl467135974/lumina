package io.lumina.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Lumina Gateway 启动类
 *
 * <p>Lumina API 网关，作为系统的统一入口。
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@SpringBootApplication
@EnableDiscoveryClient
public class LuminaGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(LuminaGatewayApplication.class, args);
    }
}
