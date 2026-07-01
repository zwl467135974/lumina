package io.lumina.base;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 集成测试基类
 *
 * <p>使用本地 MySQL（lumina_dev）+ Redis，通过 test profile 配置连接。
 * Flyway 已迁移建表，每个测试方法事务回滚保证数据隔离。
 *
 * <p>注：Testcontainers 方案因 Docker Desktop 29.x 与 docker-java 不兼容暂缓，
 * 待 docker-java 适配后可切换回 Testcontainers（代码见 git 历史）。
 *
 * @author Lumina Team
 * @since 1.1.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {
}
