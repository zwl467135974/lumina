package io.lumina.notification;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * notification 模块集成测试基类
 *
 * <p>使用本地 MySQL（lumina_dev）+ Redis，通过 test profile 配置连接。
 * 测试专用 Flyway 脚本（{@code src/test/resources/db/migration}）仅建
 * {@code lumina_notification} 表，每个测试方法事务回滚保证数据隔离。
 *
 * <p>{@link NotificationTestApplication} 位于 {@code io.lumina.notification} 包，
 * 作为本基类的上层包，{@link SpringBootTest} 会自动发现其作为配置入口。
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {
}
