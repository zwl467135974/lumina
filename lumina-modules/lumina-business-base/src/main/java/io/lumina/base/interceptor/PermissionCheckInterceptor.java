package io.lumina.base.interceptor;

import io.lumina.base.annotation.RequirePermission;
import io.lumina.base.annotation.RequireRole;
import io.lumina.common.core.BaseContext;
import io.lumina.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;

/**
 * 权限校验拦截器
 *
 * <p>拦截请求，校验用户是否拥有所需的权限或角色。
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Slf4j
@Component
public class PermissionCheckInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 只处理方法处理器
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;

        // 2. 获取方法上的注解
        RequirePermission requirePermission = handlerMethod.getMethodAnnotation(RequirePermission.class);
        RequireRole requireRole = handlerMethod.getMethodAnnotation(RequireRole.class);

        // 3. 如果没有权限或角色注解，直接放行
        if (requirePermission == null && requireRole == null) {
            return true;
        }

        // 4. 校验角色
        if (requireRole != null) {
            checkRole(requireRole);
        }

        // 5. 校验权限
        if (requirePermission != null) {
            checkPermission(requirePermission);
        }

        // 6. 校验通过，放行
        return true;
    }

    /**
     * 校验角色
     */
    private void checkRole(RequireRole requireRole) {
        String[] requiredRoles = requireRole.value();
        if (requiredRoles.length == 0) {
            return;
        }

        String[] userRoles = BaseContext.getRoles();
        if (userRoles == null || userRoles.length == 0) {
            throw new BusinessException("没有角色，无权访问");
        }

        // 检查是否拥有所需角色
        boolean hasRole = requireRole.requireAll() ?
                // 需要拥有所有角色
                Arrays.stream(requiredRoles).allMatch(required -> Arrays.stream(userRoles).anyMatch(userRole -> userRole.equals(required))) :
                // 拥有任一角色即可
                Arrays.stream(requiredRoles).anyMatch(required -> Arrays.stream(userRoles).anyMatch(userRole -> userRole.equals(required)));

        if (!hasRole) {
            throw new BusinessException("角色权限不足，需要角色: " + String.join(", ", requiredRoles));
        }

        log.debug("角色校验通过: {}", Arrays.toString(requiredRoles));
    }

    /**
     * 校验权限
     */
    private void checkPermission(RequirePermission requirePermission) {
        String[] requiredPermissions = requirePermission.value();
        if (requiredPermissions.length == 0) {
            return;
        }

        // 超级管理员拥有所有权限
        if (BaseContext.isSuperAdmin()) {
            log.debug("超级管理员，权限校验通过");
            return;
        }

        String[] userPermissions = BaseContext.getPermissions();
        if (userPermissions == null || userPermissions.length == 0) {
            throw new BusinessException("没有权限，无权访问");
        }

        // 检查是否拥有所需权限
        boolean hasPermission = requirePermission.requireAll() ?
                // 需要拥有所有权限
                Arrays.stream(requiredPermissions).allMatch(required -> Arrays.stream(userPermissions).anyMatch(userPermission -> userPermission.equals(required))) :
                // 拥有任一权限即可
                Arrays.stream(requiredPermissions).anyMatch(required -> Arrays.stream(userPermissions).anyMatch(userPermission -> userPermission.equals(required)));

        if (!hasPermission) {
            throw new BusinessException("权限不足，需要权限: " + String.join(", ", requiredPermissions));
        }

        log.debug("权限校验通过: {}", Arrays.toString(requiredPermissions));
    }
}
