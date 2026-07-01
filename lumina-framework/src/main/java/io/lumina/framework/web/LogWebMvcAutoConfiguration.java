package io.lumina.framework.web;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 日志上下文 WebMvc 自动配置
 *
 * <p>自动注册 {@link LogContextInterceptor}（order=10，在租户/权限拦截器之后），
 * 使所有引入 lumina-framework 的 MVC 服务自动获得 traceId/tenantId/userId 的 MDC 注入。
 *
 * <p>仅对 Servlet（MVC）服务生效；WebFlux 服务（如 gateway）不加载。
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnClass(WebMvcConfigurer.class)
public class LogWebMvcAutoConfiguration implements WebMvcConfigurer {

    private final LogContextInterceptor logContextInterceptor;
    private final ApiDeprecationInterceptor apiDeprecationInterceptor;

    public LogWebMvcAutoConfiguration(LogContextInterceptor logContextInterceptor,
                                      ApiDeprecationInterceptor apiDeprecationInterceptor) {
        this.logContextInterceptor = logContextInterceptor;
        this.apiDeprecationInterceptor = apiDeprecationInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(logContextInterceptor)
                .addPathPatterns("/api/**")
                .order(10);
        // API 废弃响应头注入（order=20，在日志之后）
        registry.addInterceptor(apiDeprecationInterceptor)
                .addPathPatterns("/api/**")
                .order(20);
    }
}
