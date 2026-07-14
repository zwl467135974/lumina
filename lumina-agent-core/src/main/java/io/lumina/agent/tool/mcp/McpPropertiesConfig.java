package io.lumina.agent.tool.mcp;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * MCP 配置注册类
 *
 * <p>激活 {@link McpServerProperties} 的 {@code @ConfigurationProperties} 绑定，
 * 并限定整个 MCP 自动配置仅在 {@code lumina.mcp.enabled=true} 时生效。
 *
 * <p>未启用 MCP 时，本配置类、{@link McpToolRegistrar} 均不会被实例化，
 * {@link McpServerProperties} 的绑定也随之跳过，避免引入不必要的 SDK 依赖开销。
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Configuration
@EnableConfigurationProperties(McpServerProperties.class)
@ConditionalOnProperty(prefix = "lumina.mcp", name = "enabled", havingValue = "true")
public class McpPropertiesConfig {
}
