package io.lumina.framework.web;

import io.lumina.framework.api.annotation.DeprecatedApi;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * API 废弃拦截器
 *
 * <p>检查目标方法上的 {@link DeprecatedApi} 注解，自动注入 deprecation 响应头，
 * 便于客户端感知接口废弃并迁移。
 *
 * @author Lumina Team
 * @since 1.1.0
 */
@Slf4j
@Component
public class ApiDeprecationInterceptor implements HandlerInterceptor {

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response,
                           Object handler, org.springframework.web.servlet.ModelAndView modelAndView) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return;
        }

        DeprecatedApi dep = handlerMethod.getMethodAnnotation(DeprecatedApi.class);
        if (dep == null) {
            return;
        }

        // Deprecation header（IETF 草案：值可为 true 或版本号）
        response.setHeader("Deprecation", "true");

        // Sunset（RFC 8594：计划移除日期）
        if (!dep.sunset().isEmpty()) {
            response.setHeader("Sunset", dep.sunset());
        }

        // Link（指向新版本端点）
        if (!dep.replacement().isEmpty()) {
            response.setHeader("Link", "<" + dep.replacement() + ">; rel=\"successor-version\"");
        }

        log.debug("废弃 API 调用: {} {}, since={}, replacement={}",
                request.getMethod(), request.getRequestURI(), dep.since(), dep.replacement());
    }
}
