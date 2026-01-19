package io.lumina.common.core;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应结果
 *
 * <p>封装所有 API 响应结果，统一响应格式。
 *
 * @param <T> 数据类型
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
public class R<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 响应码
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
        return new R<>(200, "操作成功", null);
    }

    /**
     * 成功响应（有数据）
     */
    public static <T> R<T> success(T data) {
        return new R<>(200, "操作成功", data);
    }

    /**
     * 成功响应（自定义消息）
     */
    public static <T> R<T> success(String msg, T data) {
        return new R<>(200, msg, data);
    }

    /**
     * 失败响应
     */
    public static <T> R<T> fail(Integer code, String msg) {
        return new R<>(code, msg, null);
    }

    /**
     * 失败响应（默认 500）
     */
    public static <T> R<T> fail(String msg) {
        return new R<>(500, msg, null);
    }

    /**
     * 判断是否成功
     */
    public boolean isSuccess() {
        return this.code != null && this.code == 200;
    }
}
