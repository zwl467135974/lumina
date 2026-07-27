package io.lumina.agent.security;

import io.lumina.agent.config.LuminaAgentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DefaultOutputGuardrail 单元测试
 *
 * <p>验证三项护栏逻辑：关键词拦截、长度截断、重复检测。
 * 不依赖 Redis 或 Spring 上下文。
 *
 * @author Lumina Team
 * @since 3.8.0
 */
class DefaultOutputGuardrailTest {

    private DefaultOutputGuardrail guardrail;

    @BeforeEach
    void setUp() {
        LuminaAgentProperties properties = new LuminaAgentProperties();
        LuminaAgentProperties.GuardrailConfig config = properties.getGuardrail();
        config.setEnabled(true);
        config.setMaxOutputLength(100);
        config.setBlockedKeywords(List.of("secret", "password", "api_key"));
        guardrail = new DefaultOutputGuardrail(properties);
    }

    @Test
    void shouldBlockOutputContainingKeyword() {
        OutputGuardrail.GuardrailResult result =
                guardrail.check("The password is 123456", 1L);

        assertTrue(result.blocked());
        assertTrue(result.reason().contains("敏感内容"));
    }

    @Test
    void shouldBlockOutputContainingCaseInsensitiveKeyword() {
        OutputGuardrail.GuardrailResult result =
                guardrail.check("My SECRET value is here", 1L);

        assertTrue(result.blocked());
    }

    @Test
    void shouldPassCleanOutput() {
        OutputGuardrail.GuardrailResult result =
                guardrail.check("Hello, the answer is 42.", 1L);

        assertFalse(result.blocked());
        assertNull(result.rewritten());
    }

    @Test
    void shouldTruncateExcessivelyLongOutput() {
        String longOutput = "A".repeat(200); // 超过 maxOutputLength=100

        OutputGuardrail.GuardrailResult result =
                guardrail.check(longOutput, 1L);

        assertFalse(result.blocked());
        assertNotNull(result.rewritten());
        assertTrue(result.rewritten().contains("截断"));
        assertTrue(result.rewritten().length() < 200);
    }

    @Test
    void shouldBlockSeverelyRepetitiveContent() {
        String repetitive = ("same line\n").repeat(25); // 25 行重复

        OutputGuardrail.GuardrailResult result =
                guardrail.check(repetitive, 1L);

        assertTrue(result.blocked());
        assertTrue(result.reason().contains("重复"));
    }

    @Test
    void shouldPassNullOutput() {
        OutputGuardrail.GuardrailResult result =
                guardrail.check(null, 1L);

        assertFalse(result.blocked());
    }

    @Test
    void shouldPassEmptyOutput() {
        OutputGuardrail.GuardrailResult result =
                guardrail.check("", 1L);

        assertFalse(result.blocked());
    }
}
