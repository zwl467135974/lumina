package io.lumina.standalone;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Lumina 单体模式启动类
 *
 * <p>将 base + notification + agent 三个业务模块合并为一个进程运行，
 * 仅依赖 MySQL + Redis 两个外部组件：
 * <ul>
 *   <li>Nacos：通过配置禁用（服务发现/配置中心均不需要）</li>
 *   <li>RocketMQ：exclude autoconfiguration，通知走 Spring ApplicationEvent 本地降级</li>
 *   <li>Gateway：由 {@code StandaloneJwtFilter}（WebMVC Filter）承担 JWT 认证</li>
 * </ul>
 *
 * <p>MapperScan 为 base / agent 两个微服务启动类的并集，外加 notification 模块。
 *
 * @author Lumina Team
 * @since 3.4.0
 */
@SpringBootApplication(scanBasePackages = "io.lumina")
@MapperScan({
        "io.lumina.base.infrastructure.mapper",
        "io.lumina.agent.infrastructure.mapper",
        "io.lumina.framework.infrastructure.mapper",
        "io.lumina.notification.infrastructure.mapper"
})
@EnableScheduling
public class LuminaStandaloneApplication {

    public static void main(String[] args) {
        SpringApplication.run(LuminaStandaloneApplication.class, args);
    }
}
