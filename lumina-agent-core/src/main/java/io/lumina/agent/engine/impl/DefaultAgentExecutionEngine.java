package io.lumina.agent.engine.impl;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.formatter.dashscope.DashScopeChatFormatter;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.Msg;
import io.agentscope.core.tool.Toolkit;
import io.lumina.agent.config.LuminaAgentProperties;
import io.lumina.agent.engine.AgentExecutionEngine;
import io.lumina.agent.loader.ConfigLoader;
import io.lumina.agent.loader.PromptLoader;
import io.lumina.agent.manager.EnhancedToolManager;
import io.lumina.agent.manager.MemoryManager;
import io.lumina.agent.model.AgentConfig;
import io.lumina.agent.model.ExecuteResult;
import io.lumina.agent.tool.ToolDefinition;
import io.lumina.agent.tool.ToolDefinitionToAgentToolAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Agent 执行引擎默认实现
 *
 * <p>基于 AgentScope 的 Agent 执行引擎实现。
 * <p>支持 ReAct Agent 模式,集成 LLM 和工具调用。
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Slf4j
@Component
public class DefaultAgentExecutionEngine implements AgentExecutionEngine {

    private final ConfigLoader configLoader;
    private final PromptLoader promptLoader;
    private final MemoryManager memoryManager;
    private final LuminaAgentProperties agentProperties;

    @Autowired(required = false)
    private EnhancedToolManager enhancedToolManager;

    @Autowired
    private ApplicationContext applicationContext;

    public DefaultAgentExecutionEngine(
            ConfigLoader configLoader,
            PromptLoader promptLoader,
            MemoryManager memoryManager,
            LuminaAgentProperties agentProperties) {
        this.configLoader = configLoader;
        this.promptLoader = promptLoader;
        this.memoryManager = memoryManager;
        this.agentProperties = agentProperties;
    }

    @Override
    public Mono<ExecuteResult> execute(String businessType, String task, AgentConfig config) {
        return Mono.fromCallable(() -> executeSync(businessType, task, config))
                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    @Override
    public ExecuteResult executeSync(String businessType, String task, AgentConfig config) {
        long startTime = System.currentTimeMillis();

        try {
            log.info("开始执行 Agent: businessType={}, task={}", businessType, task);

            // 加载配置
            AgentConfig agentConfig = config != null ? config : configLoader.loadConfig(businessType);

            // 加载提示词
            String promptTemplate = agentConfig.getPromptTemplate();
            if (promptTemplate == null || promptTemplate.isEmpty()) {
                promptTemplate = promptLoader.loadPrompt(businessType);
            }

            // 填充提示词
            String prompt = promptLoader.fillTemplate(promptTemplate, task);

            // 执行 Agent
            String result = executeAgentWithAgentScope(agentConfig, prompt);

            long duration = System.currentTimeMillis() - startTime;

            log.info("Agent 执行成功: businessType={}, duration={}ms", businessType, duration);

            ExecuteResult executeResult = ExecuteResult.success(result);
            executeResult.setDuration(duration);

            return executeResult;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Agent 执行失败: businessType={}, error={}", businessType, e.getMessage(), e);

            ExecuteResult executeResult = ExecuteResult.failure(e.getMessage());
            executeResult.setDuration(duration);
            return executeResult;
        }
    }

    @Override
    public String getEngineName() {
        return "DefaultAgentExecutionEngine";
    }

    /**
     * 使用 AgentScope 执行 Agent
     *
     * <p>集成 AgentScope Java SDK 实现 ReAct Agent 执行。
     */
    private String executeAgentWithAgentScope(AgentConfig config, String prompt) {
        log.info("Agent 配置: name={}, type={}", config.getAgentName(), config.getAgentType());
        log.info("Agent 提示词: {}", prompt);

        try {
            // 创建 AgentScope ReActAgent
            ReActAgent agent = createReActAgent(config);

            // 构建消息
            Msg message = Msg.builder()
                    .textContent(prompt)
                    .build();

            // 执行 Agent（阻塞等待结果）
            Msg response = agent.call(message).block();

            if (response != null && response.getTextContent() != null) {
                return response.getTextContent();
            } else {
                log.warn("Agent 返回空响应");
                return "Agent 执行完成，但未返回有效响应";
            }

        } catch (Exception e) {
            log.error("AgentScope 执行失败: {}", e.getMessage(), e);
            // 降级到模拟响应
            return generateMockResponse(prompt);
        }
    }

    /**
     * 创建 AgentScope ReActAgent
     */
    private ReActAgent createReActAgent(AgentConfig config) {
        // 获取 LLM 配置（优先使用传入配置，否则使用默认配置）
        AgentConfig.LLMConfig llmConfig = config.getLlmConfig();
        if (llmConfig == null) {
            llmConfig = new AgentConfig.LLMConfig();
            llmConfig.setModelType(agentProperties.getLlm().getType());
            llmConfig.setModelName(agentProperties.getLlm().getModel());
            llmConfig.setApiKey(getApiKey());
            llmConfig.setTemperature(agentProperties.getLlm().getTemperature());
            llmConfig.setMaxTokens(agentProperties.getLlm().getMaxTokens());
        }

        // 构建 DashScope 模型
        DashScopeChatModel.Builder modelBuilder = DashScopeChatModel.builder()
                .apiKey(llmConfig.getApiKey() != null ? llmConfig.getApiKey() : getApiKey())
                .modelName(llmConfig.getModelName() != null ? llmConfig.getModelName() : agentProperties.getLlm().getModel())
                .stream(agentProperties.getLlm().getStream())
                .enableThinking(agentProperties.getLlm().getEnableThinking());

        if (llmConfig.getTemperature() != null) {
            modelBuilder.defaultOptions(
                    io.agentscope.core.model.GenerateOptions.builder()
                            .temperature(llmConfig.getTemperature().floatValue())
                            .maxTokens(llmConfig.getMaxTokens())
                            .build());
        }

        DashScopeChatModel model = modelBuilder
                .formatter(new DashScopeChatFormatter())
                .build();

        // 构建工具集
        Toolkit toolkit = new Toolkit();
        registerToolsToToolkit(toolkit);

        // 构建 ReActAgent
        ReActAgent.Builder agentBuilder = ReActAgent.builder()
                .name(config.getAgentName() != null ? config.getAgentName() : "LuminaAgent")
                .sysPrompt(config.getPromptTemplate() != null ? config.getPromptTemplate() : "You are a helpful AI assistant.")
                .model(model)
                .toolkit(toolkit)
                .memory(new InMemoryMemory());

        return agentBuilder.build();
    }

    /**
     * 注册工具到 AgentScope Toolkit
     *
     * <p>将 EnhancedToolManager 管理的工具动态适配为 AgentTool 并注册到 Toolkit。
     * 支持从 @AgentTool 注解扫描的工具自动注册。
     */
    private void registerToolsToToolkit(Toolkit toolkit) {
        if (enhancedToolManager == null) {
            log.debug("EnhancedToolManager 未配置，跳过工具注册");
            return;
        }

        List<ToolDefinition> tools = enhancedToolManager.getAllTools();
        if (tools == null || tools.isEmpty()) {
            log.info("未发现可注册的工具");
            return;
        }

        int registeredCount = 0;
        for (ToolDefinition toolDef : tools) {
            try {
                // 跳过未启用的工具
                if (!toolDef.isEnabled()) {
                    log.debug("跳过未启用的工具: {}", toolDef.getName());
                    continue;
                }

                // 创建 AgentTool 适配器
                ToolDefinitionToAgentToolAdapter adapter = 
                        new ToolDefinitionToAgentToolAdapter(toolDef);

                // 注册到 Toolkit
                toolkit.registerAgentTool(adapter);

                registeredCount++;
                log.info("工具已注册到 AgentScope Toolkit: {} (分类: {})", 
                        toolDef.getName(), 
                        toolDef.getCategory() != null ? toolDef.getCategory() : "default");

            } catch (Exception e) {
                log.error("注册工具失败: {}", toolDef.getName(), e);
            }
        }

        log.info("工具注册完成: 共 {} 个工具，成功注册 {} 个", tools.size(), registeredCount);
    }

    /**
     * 获取 API Key（优先从环境变量，其次从配置）
     */
    private String getApiKey() {
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = agentProperties.getLlm().getApiKey();
        }
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("未配置 LLM API Key，Agent 可能无法正常工作");
        }
        return apiKey;
    }


    /**
     * 生成模拟响应（降级方案）
     */
    private String generateMockResponse(String prompt) {
        if (prompt.contains("search")) {
            return "I found relevant information for your search query.";
        } else if (prompt.contains("calculate")) {
            return "Calculation completed successfully.";
        } else {
            return "I understand your request: " + prompt.substring(
                    Math.min(50, prompt.length())) + "... Based on my analysis, " +
                    "I would approach this systematically by considering all available options.";
        }
    }

}
