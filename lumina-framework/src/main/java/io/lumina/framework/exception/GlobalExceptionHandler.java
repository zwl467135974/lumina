package io.lumina.framework.exception;

import io.lumina.common.core.R;
import io.lumina.common.exception.BaseException;
import io.lumina.common.exception.BusinessException;
import io.lumina.common.exception.SystemException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 *
 * <p>统一处理所有异常，返回标准响应格式。
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public R<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: errCode={}, msg={}", e.getErrorCode(), e.getMessage());
        return e.getErrorCode() != null
                ? R.fail(e.getErrorCode(), e.getMessage())
                : R.fail(e.getCode(), e.getMessage());
    }

    /**
     * 系统异常
     */
    @ExceptionHandler(SystemException.class)
    public R<Void> handleSystemException(SystemException e) {
        log.error("系统异常: errCode={}, msg={}", e.getErrorCode(), e.getMessage(), e);
        return e.getErrorCode() != null
                ? R.fail(e.getErrorCode(), e.getMessage())
                : R.fail(e.getCode(), e.getMessage());
    }

    /**
     * 基础异常
     */
    @ExceptionHandler(BaseException.class)
    public R<Void> handleBaseException(BaseException e) {
        log.error("异常: errCode={}, msg={}", e.getErrorCode(), e.getMessage(), e);
        return e.getErrorCode() != null
                ? R.fail(e.getErrorCode(), e.getMessage())
                : R.fail(e.getCode(), e.getMessage());
    }

    /**
     * 参数校验异常（@Valid）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<List<ValidationError>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        List<ValidationError> errors = new ArrayList<>();
        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            errors.add(new ValidationError(fieldError.getField(), fieldError.getDefaultMessage()));
        }
        log.warn("参数校验失败: {}", errors);
        R<List<ValidationError>> result = R.fail(400, "参数校验失败");
        result.setData(errors);
        return result;
    }

    /**
     * 参数绑定异常
     */
    @ExceptionHandler(BindException.class)
    public R<List<ValidationError>> handleBindException(BindException e) {
        List<ValidationError> errors = new ArrayList<>();
        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            errors.add(new ValidationError(fieldError.getField(), fieldError.getDefaultMessage()));
        }
        log.warn("参数绑定失败: {}", errors);
        R<List<ValidationError>> result = R.fail(400, "参数绑定失败");
        result.setData(errors);
        return result;
    }

    /**
     * 约束校验异常（@Validated + @RequestParam/@PathVariable）
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public R<List<ValidationError>> handleConstraintViolationException(ConstraintViolationException e) {
        Set<ConstraintViolation<?>> violations = e.getConstraintViolations();
        List<ValidationError> errors = violations.stream()
                .map(v -> new ValidationError(v.getPropertyPath().toString(), v.getMessage()))
                .collect(Collectors.toList());
        log.warn("约束校验失败: {}", errors);
        R<List<ValidationError>> result = R.fail(400, "参数校验失败");
        result.setData(errors);
        return result;
    }

    /**
     * 缺少必需参数
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public R<Void> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        log.warn("缺少必需参数: {}", e.getParameterName());
        return R.fail(400, "缺少必需参数: " + e.getParameterName());
    }

    /**
     * 参数类型不匹配
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public R<Void> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型不匹配: {} expected {}", e.getName(), e.getRequiredType());
        return R.fail(400, "参数类型不匹配");
    }

    /**
     * 请求体不可读（JSON 格式错误）
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public R<Void> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return R.fail(400, "请求体格式错误");
    }

    /**
     * 请求方法不支持
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public R<Void> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        log.warn("不支持的请求方法: {}", e.getMethod());
        return R.fail(405, "不支持的请求方法: " + e.getMethod());
    }

    /**
     * 上传文件超过大小限制
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public R<Void> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        log.warn("上传文件超过大小限制: {}", e.getMessage());
        return R.fail(400, "上传文件超过大小限制");
    }

    /**
     * 参数异常（不暴露内部异常信息）
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public R<Void> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("参数异常: {}", e.getMessage());
        return R.fail(400, "请求参数错误");
    }

    /**
     * 空指针异常
     */
    @ExceptionHandler(NullPointerException.class)
    public R<Void> handleNullPointerException(NullPointerException e) {
        log.error("空指针异常", e);
        return R.fail(500, "系统内部错误，请联系管理员");
    }

    /**
     * 类型转换异常
     */
    @ExceptionHandler(ClassCastException.class)
    public R<Void> handleClassCastException(ClassCastException e) {
        log.error("类型转换异常: {}", e.getMessage(), e);
        return R.fail(500, "数据类型错误");
    }

    /**
     * 数据库访问异常
     */
    @ExceptionHandler(DataAccessException.class)
    public R<Void> handleDataAccessException(DataAccessException e) {
        log.error("数据库访问异常: {}", e.getMessage(), e);
        return R.fail(500, "数据库操作失败，请稍后重试");
    }

    /**
     * 其他异常
     */
    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e) {
        log.error("未知异常: {}", e.getMessage(), e);
        return R.fail(500, "系统异常，请稍后重试");
    }

    /**
     * 验证错误
     */
    record ValidationError(String field, String message) {
    }
}
