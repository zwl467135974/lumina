package io.lumina.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * notification 模块集成测试专用启动类
 *
 * <p>notification 模块随 base 服务部署，无独立启动类。为避免与 base 模块形成
 * Maven 循环依赖（base compile→notification，若 notification test→base 则成环），
 * 本测试启动类自包含启动:扫描 {@code io.lumina} 全包加载 framework 基础设施
 * （{@code RedisConfig}、{@code MyBatisPlusConfig} 等），notification mapper 由
 * framework 的 {@code @MapperScan("io.lumina.*.infrastructure.mapper")} 自动覆盖。
 *
 * <p>Flyway 建表脚本置于 {@code src/test/resources/db/migration}，仅建
 * {@code lumina_notification} 一张表，满足集成测试最小数据需求。
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@SpringBootApplication(scanBasePackages = "io.lumina")
public class NotificationTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationTestApplication.class, args);
    }
}
