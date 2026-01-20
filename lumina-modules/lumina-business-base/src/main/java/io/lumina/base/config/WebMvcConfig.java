package io.lumina.base.config;

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

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册租户隔离拦截器，拦截所有 API 请求
        registry.addInterceptor(tenantIsolationInterceptor)
                .addPathPatterns("/api/**");
    }
}
