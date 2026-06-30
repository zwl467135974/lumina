package io.lumina.common.exception;

import io.lumina.common.core.ErrorCode;

/**
 * 系统异常
 *
 * <p>系统级异常，如数据库错误、网络错误等。
 *
 * @author Lumina Team
 * @since 1.0.0
 */
public class SystemException extends BaseException {

    private static final long serialVersionUID = 1L;

    public SystemException(Integer code, String msg) {
        super(code, msg);
    }

    public SystemException(String msg) {
        super(500, msg);
    }

    public SystemException(String msg, Throwable cause) {
        super(500, msg, cause);
    }

    public SystemException(Throwable cause) {
        super(500, "系统异常", cause);
    }

    public SystemException(ErrorCode errorCode) {
        super(errorCode);
    }

    public SystemException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public SystemException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    /**
     * 数据库异常
     */
    public static SystemException db(String msg) {
        return new SystemException(500, "数据库异常: " + msg);
    }

    /**
     * Redis 异常
     */
    public static SystemException redis(String msg) {
        return new SystemException(500, "Redis异常: " + msg);
    }

    /**
     * 外部服务异常
     */
    public static SystemException external(String msg) {
        return new SystemException(500, "外部服务异常: " + msg);
    }
}
