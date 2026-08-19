package io.lumina.agent.config;

import io.lumina.agent.tool.security.ToolApprovalPort;
import io.lumina.agent.tool.security.ToolExecutionInterceptor;
import io.lumina.agent.tool.security.ToolGuard;
import io.lumina.agent.tool.security.ToolSecurityPipeline;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 工具安全管线装配
 *
 * <p>收集所有 {@link ToolExecutionInterceptor} / {@link ToolGuard} Bean 组装管线。
 * {@link ToolApprovalPort} 可选——由业务模块提供（如通知审批），
 * 缺席时 ASK 决策按 fail-closed 拒绝。
 *
 * @author Lumina Team
 * @since 3.11.0
 */
@Configuration
public class ToolSecurityPipelineConfig {

    @Bean
    public ToolSecurityPipeline toolSecurityPipeline(List<ToolExecutionInterceptor> interceptors,
                                                     List<ToolGuard> guards,
                                                     ObjectProvider<ToolApprovalPort> approvalPort) {
        return new ToolSecurityPipeline(interceptors, guards, approvalPort.getIfAvailable());
    }
}
