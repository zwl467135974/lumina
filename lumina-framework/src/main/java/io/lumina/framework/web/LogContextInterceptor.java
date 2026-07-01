package io.lumina.framework.web;

import io.lumina.common.core.BaseContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * 日志上下文拦截器
 *
 * <p>向 MDC 注入 traceId / tenantId / userId / username，供日志 pattern 输出，
 * 便于按请求/租户/用户检索与关联日志。
 *
 * <p>需在租户隔离拦截器之后执行（order 较大），以读取已设置的 {@link BaseContext}。
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Component
public class LogContextInterceptor implements HandlerInterceptor {

    public static final String TRACE_ID = "traceId";
    public static final String TENANT_ID = "tenantId";
    public static final String USER_ID = "userId";
    public static final String USERNAME = "username";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // traceId：优先从网关透传的 Header 读取，否则生成
        String traceId = request.getHeader("X-Trace-Id");
        if (traceId == null || traceId.isEmpty()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        MDC.put(TRACE_ID, traceId);
        response.setHeader("X-Trace-Id", traceId);

        // 从 BaseContext 读取租户/用户信息（TenantIsolationInterceptor 已设置）
        if (BaseContext.getTenantId() != null) {
            MDC.put(TENANT_ID, String.valueOf(BaseContext.getTenantId()));
        }
        if (BaseContext.getUserId() != null) {
            MDC.put(USER_ID, String.valueOf(BaseContext.getUserId()));
        }
        if (BaseContext.getUsername() != null) {
            MDC.put(USERNAME, BaseContext.getUsername());
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        MDC.clear();
    }
}
