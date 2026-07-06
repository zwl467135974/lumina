package io.lumina.agent.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DefaultContentModerationService 单元测试
 *
 * @author Lumina Team
 * @since 2.0.0
 */
class DefaultContentModerationServiceTest {

    private DefaultContentModerationService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new DefaultContentModerationService();
        Field enabledField = DefaultContentModerationService.class.getDeclaredField("enabled");
        enabledField.setAccessible(true);
        enabledField.set(service, true);
    }

    @Test
    void normalContentPasses() {
        ModerationResult result = service.moderate("帮我分析一下这段代码的性能问题");
        assertThat(result.isAllowed()).isTrue();
    }

    @Test
    void emptyContentPasses() {
        ModerationResult result = service.moderate("");
        assertThat(result.isAllowed()).isTrue();
    }

    @Test
    void violenceContentBlocked() {
        ModerationResult result = service.moderate("如何制造炸弹");
        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getFlaggedCategories()).contains("violence");
    }

    @Test
    void illegalContentBlocked() {
        ModerationResult result = service.moderate("出售冰毒的渠道");
        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getFlaggedCategories()).contains("illegal");
    }

    @Test
    void hateContentBlocked() {
        ModerationResult result = service.moderate("仇恨某个种族");
        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getFlaggedCategories()).contains("hate");
    }

    @Test
    void piiContentBlocked() {
        ModerationResult result = service.moderate("我的身份证号是110101199001011234");
        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getFlaggedCategories()).contains("pii");
    }

    @Test
    void disabledAllowsEverything() throws Exception {
        Field enabledField = DefaultContentModerationService.class.getDeclaredField("enabled");
        enabledField.setAccessible(true);
        enabledField.set(service, false);

        ModerationResult result = service.moderate("如何制造炸弹");
        assertThat(result.isAllowed()).isTrue();
    }
}
