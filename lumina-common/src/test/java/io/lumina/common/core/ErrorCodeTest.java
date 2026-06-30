package io.lumina.common.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ErrorCode} 错误码测试
 *
 * @author Lumina Team
 * @since 1.0.0
 */
class ErrorCodeTest {

    @Test
    void predefinedErrorCodesShouldHaveConsistentFields() {
        assertThat(ErrorCode.SUCCESS.getHttpStatus()).isEqualTo(200);
        assertThat(ErrorCode.SUCCESS.getCode()).isEqualTo(200);

        assertThat(ErrorCode.USER_NOT_FOUND.getHttpStatus()).isEqualTo(404);
        assertThat(ErrorCode.USER_NOT_FOUND.getCode()).isEqualTo(10001);

        assertThat(ErrorCode.AGENT_NOT_ACTIVE.getHttpStatus()).isEqualTo(400);
        assertThat(ErrorCode.AGENT_NOT_ACTIVE.getCode()).isEqualTo(20002);
    }

    @Test
    void businessCodesShouldNotCollideWithHttpStatusOnSuccess() {
        // 业务错误码与 HTTP 200（成功）区分：所有非 SUCCESS 的错误码 code 应 != 200
        for (ErrorCode ec : ErrorCode.values()) {
            if (ec == ErrorCode.SUCCESS) {
                continue;
            }
            assertThat(ec.getCode())
                    .as("错误码 %s 的 code 不应为 200（与成功冲突）", ec.name())
                    .isNotEqualTo(200);
        }
    }

    @Test
    void messageShouldNotBeBlank() {
        for (ErrorCode ec : ErrorCode.values()) {
            assertThat(ec.getMessage())
                    .as("错误码 %s 的消息不应为空", ec.name())
                    .isNotBlank();
        }
    }

    @Test
    void businessCodesShouldBeUnique() {
        long distinct = java.util.Arrays.stream(ErrorCode.values())
                .map(ErrorCode::getCode)
                .distinct()
                .count();
        assertThat(distinct).isEqualTo(ErrorCode.values().length);
    }
}
