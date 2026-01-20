package io.lumina.base.interceptor;

import io.lumina.common.core.BaseContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 租户隔离拦截器
 *
 * 从 Gateway 传递的 Header 中提取用户信息，设置到 BaseContext
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Slf4j
@Component
public class TenantIsolationInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 从 Gateway 传递的 Header 中提取用户信息
        String userIdHeader = request.getHeader("X-User-Id");
        String usernameHeader = request.getHeader("X-Username");
        String tenantIdHeader = request.getHeader("X-Tenant-Id");
        String rolesHeader = request.getHeader("X-Roles");
        String permissionsHeader = request.getHeader("X-Permissions");

        // 初始化 BaseContext
        BaseContext.initFromHeaders(userIdHeader, usernameHeader, tenantIdHeader, rolesHeader, permissionsHeader);

        log.debug("租户隔离拦截器: userId={}, username={}, tenantId={}",
                BaseContext.getUserId(), BaseContext.getUsername(), BaseContext.getTenantId());

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 请求完成后清除上下文，避免内存泄漏
        BaseContext.clear();
    }
}
