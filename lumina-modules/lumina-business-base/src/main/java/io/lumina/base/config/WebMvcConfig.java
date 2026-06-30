package io.lumina.base.config;

import io.lumina.base.interceptor.PermissionCheckInterceptor;
import io.lumina.base.interceptor.TenantIsolationInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private TenantIsolationInterceptor tenantIsolationInterceptor;

    @Autowired
    private PermissionCheckInterceptor permissionCheckInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. 注册租户隔离拦截器，拦截所有 API 请求
        registry.addInterceptor(tenantIsolationInterceptor)
                .addPathPatterns("/api/**")
                .order(1);  // 租户拦截器优先级更高

        // 2. 注册权限校验拦截器，拦截 Base 模块请求
        registry.addInterceptor(permissionCheckInterceptor)
                .addPathPatterns("/api/v1/base/**")
                .excludePathPatterns(
                    "/api/v1/base/auth/login",      // 排除登录接口
                    "/actuator/**"                   // 排除监控端点
                )
                .order(2);  // 权限拦截器次之
    }
}
