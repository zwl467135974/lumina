package io.lumina.agent.loader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.lumina.agent.model.AgentConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

/**
 * Agent 配置加载器
 *
 * <p>从 YAML 文件加载 Agent 配置。
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Slf4j
@Component
public class ConfigLoader {

    private final ObjectMapper yamlMapper;

    public ConfigLoader() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
    }

    /**
     * 加载 Agent 配置
     *
     * @param businessType 业务类型
     * @return Agent 配置
     */
    public AgentConfig loadConfig(String businessType) {
        String configPath = String.format("agent-config/%s.yaml", businessType);
        try {
            ClassPathResource resource = new ClassPathResource(configPath);
            if (!resource.exists()) {
                log.warn("配置文件不存在: {}，使用默认配置", configPath);
                return createDefaultConfig(businessType);
            }
            try (InputStream inputStream = resource.getInputStream()) {
                AgentConfig config = yamlMapper.readValue(inputStream, AgentConfig.class);
                log.info("加载 Agent 配置成功: {}", businessType);
                return config;
            }
        } catch (IOException e) {
            log.error("加载 Agent 配置失败: {}", businessType, e);
            return createDefaultConfig(businessType);
        }
    }

    /**
     * 创建默认配置
     */
    private AgentConfig createDefaultConfig(String businessType) {
        AgentConfig config = new AgentConfig();
        config.setAgentName(businessType);
        config.setAgentType("ReAct");

        AgentConfig.LLMConfig llmConfig = new AgentConfig.LLMConfig();
        llmConfig.setModelType("dashscope");
        llmConfig.setModelName("qwen-max");
        llmConfig.setTemperature(0.7);
        llmConfig.setMaxTokens(2000);
        config.setLlmConfig(llmConfig);

        AgentConfig.ToolConfig toolConfig = new AgentConfig.ToolConfig();
        toolConfig.setEnableAll(true);
        config.setToolConfig(toolConfig);

        AgentConfig.MemoryConfig memoryConfig = new AgentConfig.MemoryConfig();
        memoryConfig.setMemoryType("hash_memory");
        memoryConfig.setMaxMemorySize(100);
        config.setMemoryConfig(memoryConfig);

        return config;
    }
}
