package io.lumina.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PasswordUtil} 密码工具测试
 *
 * @author Lumina Team
 * @since 1.0.0
 */
class PasswordUtilTest {

    @Test
    void hashAndVerifyShouldRoundTrip() {
        String password = "mySecret123";

        String hashed = PasswordUtil.hash(password);

        assertThat(hashed).isNotEqualTo(password);
        assertThat(PasswordUtil.verify(password, hashed)).isTrue();
    }

    @Test
    void verifyShouldFailWithWrongPassword() {
        String hashed = PasswordUtil.hash("correctPassword");

        assertThat(PasswordUtil.verify("wrongPassword", hashed)).isFalse();
    }

    @Test
    void hashShouldProduceDifferentSaltEachTime() {
        String password = "samePassword";

        String hash1 = PasswordUtil.hash(password);
        String hash2 = PasswordUtil.hash(password);

        // BCrypt 每次使用随机盐，相同密码应产生不同哈希
        assertThat(hash1).isNotEqualTo(hash2);
        // 但两者都能验证通过
        assertThat(PasswordUtil.verify(password, hash1)).isTrue();
        assertThat(PasswordUtil.verify(password, hash2)).isTrue();
    }

    @Test
    void verifyShouldReturnFalseForMalformedHash() {
        assertThat(PasswordUtil.verify("any", "not-a-valid-bcrypt-hash")).isFalse();
    }
}
