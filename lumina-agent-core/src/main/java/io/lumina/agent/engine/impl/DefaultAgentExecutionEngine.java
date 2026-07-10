package io.lumina.agent.engine.impl;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.message.Base64Source;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.Model;
import io.agentscope.core.rag.Knowledge;
import io.agentscope.core.rag.RAGMode;
import io.agentscope.core.rag.model.RetrieveConfig;
import io.agentscope.core.tool.Toolkit;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.lumina.agent.config.LuminaAgentProperties;
import io.lumina.agent.config.RagProperties;
import io.lumina.agent.engine.AgentExecutionEngine;
import io.lumina.agent.loader.ConfigLoader;
import io.lumina.agent.loader.PromptLoader;
import io.lumina.agent.manager.EnhancedToolManager;
import io.lumina.agent.manager.MemoryManager;
import io.lumina.agent.model.AgentConfig;
import io.lumina.agent.model.ChatModelFactory;
import io.lumina.agent.model.ExecuteResult;
import io.lumina.agent.model.MultimodalImage;
import io.lumina.agent.model.StreamChunk;
import io.lumina.agent.model.StreamEventType;
import io.lumina.agent.monitor.ToolCircuitBreaker;
import io.lumina.agent.monitor.ToolInvocationRecorder;
import io.lumina.agent.resilience.LlmResilienceWrapper;
import io.lumina.agent.tool.ToolDefinition;
import io.lumina.agent.tool.ToolDefinitionToAgentToolAdapter;
import io.lumina.agent.util.JsonUtils;
import io.lumina.common.core.BaseContext;
import io.micrometer.observation.annotation.Observed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

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

    private static final int CONTEXT_WINDOW = 20;

    private static final int MODEL_CACHE_MAX_SIZE = 50;

    private static final int TOOLKIT_CACHE_MAX_SIZE = 100;

    private static final long CACHE_EXPIRE_MINUTES = 30;

    private final Cache<String, Model> modelCache = Caffeine.newBuilder()
            .maximumSize(MODEL_CACHE_MAX_SIZE)
            .expireAfterAccess(CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES)
            .recordStats()
            .build();

    private final Cache<Long, Toolkit> toolkitCache = Caffeine.newBuilder()
            .maximumSize(TOOLKIT_CACHE_MAX_SIZE)
            .expireAfterAccess(CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES)
            .recordStats()
            .build();

    private final ConfigLoader configLoader;
    private final PromptLoader promptLoader;
    private final MemoryManager memoryManager;
    private final LuminaAgentProperties agentProperties;
    private final ChatModelFactory chatModelFactory;
    private final LlmResilienceWrapper llmResilience;
    private final ApplicationContext applicationContext;

    @Nullable
    private final EnhancedToolManager enhancedToolManager;

    @Nullable
    private final ToolInvocationRecorder toolInvocationRecorder;

    @Nullable
    private final ToolCircuitBreaker toolCircuitBreaker;

    @Nullable
    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;

    @Nullable
    private final Knowledge knowledge;

    @Nullable
    private final RagProperties ragProperties;

    public DefaultAgentExecutionEngine(
            ConfigLoader configLoader,
            PromptLoader promptLoader,
            MemoryManager memoryManager,
            LuminaAgentProperties agentProperties,
            ChatModelFactory chatModelFactory,
            LlmResilienceWrapper llmResilience,
            ApplicationContext applicationContext,
            @Nullable EnhancedToolManager enhancedToolManager,
            @Nullable ToolInvocationRecorder toolInvocationRecorder,
            @Nullable ToolCircuitBreaker toolCircuitBreaker,
            @Nullable io.micrometer.core.instrument.MeterRegistry meterRegistry,
            @Nullable Knowledge knowledge,
            @Nullable RagProperties ragProperties) {
        this.configLoader = configLoader;
        this.promptLoader = promptLoader;
        this.memoryManager = memoryManager;
        this.agentProperties = agentProperties;
        this.chatModelFactory = chatModelFactory;
        this.llmResilience = llmResilience;
        this.applicationContext = applicationContext;
        this.enhancedToolManager = enhancedToolManager;
        this.toolInvocationRecorder = toolInvocationRecorder;
        this.toolCircuitBreaker = toolCircuitBreaker;
        this.meterRegistry = meterRegistry;
        this.knowledge = knowledge;
        this.ragProperties = ragProperties;
    }

    @Override
    public Mono<ExecuteResult> execute(String businessType, String task, AgentConfig config, String conversationId) {
        return Mono.fromCallable(() -> executeSync(businessType, task, config, conversationId))
                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    @Override
    @Observed(name = "agent.execute", contextualName = "agent-sync-execution")
    public ExecuteResult executeSync(String businessType, String task, AgentConfig config, String conversationId) {
        return executeSyncInternal(businessType, task, Collections.emptyList(), config, conversationId);
    }

    @Override
    @Observed(name = "agent.execute.multimodal", contextualName = "agent-multimodal-execution")
    public ExecuteResult executeMultimodalSync(String businessType, String task, List<MultimodalImage> images,
                                               AgentConfig config, String conversationId) {
        return executeSyncInternal(businessType, task, images, config, conversationId);
    }

    private ExecuteResult executeSyncInternal(String businessType, String task, List<MultimodalImage> images,
                                             AgentConfig config, String conversationId) {
        long startTime = System.currentTimeMillis();
        BaseContext.setConversationId(conversationId);

        try {
            int imageCount = images != null ? images.size() : 0;
            log.info("开始执行 Agent: businessType={}, task={}, imageCount={}, conversationId={}",
                    businessType, task, imageCount, conversationId);

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
            List<Msg> contextMessages = buildContextMessages(conversationId, prompt, images);

            // 执行 Agent
            Msg agentResponse = executeAgentWithAgentScope(agentConfig, contextMessages);
            String result = agentResponse.getTextContent() != null
                    ? agentResponse.getTextContent()
                    : "Agent 执行完成，但未返回有效响应";

            // 提取 Token 使用量
            ExecuteResult.TokenUsage tokenUsage = extractTokenUsage(agentResponse);

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
            executeResult.setTokenUsage(tokenUsage);

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
    @Observed(name = "agent.execute.stream", contextualName = "agent-stream-execution")
    public reactor.core.publisher.Flux<StreamChunk> executeStream(String businessType, String task, AgentConfig config, String conversationId) {
        log.info("开始流式执行 Agent: businessType={}, task={}, conversationId={}", businessType, task, conversationId);
        BaseContext.setConversationId(conversationId);
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

            Flux<StreamChunk> ragSourcesFlux = buildRagSourcesFlux(task);

            return Flux.concat(ragSourcesFlux, agent.stream(contextMessages, options)
                    .map(this::toStreamChunk)
                    .doOnNext(chunk -> {
                        if (StreamEventType.FINAL.equals(chunk.type())) {
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
                        return Flux.just(new StreamChunk(StreamEventType.ERROR, e.getMessage() != null ? e.getMessage() : "流式执行失败", true));
                    }));
        } catch (Exception e) {
            log.error("构建流式 Agent 失败: businessType={}", businessType, e);
            return Flux.just(new StreamChunk(StreamEventType.ERROR, e.getMessage() != null ? e.getMessage() : "构建 Agent 失败", true));
        }
    }

    @Override
    @Observed(name = "agent.execute.multimodalStream", contextualName = "agent-multimodal-stream")
    public Flux<StreamChunk> executeMultimodalStream(String businessType, String task, List<MultimodalImage> images,
                                                      AgentConfig config, String conversationId) {
        int imageCount = images != null ? images.size() : 0;
        log.info("开始流式多模态执行 Agent: businessType={}, task={}, imageCount={}, conversationId={}",
                businessType, task, imageCount, conversationId);
        BaseContext.setConversationId(conversationId);
        try {
            AgentConfig agentConfig = config != null ? config : configLoader.loadConfig(businessType);

            String promptTemplate = agentConfig.getPromptTemplate();
            if (promptTemplate == null || promptTemplate.isEmpty()) {
                promptTemplate = promptLoader.loadPrompt(businessType);
            }
            String prompt = promptLoader.fillTemplate(promptTemplate, task);

            List<Msg> contextMessages = buildContextMessages(conversationId, prompt, images);

            ReActAgent agent = createReActAgent(agentConfig);

            StreamOptions options = StreamOptions.builder()
                    .incremental(true)
                    .includeReasoningChunk(true)
                    .includeActingChunk(true)
                    .build();

            StringBuilder finalResponse = new StringBuilder();

            Flux<StreamChunk> ragSourcesFlux = buildRagSourcesFlux(task);

            return Flux.concat(ragSourcesFlux, agent.stream(contextMessages, options)
                    .map(this::toStreamChunk)
                    .doOnNext(chunk -> {
                        if (StreamEventType.FINAL.equals(chunk.type())) {
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
                        log.error("流式多模态执行失败: businessType={}", businessType, e);
                        return Flux.just(new StreamChunk(StreamEventType.ERROR,
                                e.getMessage() != null ? e.getMessage() : "流式多模态执行失败", true));
                    }));
        } catch (Exception e) {
            log.error("构建流式多模态 Agent 失败: businessType={}", businessType, e);
            return Flux.just(new StreamChunk(StreamEventType.ERROR,
                    e.getMessage() != null ? e.getMessage() : "构建多模态 Agent 失败", true));
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
        return buildContextMessages(conversationId, currentPrompt, Collections.emptyList());
    }

    /**
     * 构建上下文消息列表（加载历史记忆 + 当前多模态用户输入）
     *
     * @param conversationId 会话 ID（null 则无历史，仅当前输入）
     * @param currentPrompt  当前用户提示词（已填充模板）
     * @param images         当前用户输入携带的图片
     * @return AgentScope Msg 列表
     */
    private List<Msg> buildContextMessages(String conversationId, String currentPrompt, List<MultimodalImage> images) {
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
        if (images == null || images.isEmpty()) {
            messages.add(Msg.builder().role(MsgRole.USER).textContent(currentPrompt).build());
        } else {
            List<ContentBlock> blocks = new ArrayList<>();
            blocks.add(TextBlock.builder().text(currentPrompt).build());
            for (MultimodalImage image : images) {
                blocks.add(ImageBlock.builder()
                        .source(Base64Source.builder()
                                .mediaType(image.getMediaType())
                                .data(image.getData())
                                .build())
                        .build());
            }
            messages.add(Msg.builder().role(MsgRole.USER).content(blocks).build());
        }
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
     *
     * @return AgentScope 响应 Msg（降级时构造模拟 Msg）
     */
    private Msg executeAgentWithAgentScope(AgentConfig config, List<Msg> messages) {
        log.info("Agent 配置: name={}, type={}", config.getAgentName(), config.getAgentType());
        log.info("Agent 上下文消息数: {}", messages.size());

        try {
            ReActAgent agent = createReActAgent(config);

            Msg response = llmResilience.execute("agent-call",
                    () -> agent.call(messages).block());

            if (response != null) {
                return response;
            } else {
                log.error("Agent 返回空响应，LLM 可能未正确配置");
                throw new RuntimeException("LLM 返回空响应，请检查 API Key 和模型配置");
            }

        } catch (Exception e) {
            if (llmResilience.isCircuitBreakerOpen()) {
                log.error("LLM 熔断器已开启: {}", e.getMessage());
                throw new RuntimeException("LLM 服务暂时不可用（熔断器开启），请稍后重试", e);
            }
            log.error("AgentScope 执行失败: {}", e.getMessage(), e);
            throw new RuntimeException("Agent 执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从 AgentScope 响应中提取 Token 使用量
     */
    private ExecuteResult.TokenUsage extractTokenUsage(Msg response) {
        try {
            io.agentscope.core.model.ChatUsage usage = response.getChatUsage();
            if (usage != null) {
                ExecuteResult.TokenUsage tokenUsage = new ExecuteResult.TokenUsage();
                tokenUsage.setPromptTokens(usage.getInputTokens());
                tokenUsage.setCompletionTokens(usage.getOutputTokens());
                tokenUsage.setTotalTokens(usage.getTotalTokens());
                log.debug("Token 使用量: input={}, output={}, total={}",
                        usage.getInputTokens(), usage.getOutputTokens(), usage.getTotalTokens());
                return tokenUsage;
            }
        } catch (Exception e) {
            log.debug("提取 Token 使用量失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 创建 AgentScope ReActAgent
     *
     * <p>ChatModel 和 Toolkit 走 Caffeine 缓存（重量级、无状态），
     * ReActAgent 每次新建（含 InMemoryMemory，有状态）。
     */
    private ReActAgent createReActAgent(AgentConfig config) {
        AgentConfig.LLMConfig llmConfig = resolveLlmConfig(config);

        String modelKey = buildModelCacheKey(llmConfig);
        Model model = modelCache.get(modelKey, k -> {
            log.info("ChatModel 缓存未命中，创建模型: type={}, model={}",
                    llmConfig.getModelType(), llmConfig.getModelName());
            return chatModelFactory.create(llmConfig, agentProperties.getLlm(), getApiKey());
        });

        Toolkit toolkit = resolveToolkit(config);

        ReActAgent.Builder agentBuilder = ReActAgent.builder()
                .name(config.getAgentName() != null ? config.getAgentName() : "LuminaAgent")
                .sysPrompt(config.getPromptTemplate() != null ? config.getPromptTemplate() : "You are a helpful AI assistant.")
                .model(model)
                .toolkit(toolkit)
                .memory(new InMemoryMemory());

        configureRag(agentBuilder);

        return agentBuilder.build();
    }

    @Override
    public void evictCache(Long agentId) {
        if (agentId != null) {
            toolkitCache.invalidate(agentId);
            log.info("已清除 Agent Toolkit 缓存: agentId={}", agentId);
        }
    }

    /**
     * 解析 LLM 配置（优先使用传入配置，否则用全局默认填充）
     */
    private AgentConfig.LLMConfig resolveLlmConfig(AgentConfig config) {
        AgentConfig.LLMConfig llmConfig = config.getLlmConfig();
        if (llmConfig != null) {
            return llmConfig;
        }
        LuminaAgentProperties.LLMConfig defaults = agentProperties.getLlm();
        AgentConfig.LLMConfig resolved = new AgentConfig.LLMConfig();
        resolved.setModelType(defaults.getType());
        resolved.setModelName(defaults.getModel());
        resolved.setApiKey(getApiKey());
        resolved.setBaseUrl(defaults.getBaseUrl());
        resolved.setTemperature(defaults.getTemperature());
        resolved.setMaxTokens(defaults.getMaxTokens());
        resolved.setTopP(defaults.getTopP());
        resolved.setFrequencyPenalty(defaults.getFrequencyPenalty());
        resolved.setPresencePenalty(defaults.getPresencePenalty());
        resolved.setSeed(defaults.getSeed());
        resolved.setTopK(defaults.getTopK());
        return resolved;
    }

    /**
     * 构建 ChatModel 缓存 Key（由所有影响模型创建的配置字段组成，apiKey 走 SHA-256 摘要避免明文）
     */
    private String buildModelCacheKey(AgentConfig.LLMConfig config) {
        return String.join("|",
                str(config.getModelType()),
                str(config.getModelName()),
                hashSensitive(config.getApiKey()),
                str(config.getBaseUrl()),
                str(config.getTemperature()),
                str(config.getMaxTokens()),
                str(config.getTopP()),
                str(config.getFrequencyPenalty()),
                str(config.getPresencePenalty()),
                str(config.getSeed()),
                str(config.getTopK()));
    }

    /**
     * 解析 Toolkit（按 agentId 缓存；agentId 为 null 时不缓存，每次新建）
     */
    private Toolkit resolveToolkit(AgentConfig config) {
        Long agentId = config.getAgentId();
        if (agentId == null) {
            return buildToolkit(config.getToolConfig());
        }
        return toolkitCache.get(agentId, k -> {
            log.info("Toolkit 缓存未命中，构建工具集: agentId={}", agentId);
            return buildToolkit(config.getToolConfig());
        });
    }

    private Toolkit buildToolkit(AgentConfig.ToolConfig toolConfig) {
        Toolkit toolkit = new Toolkit();
        registerToolsToToolkit(toolkit, toolConfig);
        return toolkit;
    }

    private static String str(Object value) {
        return value != null ? value.toString() : "null";
    }

    /**
     * 对敏感字段（如 API Key）做 SHA-256 摘要，截取前 16 位十六进制，避免明文出现在缓存 Key / 日志中
     */
    private static String hashSensitive(String value) {
        if (value == null || value.isEmpty()) {
            return "null";
        }
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    /**
     * 配置 RAG 知识库（当 Knowledge bean 存在且 RAG 启用时注入）
     *
     * <p>GENERIC 模式：每次提问自动检索 Top-K 相似片段注入上下文。
     * <p>AGENTIC 模式：Agent 自行决定是否调用知识检索工具。
     *
     * @param agentBuilder ReActAgent 构建器
     */
    private void configureRag(ReActAgent.Builder agentBuilder) {
        if (knowledge == null) {
            log.debug("RAG 未启用（Knowledge bean 不存在），Agent 不具备知识库检索能力");
            return;
        }

        RAGMode ragMode = parseRagMode(ragProperties != null ? ragProperties.getMode() : "generic");
        int limit = ragProperties != null ? ragProperties.getRetrieve().getLimit() : 3;
        double scoreThreshold = ragProperties != null ? ragProperties.getRetrieve().getScoreThreshold() : 0.3;

        RetrieveConfig retrieveConfig = RetrieveConfig.builder()
                .limit(limit)
                .scoreThreshold(scoreThreshold)
                .build();

        agentBuilder
                .knowledge(knowledge)
                .ragMode(ragMode)
                .retrieveConfig(retrieveConfig);

        log.info("RAG 知识库已注入 Agent: mode={}, limit={}, scoreThreshold={}", ragMode, limit, scoreThreshold);
    }

    /**
     * 构建 RAG 检索来源流（在 Agent 执行前推送命中的知识库片段）
     *
     * <p>使用与 configureRag 相同的检索参数（limit、scoreThreshold），
     * 将命中的文档片段封装为 {@link StreamChunk}（type=RAG_SOURCES）推送给前端。
     * RAG 未启用或检索失败时返回空流，不影响后续 Agent 执行。
     *
     * @param task 用户任务描述
     * @return RAG 来源事件流（0 或 1 个 chunk）
     */
    private Flux<StreamChunk> buildRagSourcesFlux(String task) {
        if (knowledge == null) {
            return Flux.empty();
        }

        int limit = ragProperties != null ? ragProperties.getRetrieve().getLimit() : 3;
        double scoreThreshold = ragProperties != null ? ragProperties.getRetrieve().getScoreThreshold() : 0.3;

        return knowledge.retrieve(task, RetrieveConfig.builder()
                        .limit(limit)
                        .scoreThreshold(scoreThreshold)
                        .build())
                .flatMap(docs -> {
                    if (docs == null || docs.isEmpty()) {
                        return Mono.empty();
                    }
                    List<java.util.Map<String, Object>> sources = new ArrayList<>();
                    for (var doc : docs) {
                        var meta = doc.getMetadata();
                        java.util.Map<String, Object> item = new java.util.LinkedHashMap<>();
                        item.put("content", meta != null ? meta.getContentText() : "");
                        item.put("score", doc.getScore() != null ? doc.getScore() : 0);
                        item.put("docId", meta != null && meta.getDocId() != null ? meta.getDocId() : "");
                        sources.add(item);
                    }
                    try {
                        String json = JsonUtils.OBJECT_MAPPER.writeValueAsString(sources);
                        return Mono.just(new StreamChunk(StreamEventType.RAG_SOURCES, json, false));
                    } catch (Exception e) {
                        log.warn("RAG 来源序列化失败: {}", e.getMessage());
                        return Mono.<StreamChunk>empty();
                    }
                })
                .flux()
                .onErrorResume(e -> {
                    log.warn("RAG 检索来源获取失败: {}", e.getMessage());
                    return Flux.empty();
                });
    }

    /**
     * 解析 RAG 模式字符串为枚举
     *
     * @param mode 模式字符串（generic/agentic/none，大小写不敏感）
     * @return RAGMode 枚举（默认 GENERIC）
     */
    RAGMode parseRagMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return RAGMode.GENERIC;
        }
        try {
            return RAGMode.valueOf(mode.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("未知 RAG 模式: {}，回退到 GENERIC", mode);
            return RAGMode.GENERIC;
        }
    }

    /**
     * 注册工具到 AgentScope Toolkit
     *
     * <p>将 EnhancedToolManager 管理的工具动态适配为 AgentTool 并注册到 Toolkit。
     * 支持从 @AgentTool 注解扫描的工具自动注册。
     */
    private void registerToolsToToolkit(Toolkit toolkit, AgentConfig.ToolConfig toolConfig) {
        if (enhancedToolManager == null) {
            log.debug("EnhancedToolManager 未配置，跳过工具注册");
            return;
        }

        List<ToolDefinition> tools = enhancedToolManager.getAllTools();
        if (tools == null || tools.isEmpty()) {
            log.info("未发现可注册的工具");
            return;
        }

        // 如果 Agent 配置了指定工具列表，只注册这些工具
        java.util.Set<String> allowedTools = null;
        if (toolConfig != null && toolConfig.getTools() != null && !toolConfig.getTools().isEmpty()) {
            allowedTools = new java.util.HashSet<>(toolConfig.getTools());
            log.info("Agent 限定工具: {}", allowedTools);
        } else {
            log.warn("Agent 未配置工具白名单（toolConfig.tools 为空），将注册全部已发现工具。建议显式配置白名单以限制工具暴露面");
        }

        int registeredCount = 0;
        for (ToolDefinition toolDef : tools) {
            try {
                if (!toolDef.isEnabled()) {
                    log.debug("跳过未启用的工具: {}", toolDef.getName());
                    continue;
                }

                // 按 Agent 配置过滤工具
                if (allowedTools != null && !allowedTools.contains(toolDef.getName())) {
                    log.debug("跳过未授权工具: {}（不在 Agent 工具列表中）", toolDef.getName());
                    continue;
                }

                ToolDefinitionToAgentToolAdapter adapter =
                        new ToolDefinitionToAgentToolAdapter(toolDef, toolInvocationRecorder, toolCircuitBreaker, meterRegistry);
                toolkit.registerAgentTool(adapter);
                registeredCount++;
                log.info("工具已注册: {} (分类: {})", toolDef.getName(),
                        toolDef.getCategory() != null ? toolDef.getCategory() : "default");

            } catch (Exception e) {
                log.error("注册工具失败: {}", toolDef.getName(), e);
            }
        }

        log.info("工具注册完成: 共 {} 个工具，成功注册 {} 个", tools.size(), registeredCount);
    }

    /**
     * 获取 API Key（优先从 Spring 配置 lumina.agent.llm.api-key，兼容 DASHSCOPE_API_KEY 环境变量）
     */
    private String getApiKey() {
        String apiKey = agentProperties.getLlm().getApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = System.getenv("DASHSCOPE_API_KEY");
        }
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = System.getenv("LLM_API_KEY");
        }
        if (apiKey == null || apiKey.isEmpty()) {
            log.error("未配置 LLM API Key（lumina.agent.llm.api-key 或 DASHSCOPE_API_KEY），Agent 无法执行");
            throw new IllegalStateException("LLM API Key 未配置，请在 Nacos 或环境变量中设置 lumina.agent.llm.api-key");
        }
        return apiKey;
    }


}
