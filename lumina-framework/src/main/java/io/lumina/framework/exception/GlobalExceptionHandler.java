package io.lumina.framework.exception;

import io.lumina.common.core.R;
import io.lumina.common.exception.BaseException;
import io.lumina.common.exception.BusinessException;
import io.lumina.common.exception.SystemException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.ArrayList;
import java.util.List;

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
        log.warn("业务异常: {}", e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    /**
     * 系统异常
     */
    @ExceptionHandler(SystemException.class)
    public R<Void> handleSystemException(SystemException e) {
        log.error("系统异常: {}", e.getMessage(), e);
        return R.fail(e.getCode(), e.getMessage());
    }

    /**
     * 基础异常
     */
    @ExceptionHandler(BaseException.class)
    public R<Void> handleBaseException(BaseException e) {
        log.error("异常: {}", e.getMessage(), e);
        return R.fail(e.getCode(), e.getMessage());
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
     * 参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public R<Void> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("参数异常: {}", e.getMessage());
        return R.fail(400, "参数错误: " + e.getMessage());
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
