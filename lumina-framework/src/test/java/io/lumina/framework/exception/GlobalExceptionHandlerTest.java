package io.lumina.framework.exception;

import io.lumina.common.core.ErrorCode;
import io.lumina.common.core.R;
import io.lumina.common.exception.BusinessException;
import io.lumina.common.exception.SystemException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * GlobalExceptionHandler 单元测试
 *
 * <p>覆盖全部 16 个异常处理器分支。
 *
 * @author Lumina Team
 * @since 2.0.0
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void businessExceptionReturnsErrorCode() {
        BusinessException ex = new BusinessException(ErrorCode.USER_NOT_FOUND);
        R<Void> result = handler.handleBusinessException(ex);
        assertThat(result.getCode()).isEqualTo(404);
        assertThat(result.getErrCode()).isEqualTo(10001);
        assertThat(result.getMsg()).isEqualTo("用户不存在");
    }

    @Test
    void businessExceptionWithNullErrorCodeUsesRawCode() {
        BusinessException ex = new BusinessException(500, "custom error");
        R<Void> result = handler.handleBusinessException(ex);
        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.getMsg()).isEqualTo("custom error");
    }

    @Test
    void systemExceptionReturnsErrorCode() {
        SystemException ex = new SystemException(ErrorCode.INTERNAL_ERROR);
        R<Void> result = handler.handleSystemException(ex);
        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.getErrCode()).isEqualTo(500);
    }

    @Test
    void methodArgumentNotValidReturnsValidationErrorList() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "dto");
        bindingResult.addError(new FieldError("dto", "name", "不能为空"));
        bindingResult.addError(new FieldError("dto", "email", "格式不正确"));

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(
                mock(MethodParameter.class), bindingResult);

        R<List<GlobalExceptionHandler.ValidationError>> result = handler.handleMethodArgumentNotValidException(ex);

        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getData()).hasSize(2);
        assertThat(result.getData().get(0).field()).isEqualTo("name");
        assertThat(result.getData().get(0).message()).isEqualTo("不能为空");
    }

    @Test
    void bindExceptionReturnsValidationErrorList() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "form");
        bindingResult.addError(new FieldError("form", "age", "必须大于 0"));

        BindException ex = new BindException(bindingResult);

        R<List<GlobalExceptionHandler.ValidationError>> result = handler.handleBindException(ex);

        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0).field()).isEqualTo("age");
    }

    @Test
    void constraintViolationReturnsValidationErrorList() {
        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("name");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("不能为 null");

        Set<ConstraintViolation<?>> violations = new HashSet<>();
        violations.add(violation);
        ConstraintViolationException ex = new ConstraintViolationException(violations);

        R<List<GlobalExceptionHandler.ValidationError>> result = handler.handleConstraintViolationException(ex);

        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0).field()).isEqualTo("name");
        assertThat(result.getData().get(0).message()).isEqualTo("不能为 null");
    }

    @Test
    void missingParamReturns400() {
        MissingServletRequestParameterException ex =
                new MissingServletRequestParameterException("page", "int");
        R<Void> result = handler.handleMissingServletRequestParameterException(ex);
        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMsg()).contains("page");
    }

    @Test
    void typeMismatchReturns400() {
        MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
        when(ex.getName()).thenReturn("id");
        when(ex.getRequiredType()).thenReturn((Class) Long.class);

        R<Void> result = handler.handleMethodArgumentTypeMismatchException(ex);
        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMsg()).isEqualTo("参数类型不匹配");
    }

    @Test
    void httpMessageNotReadableReturns400() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("bad json",
                mock(org.springframework.http.HttpInputMessage.class));
        R<Void> result = handler.handleHttpMessageNotReadableException(ex);
        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMsg()).isEqualTo("请求体格式错误");
    }

    @Test
    void methodNotSupportedReturns405() {
        HttpRequestMethodNotSupportedException ex =
                new HttpRequestMethodNotSupportedException("DELETE");
        R<Void> result = handler.handleHttpRequestMethodNotSupportedException(ex);
        assertThat(result.getCode()).isEqualTo(405);
        assertThat(result.getMsg()).contains("DELETE");
    }

    @Test
    void maxUploadSizeReturns400() {
        MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(1024L);
        R<Void> result = handler.handleMaxUploadSizeExceededException(ex);
        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMsg()).contains("大小限制");
    }

    @Test
    void illegalArgumentReturnsGenericMessage() {
        IllegalArgumentException ex = new IllegalArgumentException("internal detail");
        R<Void> result = handler.handleIllegalArgumentException(ex);
        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMsg()).isEqualTo("请求参数错误");
        assertThat(result.getMsg()).doesNotContain("internal detail");
    }

    @Test
    void nullPointerReturns500() {
        NullPointerException ex = new NullPointerException();
        R<Void> result = handler.handleNullPointerException(ex);
        assertThat(result.getCode()).isEqualTo(500);
    }

    @Test
    void classCastReturns500() {
        ClassCastException ex = new ClassCastException();
        R<Void> result = handler.handleClassCastException(ex);
        assertThat(result.getCode()).isEqualTo(500);
    }

    @Test
    void dataAccessReturns500() {
        DataAccessResourceFailureException ex = new DataAccessResourceFailureException("conn refused");
        R<Void> result = handler.handleDataAccessException(ex);
        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.getMsg()).doesNotContain("conn refused");
    }

    @Test
    void genericExceptionReturns500() {
        Exception ex = new Exception("unexpected");
        R<Void> result = handler.handleException(ex);
        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.getMsg()).isEqualTo("系统异常，请稍后重试");
    }
}
