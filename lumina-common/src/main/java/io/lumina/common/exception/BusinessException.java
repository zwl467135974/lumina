package io.lumina.common.exception;

/**
 * 业务异常
 *
 * <p>业务逻辑异常，如参数错误、数据不存在等。
 *
 * @author Lumina Team
 * @since 1.0.0
 */
public class BusinessException extends BaseException {

    private static final long serialVersionUID = 1L;

    public BusinessException(Integer code, String msg) {
        super(code, msg);
    }

    public BusinessException(String msg) {
        super(400, msg);
    }

    public BusinessException(String msg, Throwable cause) {
        super(400, msg, cause);
    }

    /**
     * 参数错误
     */
    public static BusinessException of(String msg) {
        return new BusinessException(400, msg);
    }

    /**
     * 资源不存在
     */
    public static BusinessException notFound(String msg) {
        return new BusinessException(404, msg);
    }

    /**
     * 资源冲突
     */
    public static BusinessException conflict(String msg) {
        return new BusinessException(409, msg);
    }
}
