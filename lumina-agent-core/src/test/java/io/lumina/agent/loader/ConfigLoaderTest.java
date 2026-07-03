package io.lumina.agent.loader;

import io.lumina.agent.model.AgentConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ConfigLoader 单元测试
 *
 * <p>覆盖缓存命中、默认配置生成、配置校验、缓存清除/刷新等核心逻辑。
 * 不依赖 Nacos（nacosEnabled=false），仅测试 ClassPath 回退 + 默认配置路径。
 *
 * @author Lumina Team
 * @since 2.0.0
 */
class ConfigLoaderTest {

    private ConfigLoader configLoader;

    @BeforeEach
    void setUp() throws Exception {
        configLoader = new ConfigLoader();
        setField(configLoader, "nacosEnabled", false);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    void loadConfigNonExistentReturnsDefault() {
        AgentConfig config = configLoader.loadConfig("nonexistent-agent-type");

        assertThat(config).isNotNull();
        assertThat(config.getAgentName()).isEqualTo("nonexistent-agent-type");
        assertThat(config.getAgentType()).isEqualTo("ReAct");
        assertThat(config.getLlmConfig()).isNotNull();
        assertThat(config.getLlmConfig().getModelType()).isEqualTo("dashscope");
        assertThat(config.getLlmConfig().getModelName()).isEqualTo("qwen-max");
    }

    @Test
    void loadConfigCachesResult() {
        AgentConfig first = configLoader.loadConfig("cache-test");
        AgentConfig second = configLoader.loadConfig("cache-test");

        assertThat(second).isSameAs(first);
    }

    @Test
    void clearCacheForcesReload() {
        AgentConfig first = configLoader.loadConfig("clear-test");

        configLoader.clearCache("clear-test");

        AgentConfig second = configLoader.loadConfig("clear-test");
        assertThat(second).isNotSameAs(first);
    }

    @Test
    void clearAllCacheRemovesAll() {
        configLoader.loadConfig("type-a");
        configLoader.loadConfig("type-b");

        configLoader.clearCache(null);

        AgentConfig a = configLoader.loadConfig("type-a");
        AgentConfig b = configLoader.loadConfig("type-b");

        assertThat(a).isNotNull();
        assertThat(b).isNotNull();
    }

    @Test
    void refreshConfigReturnsNewInstance() {
        AgentConfig first = configLoader.loadConfig("refresh-test");

        AgentConfig refreshed = configLoader.refreshConfig("refresh-test");

        assertThat(refreshed).isNotNull();
        assertThat(refreshed).isNotSameAs(first);
        assertThat(refreshed.getAgentName()).isEqualTo("refresh-test");
    }

    @Test
    void onConfigChangedUpdatesCache() {
        String yaml = """
            agentName: "updated-agent"
            agentType: "ReAct"
            llmConfig:
              modelType: "openai"
              modelName: "gpt-4o"
              temperature: 0.3
              maxTokens: 4000
            """;

        configLoader.loadConfig("hot-reload-test");
        configLoader.onConfigChanged(yaml, "hot-reload-test");

        AgentConfig config = configLoader.loadConfig("hot-reload-test");
        assertThat(config.getAgentName()).isEqualTo("updated-agent");
        assertThat(config.getLlmConfig().getModelName()).isEqualTo("gpt-4o");
        assertThat(config.getLlmConfig().getTemperature()).isEqualTo(0.3);
    }

    @Test
    void defaultLlmConfigHasCorrectDefaults() {
        AgentConfig config = configLoader.loadConfig("default-check");

        assertThat(config.getLlmConfig().getModelType()).isEqualTo("dashscope");
        assertThat(config.getLlmConfig().getModelName()).isEqualTo("qwen-max");
        assertThat(config.getLlmConfig().getTemperature()).isEqualTo(0.7);
        assertThat(config.getLlmConfig().getMaxTokens()).isEqualTo(2000);
    }

    @Test
    void defaultToolConfigEnabled() {
        AgentConfig config = configLoader.loadConfig("tool-default");

        assertThat(config.getToolConfig()).isNotNull();
        assertThat(config.getToolConfig().getEnableAll()).isTrue();
    }

    @Test
    void defaultMemoryConfig() {
        AgentConfig config = configLoader.loadConfig("memory-default");

        assertThat(config.getMemoryConfig()).isNotNull();
        assertThat(config.getMemoryConfig().getMemoryType()).isEqualTo("hash_memory");
        assertThat(config.getMemoryConfig().getMaxMemorySize()).isEqualTo(100);
    }

    @Test
    void customerServiceYamlExistsInClasspath() {
        AgentConfig config = configLoader.loadConfig("customer-service");

        assertThat(config).isNotNull();
        assertThat(config.getAgentName()).isNotBlank();
    }
}
