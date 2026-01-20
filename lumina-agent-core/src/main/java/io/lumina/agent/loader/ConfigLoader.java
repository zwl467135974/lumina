package io.lumina.agent.loader;

import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.annotation.NacosConfigListener;
import com.alibaba.nacos.api.exception.NacosException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.lumina.agent.model.AgentConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 配置加载器
 *
 * <p>支持从 Nacos 配置中心或本地 YAML 文件加载 Agent 配置。
 * 优先级：Nacos > ClassPath
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Slf4j
@Component
public class ConfigLoader {

    private final ObjectMapper yamlMapper;
    private final Map<String, AgentConfig> configCache = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private ConfigService nacosConfigService;

    @Value("${lumina.agent.config.nacos.enabled:true}")
    private boolean nacosEnabled;

    @Value("${lumina.agent.config.nacos.group:AGENT_GROUP}")
    private String nacosGroup;

    @Value("${lumina.agent.config.nacos.namespace:}")
    private String nacosNamespace;

    public ConfigLoader() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
    }

    /**
     * 初始化配置加载器
     */
    @PostConstruct
    public void init() {
        if (nacosEnabled && nacosConfigService != null) {
            log.info("ConfigLoader 初始化完成，支持从 Nacos 加载配置");
        } else {
            log.info("ConfigLoader 初始化完成，仅支持从 ClassPath 加载配置");
        }
    }

    /**
     * 加载 Agent 配置
     *
     * <p>优先从 Nacos 加载，如果 Nacos 中不存在则从 ClassPath 加载
     *
     * @param businessType 业务类型
     * @return Agent 配置
     */
    public AgentConfig loadConfig(String businessType) {
        // 先从缓存获取
        if (configCache.containsKey(businessType)) {
            log.debug("从缓存加载 Agent 配置: {}", businessType);
            return configCache.get(businessType);
        }

        // 尝试从 Nacos 加载
        if (nacosEnabled && nacosConfigService != null) {
            AgentConfig nacosConfig = loadConfigFromNacos(businessType);
            if (nacosConfig != null) {
                configCache.put(businessType, nacosConfig);
                return nacosConfig;
            }
        }

        // 从 ClassPath 加载
        AgentConfig classpathConfig = loadConfigFromClasspath(businessType);
        configCache.put(businessType, classpathConfig);
        return classpathConfig;
    }

    /**
     * 从 Nacos 加载配置
     *
     * @param businessType 业务类型
     * @return Agent 配置，如果 Nacos 中不存在返回 null
     */
    private AgentConfig loadConfigFromNacos(String businessType) {
        String dataId = String.format("agent-config-%s.yaml", businessType);

        try {
            String config = nacosConfigService.getConfig(
                dataId,
                nacosGroup,
                5000
            );

            if (config != null && !config.trim().isEmpty()) {
                AgentConfig agentConfig = yamlMapper.readValue(config, AgentConfig.class);
                log.info("从 Nacos 加载 Agent 配置成功: {}", businessType);

                // 验证配置
                validateConfig(agentConfig, businessType);

                return agentConfig;
            } else {
                log.debug("Nacos 中未找到配置: {}，尝试从 ClassPath 加载", dataId);
                return null;
            }
        } catch (NacosException e) {
            log.error("从 Nacos 加载配置失败: {}", dataId, e);
            return null;
        } catch (IOException e) {
            log.error("解析 Nacos 配置失败: {}", dataId, e);
            return null;
        }
    }

    /**
     * 从 ClassPath 加载配置
     *
     * @param businessType 业务类型
     * @return Agent 配置
     */
    private AgentConfig loadConfigFromClasspath(String businessType) {
        String configPath = String.format("agent-config/%s.yaml", businessType);
        try {
            ClassPathResource resource = new ClassPathResource(configPath);
            if (!resource.exists()) {
                log.warn("配置文件不存在: {}，使用默认配置", configPath);
                return createDefaultConfig(businessType);
            }
            try (InputStream inputStream = resource.getInputStream()) {
                AgentConfig config = yamlMapper.readValue(inputStream, AgentConfig.class);
                log.info("从 ClassPath 加载 Agent 配置成功: {}", businessType);

                // 验证配置
                validateConfig(config, businessType);

                return config;
            }
        } catch (IOException e) {
            log.error("加载 Agent 配置失败: {}", businessType, e);
            return createDefaultConfig(businessType);
        }
    }

    /**
     * 监听 Nacos 配置变化并热更新
     *
     * @param dataId 配置文件的 Data ID
     * @param config 新的配置内容
     */
    @NacosConfigListener(dataIds = {
        "agent-config-${businessType}.yaml"
    }, groupId = "${lumina.agent.config.nacos.group:AGENT_GROUP}")
    public void onConfigChanged(String config) {
        try {
            // 从 dataId 中提取 businessType
            String businessType = extractBusinessTypeFromDataId();

            if (businessType != null) {
                AgentConfig newConfig = yamlMapper.readValue(config, AgentConfig.class);

                // 验证新配置
                validateConfig(newConfig, businessType);

                // 更新缓存
                configCache.put(businessType, newConfig);

                log.info("Agent 配置热更新成功: {}", businessType);
            }
        } catch (IOException e) {
            log.error("解析更新的配置失败", e);
        } catch (Exception e) {
            log.error("配置热更新失败", e);
        }
    }

    /**
     * 从 dataId 中提取 businessType
     *
     * @return businessType
     */
    private String extractBusinessTypeFromDataId() {
        // 这里可以实现自定义逻辑，从 dataId 中提取业务类型
        // 例如：agent-config-customer.yaml -> customer
        return null;
    }

    /**
     * 验证配置的有效性
     *
     * @param config Agent 配置
     * @param businessType 业务类型
     */
    private void validateConfig(AgentConfig config, String businessType) {
        if (config == null) {
            throw new IllegalArgumentException("Agent 配置不能为空");
        }

        if (config.getAgentName() == null || config.getAgentName().trim().isEmpty()) {
            config.setAgentName(businessType);
        }

        if (config.getAgentType() == null || config.getAgentType().trim().isEmpty()) {
            config.setAgentType("ReAct");
        }

        if (config.getLlmConfig() == null) {
            log.warn("LLM 配置为空，使用默认配置");
            config.setLlmConfig(createDefaultLLMConfig());
        }

        if (config.getToolConfig() == null) {
            log.debug("Tool 配置为空，使用默认配置");
            config.setToolConfig(createDefaultToolConfig());
        }

        if (config.getMemoryConfig() == null) {
            log.debug("Memory 配置为空，使用默认配置");
            config.setMemoryConfig(createDefaultMemoryConfig());
        }
    }

    /**
     * 创建默认配置
     */
    private AgentConfig createDefaultConfig(String businessType) {
        AgentConfig config = new AgentConfig();
        config.setAgentName(businessType);
        config.setAgentType("ReAct");
        config.setLlmConfig(createDefaultLLMConfig());
        config.setToolConfig(createDefaultToolConfig());
        config.setMemoryConfig(createDefaultMemoryConfig());
        return config;
    }

    /**
     * 创建默认 LLM 配置
     */
    private AgentConfig.LLMConfig createDefaultLLMConfig() {
        AgentConfig.LLMConfig llmConfig = new AgentConfig.LLMConfig();
        llmConfig.setModelType("dashscope");
        llmConfig.setModelName("qwen-max");
        llmConfig.setTemperature(0.7);
        llmConfig.setMaxTokens(2000);
        return llmConfig;
    }

    /**
     * 创建默认 Tool 配置
     */
    private AgentConfig.ToolConfig createDefaultToolConfig() {
        AgentConfig.ToolConfig toolConfig = new AgentConfig.ToolConfig();
        toolConfig.setEnableAll(true);
        return toolConfig;
    }

    /**
     * 创建默认 Memory 配置
     */
    private AgentConfig.MemoryConfig createDefaultMemoryConfig() {
        AgentConfig.MemoryConfig memoryConfig = new AgentConfig.MemoryConfig();
        memoryConfig.setMemoryType("hash_memory");
        memoryConfig.setMaxMemorySize(100);
        return memoryConfig;
    }

    /**
     * 清除配置缓存
     *
     * @param businessType 业务类型，如果为 null 则清除所有缓存
     */
    public void clearCache(String businessType) {
        if (businessType == null) {
            configCache.clear();
            log.info("已清除所有配置缓存");
        } else {
            configCache.remove(businessType);
            log.info("已清除配置缓存: {}", businessType);
        }
    }

    /**
     * 手动刷新配置
     *
     * @param businessType 业务类型
     * @return 新的配置
     */
    public AgentConfig refreshConfig(String businessType) {
        clearCache(businessType);
        return loadConfig(businessType);
    }
}
