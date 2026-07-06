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
     * <p>bean 名显式指定为 {@code mybatisPlusInterceptor}，配合
     * {@code spring.main.allow-bean-definition-overriding=true} 覆盖
     * {@code io.lumina.framework.config.MyBatisPlusConfig#mybatisPlusInterceptor}，
     * 避免两个 MybatisPlusInterceptor 同时注册到 MyBatis 拦截器链造成 SQL 被双重重写。
     */
    @Bean(name = "mybatisPlusInterceptor")
    @Primary
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 注意：MyBatis-Plus 要求拦截器顺序为「多租户 → 分页」，租户拦截器必须在分页之前，
        // 否则分页 count 重写时会与租户条件冲突，触发 Parameter index out of range。
        TenantLineInnerInterceptor tenantLineInnerInterceptor = new TenantLineInnerInterceptor();
        tenantLineInnerInterceptor.setTenantLineHandler(new TenantLineHandlerImpl());
        interceptor.addInnerInterceptor(tenantLineInnerInterceptor);

        PaginationInnerInterceptor paginationInnerInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        paginationInnerInterceptor.setMaxLimit(100L);
        paginationInnerInterceptor.setOverflow(false);
        interceptor.addInnerInterceptor(paginationInnerInterceptor);

        return interceptor;
    }
}
