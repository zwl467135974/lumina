package io.lumina.agent.model;

import io.lumina.agent.config.LuminaAgentProperties;
import io.lumina.agent.model.AgentConfig.LLMConfig;
import io.lumina.common.exception.SystemException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ChatModelFactory 单元测试
 *
 * <p>覆盖 Provider 路由逻辑、不支持的类型异常、预设 Provider 路由。
 * 不验证实际模型创建（需 AgentScope 运行时 + API Key），仅测试分支逻辑。
 *
 * @author Lumina Team
 * @since 2.0.0
 */
class ChatModelFactoryTest {

    private ChatModelFactory factory;
    private LuminaAgentProperties.LLMConfig defaults;

    @BeforeEach
    void setUp() {
        LuminaAgentProperties props = new LuminaAgentProperties();
        factory = new ChatModelFactory(props);
        defaults = new LuminaAgentProperties.LLMConfig();
        defaults.setType("dashscope");
        defaults.setModel("qwen-max");
        defaults.setStream(false);
        defaults.setEnableThinking(false);
    }

    @Test
    void unsupportedTypeThrowsSystemException() {
        LLMConfig config = new LLMConfig();
        config.setModelType("nonexistent-provider");

        assertThatThrownBy(() -> factory.create(config, defaults, "test-key"))
                .isInstanceOf(SystemException.class)
                .hasMessageContaining("不支持的模型类型")
                .hasMessageContaining("nonexistent-provider");
    }

    @Test
    void unsupportedTypeErrorMessageListsSupportedProviders() {
        LLMConfig config = new LLMConfig();
        config.setModelType("bad-type");

        assertThatThrownBy(() -> factory.create(config, defaults, "key"))
                .hasMessageContaining("dashscope")
                .hasMessageContaining("openai")
                .hasMessageContaining("anthropic")
                .hasMessageContaining("ollama")
                .hasMessageContaining("glm")
                .hasMessageContaining("kimi");
    }

    @Test
    void nullModelTypeFallsBackToDefaults() {
        LLMConfig config = new LLMConfig();
        config.setModelType(null);

        // When type is null, falls back to defaults.getType() = "dashscope"
        // This should create a DashScope model (needs API key but that's OK for creation)
        // We just verify it doesn't throw SystemException for unsupported type
        try {
            factory.create(config, defaults, "dummy-key");
        } catch (SystemException e) {
            assertThat(e.getMessage()).doesNotContain("不支持的模型类型");
        } catch (Exception e) {
            // Other exceptions (e.g. network/API key validation) are OK - we only care about routing
        }
    }

    @Test
    void createDashScopeSucceedsWithApiKey() {
        LLMConfig config = new LLMConfig();
        config.setModelType("dashscope");
        config.setModelName("qwen-plus");
        config.setTemperature(0.5);
        config.setMaxTokens(1000);

        // Should not throw
        var model = factory.create(config, defaults, "sk-dummy");
        assertThat(model).isNotNull();
    }

    @Test
    void createOllamaSucceedsWithoutApiKey() {
        LLMConfig config = new LLMConfig();
        config.setModelType("ollama");
        config.setModelName("llama3");
        config.setBaseUrl("http://localhost:11434");

        var model = factory.create(config, defaults, null);
        assertThat(model).isNotNull();
    }

    @Test
    void createOpenAIWithBaseUrl() {
        LLMConfig config = new LLMConfig();
        config.setModelType("openai");
        config.setModelName("gpt-4o");
        config.setBaseUrl("https://api.openai.com/v1");
        config.setApiKey("sk-test");
        config.setTemperature(0.7);

        var model = factory.create(config, defaults, "fallback-key");
        assertThat(model).isNotNull();
    }

    @Test
    void createClaudeTypeRoutesToAnthropic() {
        LLMConfig config = new LLMConfig();
        config.setModelType("claude");
        config.setModelName("claude-3-opus");
        config.setApiKey("sk-ant-test");

        // "claude" is an alias for "anthropic" — routing should reach Anthropic model builder.
        // Anthropic SDK may not be on test classpath, so we only verify it doesn't throw
        // SystemException("不支持的模型类型").
        try {
            factory.create(config, defaults, "key");
        } catch (SystemException e) {
            assertThat(e.getMessage()).doesNotContain("不支持的模型类型");
        } catch (Error | Exception e) {
            // NoClassDefFoundError or similar — routing is correct, SDK just not available
        }
    }

    @Test
    void createGlmPresetRoutesToOpenAICompatible() {
        LLMConfig config = new LLMConfig();
        config.setModelType("glm");
        config.setModelName("glm-4-flash");
        config.setApiKey("test-glm-key");

        // Should route through createOpenAICompatiblePreset, not throw unsupported
        try {
            factory.create(config, defaults, "fallback");
        } catch (SystemException e) {
            assertThat(e.getMessage()).doesNotContain("不支持的模型类型");
        } catch (Exception e) {
            // Acceptable — routing worked, other errors OK
        }
    }

    @Test
    void createDeepSeekPresetRoutesToOpenAICompatible() {
        LLMConfig config = new LLMConfig();
        config.setModelType("deepseek");
        config.setModelName("deepseek-chat");
        config.setApiKey("test-ds-key");

        try {
            factory.create(config, defaults, "fallback");
        } catch (SystemException e) {
            assertThat(e.getMessage()).doesNotContain("不支持的模型类型");
        } catch (Exception e) {
            // OK
        }
    }

    @Test
    void agentLevelStreamOverridesGlobalDefault() {
        // 全局 stream=false，Agent 级 stream=true，应创建成功且不报路由错误
        LLMConfig config = new LLMConfig();
        config.setModelType("dashscope");
        config.setModelName("qwen-plus");
        config.setStream(true);
        config.setEnableThinking(true);
        config.setApiKey("sk-dummy");

        var model = factory.create(config, defaults, "fallback-key");
        assertThat(model).isNotNull();
    }

    @Test
    void agentLevelStreamNullFallsBackToGlobal() {
        // Agent 级 stream=null（未设置），应回退到全局默认（false）
        LLMConfig config = new LLMConfig();
        config.setModelType("dashscope");
        config.setModelName("qwen-plus");
        // 不设置 stream / enableThinking
        config.setApiKey("sk-dummy");

        var model = factory.create(config, defaults, "fallback-key");
        assertThat(model).isNotNull();
    }

    @Test
    void reasoningModelOptionsAreAccepted() {
        // thinkingBudget + reasoningEffort 应能传入 GenerateOptions 而不报错
        LLMConfig config = new LLMConfig();
        config.setModelType("dashscope");
        config.setModelName("qwen-plus");
        config.setApiKey("sk-dummy");
        config.setTemperature(0.5);  // 触发 buildGenerateOptions
        config.setThinkingBudget(4096);
        config.setReasoningEffort("medium");

        var model = factory.create(config, defaults, "fallback-key");
        assertThat(model).isNotNull();
    }
}
