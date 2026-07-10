package io.lumina.base;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.mybatis.spring.annotation.MapperScan;

/**
 * Lumina Base 模块主启动类
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@SpringBootApplication(scanBasePackages = "io.lumina")
@MapperScan({"io.lumina.base.infrastructure.mapper", "io.lumina.framework.infrastructure.mapper"})
@EnableFeignClients
public class LuminaBaseApplication {

    public static void main(String[] args) {
        SpringApplication.run(LuminaBaseApplication.class, args);
    }
}
