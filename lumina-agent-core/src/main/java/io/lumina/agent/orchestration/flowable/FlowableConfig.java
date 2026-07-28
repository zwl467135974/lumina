package io.lumina.agent.orchestration.flowable;

import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Flowable 工作流引擎配置
 *
 * <p>仅在 DataSource 可用时激活（即业务模块运行时）。
 * agent-core 单元测试不会触发 Flowable 引擎初始化。
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Configuration
@ConditionalOnBean(DataSource.class)
@org.springframework.boot.autoconfigure.condition.ConditionalOnClass(name = "org.flowable.spring.boot.EngineConfigurationConfigurer")
public class FlowableConfig {

    @Bean
    public EngineConfigurationConfigurer<SpringProcessEngineConfiguration> luminaFlowableConfigurer() {
        return config -> {
            config.setDatabaseSchemaUpdate("true");
            config.setAsyncExecutorActivate(false);
        };
    }
}
