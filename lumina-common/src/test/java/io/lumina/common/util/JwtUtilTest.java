package io.lumina.common.util;

import io.lumina.common.core.LoginUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link JwtUtil} JWT 工具测试
 *
 * <p>通过反射注入 @Value 字段，避免启动 Spring 容器。
 *
 * @author Lumina Team
 * @since 1.0.0
 */
class JwtUtilTest {

    private static final String SECRET = "test-secret-key-must-be-at-least-32-chars-long-for-hs256";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() throws Exception {
        jwtUtil = new JwtUtil();
        setField(jwtUtil, "secretKey", SECRET);
        setField(jwtUtil, "expirationTime", 604800000L);
    }

    @Test
    void generateAndParseTokenShouldRoundTrip() {
        String token = jwtUtil.generateToken("alice", Map.of("tenantId", 100L));

        assertThat(token).isNotBlank();

        var claims = jwtUtil.parseToken(token);
        assertThat(claims.getSubject()).isEqualTo("alice");
        // JWT JSON 反序列化 number 默认为 Integer，用 Number 比较
        assertThat(((Number) claims.get("tenantId")).longValue()).isEqualTo(100L);
    }

    @Test
    void validateTokenShouldReturnTrueForValidToken() {
        String token = jwtUtil.generateToken("bob");

        assertThat(jwtUtil.validateToken(token)).isTrue();
    }

    @Test
    void validateTokenShouldReturnFalseForTamperedToken() {
        String token = jwtUtil.generateToken("bob");
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";

        assertThat(jwtUtil.validateToken(tampered)).isFalse();
    }

    @Test
    void parseTokenShouldThrowForInvalidToken() {
        assertThatThrownBy(() -> jwtUtil.parseToken("invalid.token.here"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void parseTokenToLoginUserShouldExtractAllClaims() {
        String token = jwtUtil.generateToken("alice", Map.of(
                "userId", 1L,
                "tenantId", 100L,
                "roles", new String[]{"TENANT_ADMIN"},
                "permissions", new String[]{"system:user:list"}
        ));

        LoginUser loginUser = jwtUtil.parseTokenToLoginUser(token);

        assertThat(loginUser.getUsername()).isEqualTo("alice");
        assertThat(loginUser.getUserId()).isEqualTo(1L);
        assertThat(loginUser.getTenantId()).isEqualTo(100L);
        assertThat(loginUser.getRoles()).containsExactly("TENANT_ADMIN");
        assertThat(loginUser.getPermissions()).containsExactly("system:user:list");
    }

    @Test
    void getSubjectShouldReturnSubject() {
        String token = jwtUtil.generateToken("charlie");

        assertThat(jwtUtil.getSubject(token)).isEqualTo("charlie");
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
