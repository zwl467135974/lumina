package io.lumina.common.core;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应结果
 *
 * <p>封装所有 API 响应结果，统一响应格式。
 *
 * <p>字段说明：
 * <ul>
 *   <li>{@link #code}：HTTP 状态码（200 成功，4xx/5xx 失败），前端据此判断 {@link #isSuccess()}</li>
 *   <li>{@link #errCode}：业务错误码（成功为 0，失败为 {@link ErrorCode#getCode()}），前端可据此精确区分业务场景</li>
 *   <li>{@link #msg}、{@link #data}、{@link #timestamp}：消息、数据、时间戳</li>
 * </ul>
 *
 * @param <T> 数据类型
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
public class R<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 响应码（HTTP 状态码）
     */
    private Integer code;

    /**
     * 响应消息
     */
    private String msg;

    /**
     * 响应数据
     */
    private T data;

    /**
     * 业务错误码（成功为 0，失败为 {@link ErrorCode#getCode()}）
     */
    private Integer errCode;

    /**
     * 时间戳
     */
    private Long timestamp;

    public R() {
        this.timestamp = System.currentTimeMillis();
    }

    public R(Integer code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 成功响应（无数据）
     */
    public static <T> R<T> success() {
        return success(null);
    }

    /**
     * 成功响应（有数据）
     */
    public static <T> R<T> success(T data) {
        return success("操作成功", data);
    }

    /**
     * 成功响应（自定义消息）
     */
    public static <T> R<T> success(String msg, T data) {
        R<T> r = new R<>(200, msg, data);
        r.errCode = 0;
        return r;
    }

    /**
     * 失败响应（按错误码）
     */
    public static <T> R<T> fail(ErrorCode errorCode) {
        R<T> r = new R<>(errorCode.getHttpStatus(), errorCode.getMessage(), null);
        r.errCode = errorCode.getCode();
        return r;
    }

    /**
     * 失败响应（按错误码，覆盖默认消息）
     */
    public static <T> R<T> fail(ErrorCode errorCode, String message) {
        R<T> r = new R<>(errorCode.getHttpStatus(), message, null);
        r.errCode = errorCode.getCode();
        return r;
    }

    /**
     * 失败响应（兼容旧用法：直接指定 code/msg）
     */
    public static <T> R<T> fail(Integer code, String msg) {
        R<T> r = new R<>(code, msg, null);
        r.errCode = code;
        return r;
    }

    /**
     * 失败响应（默认 500）
     */
    public static <T> R<T> fail(String msg) {
        return fail(500, msg);
    }

    /**
     * 判断是否成功
     */
    public boolean isSuccess() {
        return this.code != null && this.code == 200;
    }
}
