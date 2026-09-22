package io.lumina.agent.tool;

import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.ToolCallParam;
import io.lumina.agent.config.LuminaAgentProperties;
import io.lumina.agent.hook.AgentHookInvoker;
import io.lumina.agent.hook.AgentLifecycleHook;
import io.lumina.agent.hook.HookDecision;
import io.lumina.agent.tool.security.ToolSecurityPipeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 工具适配器的生命周期 Hook 接线测试（v3.14 批次 3.1）
 *
 * <p>覆盖：PreToolUse 拒绝（先于安全管线，工具不执行）、放行透传、
 * PostToolUse 观测模型可见结果、PostToolUseFailure 观测执行异常。
 *
 * @author Lumina Team
 * @since 3.14.0
 */
class ToolDefinitionToAgentToolAdapterHookTest {

    private LuminaAgentProperties props;
    private AtomicInteger executions;
    private ToolDefinition toolDefinition;
    private ToolSecurityPipeline securityPipeline;

    @BeforeEach
    void setUp() {
        props = new LuminaAgentProperties();
        props.getHooks().setEnabled(true);
        executions = new AtomicInteger();
        toolDefinition = ToolDefinition.create("util.echo", "回声工具", "util",
                params -> {
                    executions.incrementAndGet();
                    return "执行结果: " + params;
                });
        securityPipeline = mock(ToolSecurityPipeline.class);
        when(securityPipeline.check(any())).thenReturn(null);
    }

    @SuppressWarnings("unchecked")
    private AgentHookInvoker invoker(AgentLifecycleHook hook) {
        ObjectProvider<AgentLifecycleHook> provider = Mockito.mock(ObjectProvider.class);
        when(provider.orderedStream()).thenReturn(java.util.stream.Stream.of(hook));
        ObjectProvider<io.micrometer.core.instrument.MeterRegistry> meterProvider =
                Mockito.mock(ObjectProvider.class);
        return new AgentHookInvoker(props, provider, meterProvider);
    }

    private Mono<ToolResultBlock> call(AgentHookInvoker hookInvoker) {
        ToolDefinitionToAgentToolAdapter adapter = new ToolDefinitionToAgentToolAdapter(
                toolDefinition, null, null, null, 60000, securityPipeline, null, hookInvoker);
        return adapter.callAsync(ToolCallParam.builder()
                .input(Map.of("text", "hi"))
                .build());
    }

    private String textOf(ToolResultBlock result) {
        return result.getOutput().stream()
                .map(b -> b instanceof io.agentscope.core.message.TextBlock tb ? tb.getText() : String.valueOf(b))
                .collect(java.util.stream.Collectors.joining());
    }

    @Test
    void preToolUseDenyBlocksExecutionBeforeSecurityPipeline() {
        AtomicInteger hookCalls = new AtomicInteger();
        AgentLifecycleHook denyHook = new AgentLifecycleHook() {
            @Override
            public HookDecision onPreToolUse(AgentLifecycleHook.ToolUseInput input) {
                hookCalls.incrementAndGet();
                return HookDecision.deny("外发审计拦截");
            }
        };

        ToolResultBlock result = call(invoker(denyHook)).block();

        assertThat(result).isNotNull();
        assertThat(textOf(result)).contains("生命周期钩子拒绝", "外发审计拦截");
        assertThat(executions.get()).isZero();
        assertThat(hookCalls.get()).isEqualTo(1);
        verify(securityPipeline, never()).check(any());
    }

    @Test
    void allowRunsToolAndNotifiesPostHook() {
        AtomicReference<AgentLifecycleHook.ToolResultInput> observed = new AtomicReference<>();
        AgentLifecycleHook allowHook = new AgentLifecycleHook() {
            @Override
            public void onPostToolUse(AgentLifecycleHook.ToolResultInput input) {
                observed.set(input);
            }
        };

        ToolResultBlock result = call(invoker(allowHook)).block();

        assertThat(result).isNotNull();
        assertThat(textOf(result)).contains("执行结果");
        assertThat(executions.get()).isEqualTo(1);
        verify(securityPipeline).check(any());
        assertThat(observed.get()).isNotNull();
        assertThat(observed.get().resultText()).contains("执行结果");
        assertThat(observed.get().errorMessage()).isNull();
    }

    @Test
    void executionFailureNotifiesPostFailureHook() {
        toolDefinition = ToolDefinition.create("util.boom", "必炸工具", "util",
                params -> {
                    throw new IllegalStateException("连接超时");
                });
        AtomicReference<AgentLifecycleHook.ToolResultInput> observed = new AtomicReference<>();
        AgentLifecycleHook hook = new AgentLifecycleHook() {
            @Override
            public void onPostToolUseFailure(AgentLifecycleHook.ToolResultInput input) {
                observed.set(input);
            }
        };

        ToolResultBlock result = call(invoker(hook)).block();

        assertThat(result).isNotNull();
        assertThat(result.getState()).isEqualTo(io.agentscope.core.message.ToolResultState.ERROR);
        assertThat(observed.get()).isNotNull();
        assertThat(observed.get().errorMessage()).contains("连接超时");
        assertThat(observed.get().resultText()).isNull();
    }

    @Test
    void nullInvokerKeepsLegacyBehavior() {
        ToolResultBlock result = call(null).block();

        assertThat(result).isNotNull();
        assertThat(textOf(result)).contains("执行结果");
        assertThat(executions.get()).isEqualTo(1);
    }
}
