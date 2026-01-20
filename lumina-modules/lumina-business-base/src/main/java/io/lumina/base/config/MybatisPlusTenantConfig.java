package io.lumina.base.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import io.lumina.base.handler.TenantLineHandlerImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 租户配置
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Configuration
public class MybatisPlusTenantConfig {

    /**
     * 配置 MyBatis-Plus 拦截器，启用租户隔离
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 添加租户拦截器
        TenantLineInnerInterceptor tenantLineInnerInterceptor = new TenantLineInnerInterceptor();
        tenantLineInnerInterceptor.setTenantLineHandler(new TenantLineHandlerImpl());
        interceptor.addInnerInterceptor(tenantLineInnerInterceptor);

        return interceptor;
    }
}
