package io.lumina.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Lumina Agent 业务模块启动类
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@SpringBootApplication(scanBasePackages = "io.lumina")
@EnableDiscoveryClient
public class LuminaAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(LuminaAgentApplication.class, args);
    }
}
