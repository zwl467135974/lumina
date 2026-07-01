package io.lumina.common.core;

import lombok.Getter;

/**
 * 统一错误码
 *
 * <p>业务错误码分段规划：
 * <ul>
 *   <li>通用：200/400/401/403/404/409/500（与 HTTP 状态码一致）</li>
 *   <li>认证：1000-1999</li>
 *   <li>用户：10000-10999</li>
 *   <li>角色：11000-11999</li>
 *   <li>权限：12000-12999</li>
 *   <li>租户：13000-13999</li>
 *   <li>Agent：20000-20999</li>
 * </ul>
 *
 * <p>每个错误码包含：{@link #httpStatus}（HTTP 状态码，用于响应 R.code）、
 * {@link #code}（业务错误码，用于前端精确处理，对应 R.errCode）、{@link #message}（默认消息）。
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Getter
public enum ErrorCode {

    // ==================== 通用 ====================
    SUCCESS(200, 200, "操作成功"),
    BAD_REQUEST(400, 400, "请求参数错误"),
    UNAUTHORIZED(401, 401, "未授权"),
    FORBIDDEN(403, 403, "无访问权限"),
    NOT_FOUND(404, 404, "资源不存在"),
    CONFLICT(409, 409, "资源冲突"),
    INTERNAL_ERROR(500, 500, "系统内部错误"),

    // ==================== 认证 1000-1999 ====================
    TOKEN_INVALID(401, 1001, "Token 无效或已过期"),
    TOKEN_MISSING(401, 1002, "未提供有效的 Token"),
    LOGIN_FAILED(400, 1003, "用户名或密码错误"),
    REFRESH_TOKEN_INVALID(401, 1004, "刷新令牌无效"),

    // ==================== 用户 10000-10999 ====================
    USER_NOT_FOUND(404, 10001, "用户不存在"),
    USER_ALREADY_EXISTS(409, 10002, "用户已存在"),
    USERNAME_EXISTS(409, 10003, "用户名已存在"),
    USER_DISABLED(403, 10004, "用户已禁用"),
    PASSWORD_ERROR(400, 10005, "密码错误"),
    PASSWORD_RESET_FAILED(400, 10006, "密码重置失败"),
    USER_IS_ADMIN(403, 10007, "不能操作系统管理员账户"),
    PASSWORD_NOT_MATCH(400, 10008, "两次输入的密码不一致"),

    // ==================== 角色 11000-11999 ====================
    ROLE_NOT_FOUND(404, 11001, "角色不存在"),
    ROLE_ALREADY_EXISTS(409, 11002, "角色已存在"),
    ROLE_IN_USE(409, 11003, "角色正在使用中，无法删除"),
    ROLE_ASSIGNED_FAILED(400, 11004, "角色分配失败"),
    SYSTEM_ROLE_PROTECTED(403, 11005, "不能修改或删除系统角色"),
    ROLE_NOT_IN_TENANT(400, 11006, "角色不属于当前租户"),

    // ==================== 权限 12000-12999 ====================
    PERMISSION_NOT_FOUND(404, 12001, "权限不存在"),
    PERMISSION_ALREADY_EXISTS(409, 12002, "权限已存在"),
    PERMISSION_DENIED(403, 12003, "权限不足"),
    PERMISSION_ASSIGNED_FAILED(400, 12004, "权限分配失败"),

    // ==================== 租户 13000-13999 ====================
    TENANT_NOT_FOUND(404, 13001, "租户不存在"),
    TENANT_ALREADY_EXISTS(409, 13002, "租户已存在"),
    TENANT_DISABLED(403, 13003, "租户已禁用"),
    TENANT_IN_USE(409, 13004, "租户下存在用户，无法删除"),

    // ==================== Agent 20000-20999 ====================
    AGENT_NOT_FOUND(404, 20001, "Agent 不存在"),
    AGENT_NOT_ACTIVE(400, 20002, "Agent 未启用"),
    AGENT_EXECUTE_FAILED(500, 20003, "Agent 执行失败"),
    AGENT_CONFIG_ERROR(500, 20004, "Agent 配置错误"),
    AGENT_TASK_EMPTY(400, 20005, "任务描述不能为空"),

    // ==================== 会话 21000-21999 ====================
    CONVERSATION_NOT_FOUND(404, 21001, "会话不存在"),
    CONVERSATION_AGENT_MISMATCH(400, 21002, "会话与 Agent 不匹配"),

    ;

    /**
     * HTTP 状态码（对应 R.code，用于前端判断成功/失败）
     */
    private final int httpStatus;

    /**
     * 业务错误码（对应 R.errCode，用于前端精确区分业务场景）
     */
    private final int code;

    /**
     * 默认消息
     */
    private final String message;

    ErrorCode(int httpStatus, int code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }
}
