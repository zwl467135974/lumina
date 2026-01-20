package io.lumina.framework.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * SpringDoc OpenAPI 配置
 *
 * @author Lumina Framework
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnProperty(
    prefix = "springdoc.api-docs",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class SpringDocConfig {

    private static final String SECURITY_SCHEME_NAME = "JWT Authentication";

    @Bean
    public OpenAPI openAPI() {
        final String apiTitle = "Lumina AI Agent Platform API";
        final String apiVersion = "v1.0.0";
        final String apiDescription = """
            Lumina AI Agent Platform 是一个企业级 AI Agent 应用开发框架。

            ## 认证方式
            本 API 使用 JWT (JSON Web Token) 进行认证。请在请求头中包含：
            `Authorization: Bearer <your-token>`

            ## 主要功能
            - **Agent 管理**: 创建和管理 AI Agent
            - **用户管理**: 用户账户管理和权限控制
            - **角色管理**: 角色定义和权限分配
            - **权限管理**: 细粒度的权限控制
            - **租户管理**: 多租户支持

            ## 错误码说明
            - 200: 请求成功
            - 201: 创建成功
            - 400: 请求参数错误
            - 401: 未授权（Token 无效或过期）
            - 403: 禁止访问（权限不足）
            - 404: 资源不存在
            - 500: 服务器内部错误
            """;

        return new OpenAPI()
                .info(new Info()
                        .title(apiTitle)
                        .version(apiVersion)
                        .description(apiDescription)
                        .contact(new Contact()
                                .name("Lumina Framework Team")
                                .email("support@lumina.io")
                                .url("https://github.com/lumina-framework/lumina"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("本地开发环境"),
                        new Server().url("https://api.lumina.io").description("生产环境")
                ))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("请输入 JWT Token，格式：Bearer {token}")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
    }
}
