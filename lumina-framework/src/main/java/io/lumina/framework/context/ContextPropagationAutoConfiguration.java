package io.lumina.framework.context;

import io.micrometer.context.ContextRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import reactor.core.publisher.Hooks;

/**
 * 上下文传播自动配置
 *
 * <p>启用 Reactor 全局自动上下文传播（{@link Hooks#enableAutomaticContextPropagation()}），
 * 并注册 {@link LoginContextThreadLocalAccessor}，使 {@link io.lumina.common.core.LoginContext}
 * 在 Reactor 异步线程切换时自动透传。
 *
 * <p>仅在 Reactor 存在的服务（如 agent-service）生效；不含 Reactor 的服务（如 base-service）
 * 不会加载此配置，保持原有同步行为。
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass({ContextRegistry.class, Hooks.class})
public class ContextPropagationAutoConfiguration {

    @PostConstruct
    public void init() {
        ContextRegistry registry = ContextRegistry.getInstance();
        registry.registerThreadLocalAccessor(new LoginContextThreadLocalAccessor());
        Hooks.enableAutomaticContextPropagation();
        log.info("已启用 Reactor 自动上下文传播并注册 LoginContextThreadLocalAccessor");
    }
}
