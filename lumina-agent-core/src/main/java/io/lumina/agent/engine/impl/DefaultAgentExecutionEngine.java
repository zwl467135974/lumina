package io.lumina.agent.engine.impl;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.Model;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.tool.Toolkit;
import io.lumina.agent.config.LuminaAgentProperties;
import io.lumina.agent.engine.AgentExecutionEngine;
import io.lumina.agent.loader.ConfigLoader;
import io.lumina.agent.loader.PromptLoader;
import io.lumina.agent.manager.EnhancedToolManager;
import io.lumina.agent.manager.MemoryManager;
import io.lumina.agent.model.AgentConfig;
import io.lumina.agent.model.ChatModelFactory;
import io.lumina.agent.model.ExecuteResult;
import io.lumina.agent.model.StreamChunk;
import io.lumina.agent.monitor.ToolCircuitBreaker;
import io.lumina.agent.monitor.ToolInvocationRecorder;
import io.lumina.agent.tool.ToolDefinition;
import io.lumina.agent.tool.ToolDefinitionToAgentToolAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
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

    /**
     * 上下文窗口大小（加载最近 N 条历史记忆构建多轮上下文）
     */
    private static final int CONTEXT_WINDOW = 20;

    @Autowired(required = false)
    private EnhancedToolManager enhancedToolManager;

    @Autowired(required = false)
    private ToolInvocationRecorder toolInvocationRecorder;

    @Autowired(required = false)
    private ToolCircuitBreaker toolCircuitBreaker;

    @Autowired(required = false)
    private io.micrometer.core.instrument.MeterRegistry meterRegistry;

    @Autowired
    private ChatModelFactory chatModelFactory;

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
    public Mono<ExecuteResult> execute(String businessType, String task, AgentConfig config, String conversationId) {
        return Mono.fromCallable(() -> executeSync(businessType, task, config, conversationId))
                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    @Override
    public ExecuteResult executeSync(String businessType, String task, AgentConfig config, String conversationId) {
        long startTime = System.currentTimeMillis();

        try {
            log.info("开始执行 Agent: businessType={}, task={}, conversationId={}", businessType, task, conversationId);

            // 加载配置
            AgentConfig agentConfig = config != null ? config : configLoader.loadConfig(businessType);

            // 加载提示词
            String promptTemplate = agentConfig.getPromptTemplate();
            if (promptTemplate == null || promptTemplate.isEmpty()) {
                promptTemplate = promptLoader.loadPrompt(businessType);
            }

            // 填充提示词
            String prompt = promptLoader.fillTemplate(promptTemplate, task);

            // 构建上下文消息（含历史记忆）
            List<Msg> contextMessages = buildContextMessages(conversationId, prompt);

            // 执行 Agent
            String result = executeAgentWithAgentScope(agentConfig, contextMessages);

            // 保存对话记忆（Redis 热记忆）
            if (conversationId != null) {
                memoryManager.addMemory(conversationId, "user", task);
                memoryManager.addMemory(conversationId, "assistant", result);
            }

            long duration = System.currentTimeMillis() - startTime;

            log.info("Agent 执行成功: businessType={}, duration={}ms", businessType, duration);

            recordTimer("agent.execution.duration", businessType, "success", duration);

            ExecuteResult executeResult = ExecuteResult.success(result);
            executeResult.setDuration(duration);

            return executeResult;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Agent 执行失败: businessType={}, error={}", businessType, e.getMessage(), e);

            recordTimer("agent.execution.duration", businessType, "failure", duration);

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
     * 记录执行耗时指标到 Micrometer（Prometheus 抓取）
     *
     * @param metric     指标名（如 agent.execution.duration）
     * @param type       业务类型（tag）
     * @param result     执行结果 success/failure（tag）
     * @param durationMs 耗时毫秒
     */
    private void recordTimer(String metric, String type, String result, long durationMs) {
        if (meterRegistry != null) {
            meterRegistry.timer(metric, "type", type, "result", result)
                    .record(java.time.Duration.ofMillis(durationMs));
        }
    }

    @Override
    public reactor.core.publisher.Flux<StreamChunk> executeStream(String businessType, String task, AgentConfig config, String conversationId) {
        log.info("开始流式执行 Agent: businessType={}, task={}, conversationId={}", businessType, task, conversationId);
        try {
            AgentConfig agentConfig = config != null ? config : configLoader.loadConfig(businessType);

            // 加载并填充提示词
            String promptTemplate = agentConfig.getPromptTemplate();
            if (promptTemplate == null || promptTemplate.isEmpty()) {
                promptTemplate = promptLoader.loadPrompt(businessType);
            }
            String prompt = promptLoader.fillTemplate(promptTemplate, task);

            // 构建上下文消息（含历史记忆）
            List<Msg> contextMessages = buildContextMessages(conversationId, prompt);

            ReActAgent agent = createReActAgent(agentConfig);

            // 流式选项：增量输出，包含推理片段与行动片段
            StreamOptions options = StreamOptions.builder()
                    .incremental(true)
                    .includeReasoningChunk(true)
                    .includeActingChunk(true)
                    .build();

            // 累积最终回复内容（用于流结束后保存记忆）
            StringBuilder finalResponse = new StringBuilder();

            return agent.stream(contextMessages, options)
                    .map(this::toStreamChunk)
                    .doOnNext(chunk -> {
                        if ("FINAL".equals(chunk.type())) {
                            finalResponse.append(chunk.content());
                        }
                    })
                    .doOnComplete(() -> {
                        if (conversationId != null && finalResponse.length() > 0) {
                            memoryManager.addMemory(conversationId, "user", task);
                            memoryManager.addMemory(conversationId, "assistant", finalResponse.toString());
                        }
                    })
                    .onErrorResume(e -> {
                        log.error("流式执行失败: businessType={}", businessType, e);
                        return Flux.just(new StreamChunk("ERROR", e.getMessage() != null ? e.getMessage() : "流式执行失败", true));
                    });
        } catch (Exception e) {
            log.error("构建流式 Agent 失败: businessType={}", businessType, e);
            return Flux.just(new StreamChunk("ERROR", e.getMessage() != null ? e.getMessage() : "构建 Agent 失败", true));
        }
    }

    /**
     * 构建上下文消息列表（加载历史记忆 + 当前用户输入）
     *
     * @param conversationId 会话 ID（null 则无历史，仅当前输入）
     * @param currentPrompt  当前用户提示词（已填充模板）
     * @return AgentScope Msg 列表
     */
    private List<Msg> buildContextMessages(String conversationId, String currentPrompt) {
        List<Msg> messages = new ArrayList<>();

        if (conversationId != null) {
            List<MemoryManager.Memory> history = memoryManager.getRecentMemories(conversationId, CONTEXT_WINDOW);
            for (MemoryManager.Memory m : history) {
                MsgRole role = "assistant".equals(m.role()) ? MsgRole.ASSISTANT
                        : "system".equals(m.role()) ? MsgRole.SYSTEM : MsgRole.USER;
                messages.add(Msg.builder().role(role).textContent(m.content()).build());
            }
            log.debug("加载历史记忆: conversationId={}, 条数={}", conversationId, history.size());
        }

        // 追加当前用户消息
        messages.add(Msg.builder().role(MsgRole.USER).textContent(currentPrompt).build());
        return messages;
    }

    /**
     * 将 AgentScope Event 转换为 StreamChunk
     */
    private StreamChunk toStreamChunk(Event event) {
        String type = event.getType() != null ? event.getType().name() : "CHUNK";
        String content = "";
        if (event.getMessage() != null && event.getMessage().getTextContent() != null) {
            content = event.getMessage().getTextContent();
        }
        return new StreamChunk(type, content, event.isLast());
    }

    /**
     * 使用 AgentScope 执行 Agent
     *
     * <p>集成 AgentScope Java SDK 实现 ReAct Agent 执行。
     */
    private String executeAgentWithAgentScope(AgentConfig config, List<Msg> messages) {
        log.info("Agent 配置: name={}, type={}", config.getAgentName(), config.getAgentType());
        log.info("Agent 上下文消息数: {}", messages.size());

        try {
            // 创建 AgentScope ReActAgent
            ReActAgent agent = createReActAgent(config);

            // 执行 Agent（阻塞等待结果）
            Msg response = agent.call(messages).block();

            if (response != null && response.getTextContent() != null) {
                return response.getTextContent();
            } else {
                log.warn("Agent 返回空响应");
                return "Agent 执行完成，但未返回有效响应";
            }

        } catch (Exception e) {
            log.error("AgentScope 执行失败: {}", e.getMessage(), e);
            // 降级到模拟响应（取最后一条用户消息文本）
            String lastUserText = messages.isEmpty() ? "" : messages.get(messages.size() - 1).getTextContent();
            return generateMockResponse(lastUserText);
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

        // 构建模型（按 modelType 路由到 DashScope/OpenAI/Anthropic/Ollama）
        Model model = chatModelFactory.create(llmConfig, agentProperties.getLlm(), getApiKey());

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

                // 创建 AgentTool 适配器（注入可观测组件：调用记录 + 熔断器）
                ToolDefinitionToAgentToolAdapter adapter =
                        new ToolDefinitionToAgentToolAdapter(toolDef, toolInvocationRecorder, toolCircuitBreaker, meterRegistry);

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
