package io.lumina.gateway.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WhitelistConfig 单元测试
 *
 * <p>覆盖精确匹配、前缀匹配、通配符匹配、空白名单等场景。
 *
 * @author Lumina Team
 * @since 2.0.0
 */
class WhitelistConfigTest {

    private WhitelistConfig config;

    @BeforeEach
    void setUp() {
        config = new WhitelistConfig();
    }

    @Test
    void exactMatchWhitelisted() {
        config.setPaths(Arrays.asList("/api/v1/auth/login", "/actuator/health"));
        assertThat(config.isWhitelisted("/api/v1/auth/login")).isTrue();
        assertThat(config.isWhitelisted("/actuator/health")).isTrue();
    }

    @Test
    void exactMatchNotWhitelisted() {
        config.setPaths(Collections.singletonList("/api/v1/auth/login"));
        assertThat(config.isWhitelisted("/api/v1/auth/register")).isFalse();
        assertThat(config.isWhitelisted("/api/v1/users")).isFalse();
    }

    @Test
    void wildcardMatch() {
        config.setPaths(Arrays.asList("/actuator/**", "/public/**"));
        assertThat(config.isWhitelisted("/actuator/health")).isTrue();
        assertThat(config.isWhitelisted("/actuator/info")).isTrue();
        assertThat(config.isWhitelisted("/actuator/metrics/prometheus")).isTrue();
        assertThat(config.isWhitelisted("/public/images/logo.png")).isTrue();
    }

    @Test
    void wildcardNotMatch() {
        config.setPaths(Collections.singletonList("/actuator/**"));
        assertThat(config.isWhitelisted("/api/v1/agents")).isFalse();
    }

    @Test
    void prefixMatch() {
        config.setPaths(Collections.singletonList("/api/v1/auth"));
        assertThat(config.isWhitelisted("/api/v1/auth/login")).isTrue();
        assertThat(config.isWhitelisted("/api/v1/auth/register")).isTrue();
    }

    @Test
    void emptyWhitelist() {
        config.setPaths(Collections.emptyList());
        assertThat(config.isWhitelisted("/api/v1/agents")).isFalse();
        assertThat(config.isWhitelisted("/any/path")).isFalse();
    }

    @Test
    void defaultPathsIsEmpty() {
        assertThat(config.getPaths()).isEmpty();
        assertThat(config.isWhitelisted("/any")).isFalse();
    }
}
