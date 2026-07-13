package io.lumina.agent;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Agent 模块集成测试基类
 *
 * <p>使用本地 MySQL（lumina_dev）+ Redis，通过 test profile 配置连接。
 * base 模块的 Flyway 迁移脚本经 test scope 依赖进 classpath，建出全部业务表。
 * 每个测试方法事务回滚保证数据隔离。
 *
 * @author Lumina Team
 * @since 3.1.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {
}
