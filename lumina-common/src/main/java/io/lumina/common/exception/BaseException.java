package io.lumina.common.exception;

import lombok.Getter;

/**
 * 基础异常
 *
 * <p>所有业务异常和系统异常的基类。
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Getter
public class BaseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 错误码
     */
    private final Integer code;

    /**
     * 错误消息
     */
    private final String msg;

    public BaseException(Integer code, String msg) {
        super(msg);
        this.code = code;
        this.msg = msg;
    }

    public BaseException(Integer code, String msg, Throwable cause) {
        super(msg, cause);
        this.code = code;
        this.msg = msg;
    }

    public BaseException(String msg) {
        this(500, msg);
    }

    public BaseException(String msg, Throwable cause) {
        this(500, msg, cause);
    }
}
