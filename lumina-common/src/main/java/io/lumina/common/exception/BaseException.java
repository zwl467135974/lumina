package io.lumina.common.exception;

import io.lumina.common.core.ErrorCode;
import lombok.Getter;

/**
 * 基础异常
 *
 * <p>所有业务异常和系统异常的基类。支持通过 {@link ErrorCode} 构造以携带标准业务错误码。
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Getter
public class BaseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 错误码（HTTP 状态码）
     */
    private final Integer code;

    /**
     * 错误消息
     */
    private final String msg;

    /**
     * 业务错误码枚举（按 ErrorCode 构造时携带，便于全局异常处理器统一翻译）
     */
    private final ErrorCode errorCode;

    public BaseException(Integer code, String msg) {
        super(msg);
        this.code = code;
        this.msg = msg;
        this.errorCode = null;
    }

    public BaseException(Integer code, String msg, Throwable cause) {
        super(msg, cause);
        this.code = code;
        this.msg = msg;
        this.errorCode = null;
    }

    public BaseException(String msg) {
        this(500, msg);
    }

    public BaseException(String msg, Throwable cause) {
        this(500, msg, cause);
    }

    public BaseException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getHttpStatus();
        this.msg = errorCode.getMessage();
        this.errorCode = errorCode;
    }

    public BaseException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getHttpStatus();
        this.msg = message;
        this.errorCode = errorCode;
    }

    public BaseException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.code = errorCode.getHttpStatus();
        this.msg = errorCode.getMessage();
        this.errorCode = errorCode;
    }

    public BaseException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.code = errorCode.getHttpStatus();
        this.msg = message;
        this.errorCode = errorCode;
    }
}
