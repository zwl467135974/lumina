package io.lumina.base;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Lumina Base 模块主启动类
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@SpringBootApplication(scanBasePackages = "io.lumina")
@EnableFeignClients
public class LuminaBaseApplication {

    public static void main(String[] args) {
        SpringApplication.run(LuminaBaseApplication.class, args);
    }
}
