package io.lumina.agent.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OutputSanitizer 单元测试
 *
 * @author Lumina Team
 * @since 2.0.0
 */
class OutputSanitizerTest {

    private final OutputSanitizer sanitizer = new OutputSanitizer();

    @Test
    void normalTextUnchanged() {
        String result = sanitizer.sanitize("这是一个正常的回复，没有敏感信息。");
        assertThat(result).isEqualTo("这是一个正常的回复，没有敏感信息。");
    }

    @Test
    void phoneNumberMasked() {
        String result = sanitizer.sanitize("联系电话：13812345678，请回拨。");
        assertThat(result).contains("138****5678");
        assertThat(result).doesNotContain("13812345678");
    }

    @Test
    void idCardMasked() {
        String result = sanitizer.sanitize("身份证号：110101199001011234");
        assertThat(result).contains("110101********1234");
    }

    @Test
    void emailMasked() {
        String result = sanitizer.sanitize("邮箱：testuser@example.com");
        assertThat(result).contains("t***@example.com");
        assertThat(result).doesNotContain("testuser@");
    }

    @Test
    void multiplePiiMasked() {
        String result = sanitizer.sanitize("电话13900001111，邮箱admin@test.org");
        assertThat(result).contains("139****1111");
        assertThat(result).contains("a***@test.org");
    }

    @Test
    void nullInputReturnsNull() {
        assertThat(sanitizer.sanitize(null)).isNull();
    }
}
