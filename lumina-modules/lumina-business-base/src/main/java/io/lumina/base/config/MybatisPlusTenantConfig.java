package io.lumina.base.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.annotation.DbType;
import io.lumina.base.handler.TenantLineHandlerImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class MybatisPlusTenantConfig {

    /**
     * 带租户隔离的 MyBatis-Plus 拦截器
     *
     * <p>bean 名使用 {@code mybatisPlusTenantInterceptor} 以避免与
     * {@code io.lumina.framework.config.MyBatisPlusConfig#mybatisPlusInterceptor}
     * 同名冲突（Spring Boot 默认禁用 bean 定义覆盖）；通过 {@link Primary} 让本拦截器
     * 在按类型注入时优先于框架默认实现。
     */
    @Bean
    @Primary
    public MybatisPlusInterceptor mybatisPlusTenantInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        PaginationInnerInterceptor paginationInnerInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        paginationInnerInterceptor.setMaxLimit(100L);
        paginationInnerInterceptor.setOverflow(false);
        interceptor.addInnerInterceptor(paginationInnerInterceptor);

        TenantLineInnerInterceptor tenantLineInnerInterceptor = new TenantLineInnerInterceptor();
        tenantLineInnerInterceptor.setTenantLineHandler(new TenantLineHandlerImpl());
        interceptor.addInnerInterceptor(tenantLineInnerInterceptor);

        return interceptor;
    }
}
