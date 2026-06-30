package io.lumina.common.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link R} 统一响应测试
 *
 * @author Lumina Team
 * @since 1.0.0
 */
class RTest {

    @Test
    void successShouldHaveHttp200AndZeroErrCode() {
        R<String> r = R.success("ok");

        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getCode()).isEqualTo(200);
        assertThat(r.getErrCode()).isZero();
        assertThat(r.getData()).isEqualTo("ok");
        assertThat(r.getTimestamp()).isNotNull();
    }

    @Test
    void failByErrorCodeShouldMapHttpStatusAndErrCode() {
        R<Void> r = R.fail(ErrorCode.USER_NOT_FOUND);

        assertThat(r.isSuccess()).isFalse();
        assertThat(r.getCode()).isEqualTo(ErrorCode.USER_NOT_FOUND.getHttpStatus());
        assertThat(r.getErrCode()).isEqualTo(ErrorCode.USER_NOT_FOUND.getCode());
        assertThat(r.getMsg()).isEqualTo(ErrorCode.USER_NOT_FOUND.getMessage());
    }

    @Test
    void failByErrorCodeWithCustomMessageShouldKeepErrCode() {
        R<Void> r = R.fail(ErrorCode.USER_NOT_FOUND, "用户 123 不存在");

        assertThat(r.getCode()).isEqualTo(404);
        assertThat(r.getErrCode()).isEqualTo(ErrorCode.USER_NOT_FOUND.getCode());
        assertThat(r.getMsg()).isEqualTo("用户 123 不存在");
    }

    @Test
    void failWithRawCodeShouldBeBackwardCompatible() {
        R<Void> r = R.fail(409, "冲突");

        assertThat(r.getCode()).isEqualTo(409);
        assertThat(r.getErrCode()).isEqualTo(409);
        assertThat(r.getMsg()).isEqualTo("冲突");
    }

    @Test
    void failWithOnlyMessageDefaultsTo500() {
        R<Void> r = R.fail("内部错误");

        assertThat(r.getCode()).isEqualTo(500);
        assertThat(r.isSuccess()).isFalse();
    }
}
