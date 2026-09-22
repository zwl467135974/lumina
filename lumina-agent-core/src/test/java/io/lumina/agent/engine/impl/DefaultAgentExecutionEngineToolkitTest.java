package io.lumina.agent.engine.impl;

import io.agentscope.core.tool.Toolkit;
import io.lumina.agent.config.LuminaAgentProperties;
import io.lumina.agent.config.RagProperties;
import io.lumina.agent.loader.ConfigLoader;
import io.lumina.agent.loader.PromptLoader;
import io.lumina.agent.manager.EnhancedToolManager;
import io.lumina.agent.manager.MemoryManager;
import io.lumina.agent.model.AgentConfig;
import io.lumina.agent.model.ChatModelFactory;
import io.lumina.agent.monitor.ToolCircuitBreaker;
import io.lumina.agent.monitor.ToolInvocationRecorder;
import io.lumina.agent.resilience.LlmResilienceWrapper;
import io.lumina.agent.tool.ToolDefinition;
import io.lumina.agent.tool.security.ReadOnlyToolClassifier;
import io.lumina.agent.tool.security.ToolExecutionContext;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Explore 型工具面注册端到端测试（v3.14 批次 3.2）
 *
 * <p>断言 Toolkit 里真的没注册进写工具（不止决策方法返回值）：
 * 白名单含写工具 + 分类器判其不可证明只读 → Explore 型 Toolkit 只含只读工具；
 * 非 Explore 型两者都注册。
 *
 * @author Lumina Team
 * @since 3.14.0
 */
class DefaultAgentExecutionEngineToolkitTest {

    private ReadOnlyToolClassifier classifier;
    private EnhancedToolManager enhancedToolManager;
    private DefaultAgentExecutionEngine engine;

    @BeforeEach
    void setUp() throws Exception {
        classifier = Mockito.mock(ReadOnlyToolClassifier.class);
        enhancedToolManager = Mockito.mock(EnhancedToolManager.class);
        ToolDefinition search = ToolDefinition.create("util.search", "检索", "util", p -> "ok");
        ToolDefinition write = ToolDefinition.create("util.write", "写入", "util", p -> "ok");
        when(enhancedToolManager.getAllTools()).thenReturn(List.of(search, write));
        // 注册期空参数判定：名单证据即可证明，写工具不可证明
        when(classifier.isProvablyReadOnly(any(ToolExecutionContext.class)))
                .thenAnswer(inv -> "util.search".equals(
                        inv.getArgument(0, ToolExecutionContext.class).getToolName()));

        LuminaAgentProperties props = new LuminaAgentProperties();
        engine = new DefaultAgentExecutionEngine(
                Mockito.mock(ConfigLoader.class),
                Mockito.mock(PromptLoader.class),
                Mockito.mock(MemoryManager.class),
                props,
                Mockito.mock(ChatModelFactory.class),
                Mockito.mock(LlmResilienceWrapper.class),
                Mockito.mock(ApplicationContext.class),
                enhancedToolManager,
                Mockito.mock(ToolInvocationRecorder.class),
                Mockito.mock(ToolCircuitBreaker.class),
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry(),
                Mockito.mock(io.agentscope.core.rag.Knowledge.class),
                Mockito.mock(RagProperties.class));
        setField("readOnlyClassifier", classifier);
    }

    @AfterEach
    void tearDown() {
        io.lumina.common.core.BaseContext.clear();
    }

    private void setField(String name, Object value) throws Exception {
        Field field = DefaultAgentExecutionEngine.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(engine, value);
    }

    private Toolkit buildToolkit(String agentType) throws Exception {
        AgentConfig config = new AgentConfig();
        config.setAgentId(null);
        config.setAgentType(agentType);
        AgentConfig.ToolConfig toolConfig = new AgentConfig.ToolConfig();
        toolConfig.setTools(List.of("util.search", "util.write"));
        config.setToolConfig(toolConfig);
        Method method = DefaultAgentExecutionEngine.class.getDeclaredMethod("buildToolkit", AgentConfig.class);
        method.setAccessible(true);
        return (Toolkit) method.invoke(engine, config);
    }

    @Test
    void exploreToolkitExcludesNonReadonlyTool() throws Exception {
        Toolkit toolkit = buildToolkit("Explore");

        assertThat(toolkit.getToolNames()).containsExactly("util.search");
    }

    @Test
    void nonExploreToolkitKeepsWhitelist() throws Exception {
        Toolkit toolkit = buildToolkit("ReAct");

        assertThat(toolkit.getToolNames()).containsExactlyInAnyOrder("util.search", "util.write");
    }

    @Test
    void exploreWithoutClassifierDegradesToWhitelist() throws Exception {
        setField("readOnlyClassifier", null);

        Toolkit toolkit = buildToolkit("Explore");

        assertThat(toolkit.getToolNames()).containsExactlyInAnyOrder("util.search", "util.write");
    }
}
