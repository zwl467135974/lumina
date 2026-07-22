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
 *   <li>字典：14000-14999</li>
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
    REQUEST_TIMEOUT(408, 408, "请求超时"),
    CONFLICT(409, 409, "资源冲突"),
    TOO_MANY_REQUESTS(429, 429, "请求过于频繁"),
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
    USER_LOCKED(403, 10009, "账号已被锁定，请稍后重试"),

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

    // ==================== 字典 14000-14999 ====================
    DICT_TYPE_NOT_FOUND(404, 14001, "字典类型不存在"),
    DICT_TYPE_ALREADY_EXISTS(409, 14002, "字典类型已存在"),
    DICT_ITEM_NOT_FOUND(404, 14003, "字典项不存在"),

    // ==================== Agent 20000-20999 ====================
    AGENT_NOT_FOUND(404, 20001, "Agent 不存在"),
    AGENT_NOT_ACTIVE(400, 20002, "Agent 未启用"),
    AGENT_EXECUTE_FAILED(500, 20003, "Agent 执行失败"),
    AGENT_CONFIG_ERROR(500, 20004, "Agent 配置错误"),
    AGENT_TASK_EMPTY(400, 20005, "任务描述不能为空"),
    AGENT_RATE_LIMITED(429, 20006, "Agent 请求过于频繁，请稍后重试"),
    AGENT_CONCURRENT_LIMITED(429, 20010, "Agent 并发数已达上限，请稍后重试"),
    BUDGET_EXCEEDED(403, 20007, "预算已耗尽，执行被拒绝"),
    BUDGET_RULE_NOT_FOUND(404, 20008, "预算规则不存在"),

    // ==================== 会话 21000-21999 ====================
    CONVERSATION_NOT_FOUND(404, 21001, "会话不存在"),
    CONVERSATION_AGENT_MISMATCH(400, 21002, "会话与 Agent 不匹配"),

    // ==================== LLM Provider 22000-22999 ====================
    LLM_PROVIDER_NOT_FOUND(404, 22001, "LLM Provider 不存在"),
    LLM_PROVIDER_ALREADY_EXISTS(409, 22002, "LLM Provider 已存在"),
    LLM_PROVIDER_TEST_FAILED(500, 22003, "LLM Provider 连通性测试失败"),
    LLM_PROVIDER_API_KEY_MISSING(400, 22004, "该 Provider 未配置 API Key"),

    // ==================== 文件 30000-30999 ====================
    FILE_READ_FAILED(400, 30001, "读取文件失败"),
    FILE_NOT_FOUND(404, 30002, "文件不存在或已删除"),
    FILE_UPLOAD_FAILED(500, 30003, "文件上传失败"),
    FILE_DELETE_FAILED(500, 30004, "文件删除失败"),
    FILE_DOWNLOAD_FAILED(500, 30005, "文件下载失败"),
    FILE_STORAGE_INIT_FAILED(500, 30006, "存储初始化失败"),

    // ==================== RAG/知识库 31000-31999 ====================
    RAG_STORE_ERROR(500, 31001, "向量存储操作失败"),
    RAG_EMBEDDING_FAILED(500, 31002, "文本向量化失败"),
    RAG_RETRIEVE_FAILED(500, 31003, "知识检索失败"),

    // ==================== 工作流 32000-32999 ====================
    WORKFLOW_EXECUTE_FAILED(500, 32001, "工作流执行失败"),
    WORKFLOW_PARSE_FAILED(400, 32002, "工作流定义解析失败"),

    // ==================== 搜索 33000-33999 ====================
    SEARCH_FAILED(500, 33001, "网络搜索失败"),

    // ==================== MCP 34000-34999 ====================
    MCP_TOOL_CALL_FAILED(500, 34001, "MCP 工具调用失败"),

    // ==================== 加解密 35000-35999 ====================
    CRYPTO_FAILED(500, 35001, "加解密操作失败"),

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
