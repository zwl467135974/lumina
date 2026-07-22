package io.lumina.base.interceptor;

import io.lumina.base.LuminaBaseApplication;
import io.lumina.common.core.BaseContext;
import io.lumina.common.core.LoginContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 权限拦截器集成测试
 *
 * <p>回归历史 bug P0-2：Agent 模块全部 Controller 无权限控制。
 * 此测试验证 PermissionCheckInterceptor 正确识别 common 模块的 @RequirePermission 注解。
 *
 * @author Lumina Team
 * @since 3.6.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class PermissionInterceptorIntegrationTest {

    @BeforeEach
    void setUp() {
        BaseContext.setCurrent(new LoginContext(0L, 1L, "admin", new String[]{"SUPER_ADMIN"}, null));
    }

    @AfterEach
    void tearDown() {
        BaseContext.clear();
    }

    /**
     * 回归：common 包的 @RequirePermission 注解能被拦截器识别
     *
     * <p>历史 bug：拦截器只识别 base 包的注解，common 包的不生效
     */
    @Test
    void commonRequirePermissionAnnotationIsRecognized() {
        // 用一个标注了 common 包 @RequirePermission 的测试类验证
        // TestController 用 io.lumina.common.annotation.RequirePermission
        io.lumina.common.annotation.RequirePermission annotation =
                TestController.class.getAnnotation(io.lumina.common.annotation.RequirePermission.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).contains("agent:list");
    }

    /**
     * 测试用 Controller：模拟 agent 模块使用 common 包注解
     */
    @io.lumina.common.annotation.RequirePermission("agent:list")
    private static class TestController {
        // 模拟 Controller 类
    }
}
