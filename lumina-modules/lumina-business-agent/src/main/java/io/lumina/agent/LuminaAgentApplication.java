package io.lumina.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.mybatis.spring.annotation.MapperScan;

/**
 * Lumina Agent 业务模块启动类
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@SpringBootApplication(scanBasePackages = "io.lumina")
@MapperScan({"io.lumina.agent.infrastructure.mapper", "io.lumina.framework.infrastructure.mapper"})
@EnableDiscoveryClient
@EnableScheduling
public class LuminaAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(LuminaAgentApplication.class, args);
    }
}
