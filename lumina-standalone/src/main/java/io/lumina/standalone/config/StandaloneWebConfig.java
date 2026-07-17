package io.lumina.standalone.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lumina.base.service.ApiTokenService;
import io.lumina.common.util.JwtUtil;
import io.lumina.standalone.filter.StandaloneJwtFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 单体模式 Web 配置
 *
 * <p>注册 {@link StandaloneJwtFilter} 为最外层 Servlet Filter，替代 Gateway 的 JWT 认证。
 *
 * <p>CORS 由 framework 模块的 WebMvcConfig 统一配置（读取 {@code lumina.cors.allowed-origins}），
 * 拦截器（TenantIsolationInterceptor / PermissionCheckInterceptor）由 base 模块的
 * WebMvcConfig 注册，此处无需重复。
 *
 * @author Lumina Team
 * @since 3.4.0
 */
@Configuration
public class StandaloneWebConfig {

    @Bean
    public FilterRegistrationBean<StandaloneJwtFilter> jwtFilter(JwtUtil jwtUtil,
                                                                 WhitelistConfig whitelistConfig,
                                                                 StringRedisTemplate stringRedisTemplate,
                                                                 ObjectMapper objectMapper,
                                                                 @Autowired(required = false) ApiTokenService apiTokenService) {
        // apiTokenService 可选注入：standalone 不启用 ApiToken 时为 null，filter 自动跳过 sk- 分支
        StandaloneJwtFilter filter = new StandaloneJwtFilter(jwtUtil, whitelistConfig,
                stringRedisTemplate, objectMapper, apiTokenService);
        FilterRegistrationBean<StandaloneJwtFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/*");
        registration.setOrder(1);
        return registration;
    }
}
