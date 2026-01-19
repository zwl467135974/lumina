package io.lumina.common.constant;

/**
 * 通用常量
 *
 * @author Lumina Team
 * @since 1.0.0
 */
public class Constants {

    /**
     * 成功状态码
     */
    public static final Integer SUCCESS_CODE = 200;

    /**
     * 失败状态码
     */
    public static final Integer FAIL_CODE = 500;

    /**
     * 参数错误状态码
     */
    public static final Integer BAD_REQUEST_CODE = 400;

    /**
     * 未授权状态码
     */
    public static final Integer UNAUTHORIZED_CODE = 401;

    /**
     * 无权限状态码
     */
    public static final Integer FORBIDDEN_CODE = 403;

    /**
     * 资源不存在状态码
     */
    public static final Integer NOT_FOUND_CODE = 404;

    /**
     * UTF-8 编码
     */
    public static final String UTF8 = "UTF-8";

    /**
     * GMT+8 时区
     */
    public static final String TIMEZONE_GMT8 = "GMT+8";

    /**
     * 默认分页大小
     */
    public static final Integer DEFAULT_PAGE_SIZE = 10;

    /**
     * 最大分页大小
     */
    public static final Integer MAX_PAGE_SIZE = 100;

    /**
     * 默认页码
     */
    public static final Integer DEFAULT_PAGE_NUM = 1;

    /**
     * 是（逻辑删除）
     */
    public static final Integer DELETED_YES = 1;

    /**
     * 否（逻辑删除）
     */
    public static final Integer DELETED_NO = 0;

    /**
     * 启用状态
     */
    public static final Integer STATUS_ACTIVE = 1;

    /**
     * 禁用状态
     */
    public static final Integer STATUS_INACTIVE = 0;

    private Constants() {
        throw new UnsupportedOperationException("Constant class cannot be instantiated");
    }
}
