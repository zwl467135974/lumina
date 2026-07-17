package io.lumina.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.lumina.agent.api.dto.openai.ChatCompletionChunk;
import io.lumina.agent.api.dto.openai.ChatCompletionRequest;
import io.lumina.agent.api.dto.openai.ChatCompletionResponse;
import io.lumina.agent.api.dto.openai.ModelListResponse;
import io.lumina.agent.api.dto.openai.Usage;
import io.lumina.agent.domain.model.Agent;
import io.lumina.agent.infrastructure.entity.AgentDO;
import io.lumina.agent.infrastructure.mapper.AgentMapper;
import io.lumina.agent.model.ExecuteResult;
import io.lumina.agent.model.StreamChunk;
import io.lumina.agent.model.StreamEventType;
import io.lumina.agent.service.AgentService;
import io.lumina.agent.service.OpenAiCompatService;
import io.lumina.common.core.BaseContext;
import io.lumina.common.core.ErrorCode;
import io.lumina.common.core.PageResult;
import io.lumina.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * OpenAI 兼容服务实现
 *
 * <p>model 解析：{@code agent-{id}} 按 ID；否则按 Agent 名称查表（限当前租户）。
 * messages 拼装：system 消息作为任务前缀，多轮历史按角色拼接，复用单 task 入参的
 * {@link AgentService} 管线（每次请求无状态，历史由客户端携带）。
 *
 * @author Lumina Team
 * @since 3.4.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiCompatServiceImpl implements OpenAiCompatService {

    /**
     * model 按 ID 引用的前缀（agent-{id}）
     */
    private static final String MODEL_ID_PREFIX = "agent-";

    /**
     * 响应 ID 前缀
     */
    private static final String COMPLETION_ID_PREFIX = "chatcmpl-";

    /**
     * models 列表最大返回数
     */
    private static final int MODEL_LIST_MAX_SIZE = 100;

    private final AgentService agentService;

    private final AgentMapper agentMapper;

    @Override
    public ChatCompletionResponse execute(ChatCompletionRequest request) {
        Long agentId = resolveAgentId(request.getModel());
        String task = composeTask(request.getMessages());

        // 复用 AgentService 管线（限流/预算/注入检测/审计/脱敏），无会话上下文（历史由客户端携带）
        ExecuteResult result = agentService.executeAgentForResult(agentId, task, null);

        ChatCompletionResponse response = new ChatCompletionResponse();
        response.setId(COMPLETION_ID_PREFIX + UUID.randomUUID());
        response.setCreated(Instant.now().getEpochSecond());
        response.setModel(request.getModel());

        ChatCompletionResponse.ChatMessage message = new ChatCompletionResponse.ChatMessage();
        message.setRole("assistant");
        message.setContent(result.getResult());

        ChatCompletionResponse.Choice choice = new ChatCompletionResponse.Choice();
        choice.setIndex(0);
        choice.setMessage(message);
        choice.setFinishReason("stop");
        response.setChoices(List.of(choice));

        ExecuteResult.TokenUsage tokenUsage = result.getTokenUsage();
        if (tokenUsage != null) {
            response.setUsage(Usage.of(tokenUsage.getPromptTokens(),
                    tokenUsage.getCompletionTokens(), tokenUsage.getTotalTokens()));
        } else {
            response.setUsage(Usage.of(0, 0, 0));
        }
        return response;
    }

    @Override
    public Flux<ChatCompletionChunk> executeStream(ChatCompletionRequest request) {
        Long agentId = resolveAgentId(request.getModel());
        String task = composeTask(request.getMessages());

        String completionId = COMPLETION_ID_PREFIX + UUID.randomUUID();
        long created = Instant.now().getEpochSecond();
        String model = request.getModel();
        AtomicBoolean firstChunk = new AtomicBoolean(true);

        return agentService.executeAgentStream(agentId, task, null)
                .mapNotNull(chunk -> toDeltaChunk(chunk, completionId, created, model, firstChunk))
                .concatWith(Mono.fromCallable(() -> stopChunk(completionId, created, model)));
    }

    @Override
    public ModelListResponse listModels() {
        PageResult<Agent> pageResult = agentService.pageAgents(null, null, 1, MODEL_LIST_MAX_SIZE);

        ModelListResponse response = new ModelListResponse();
        response.setData(pageResult.getList().stream()
                .map(agent -> {
                    ModelListResponse.ModelInfo info = new ModelListResponse.ModelInfo();
                    info.setId(MODEL_ID_PREFIX + agent.getAgentId());
                    info.setCreated(agent.getCreateTime() != null
                            ? agent.getCreateTime().atZone(ZoneId.systemDefault()).toEpochSecond()
                            : Instant.now().getEpochSecond());
                    return info;
                })
                .collect(Collectors.toList()));
        return response;
    }

    /**
     * 解析 model → agentId（agent-{id} 按 ID；否则按名称查当前租户）
     */
    private Long resolveAgentId(String model) {
        if (model.startsWith(MODEL_ID_PREFIX)) {
            try {
                return Long.parseLong(model.substring(MODEL_ID_PREFIX.length()));
            } catch (NumberFormatException e) {
                // agent- 后不是数字，回退按名称查
            }
        }
        Long tenantId = BaseContext.getTenantId() != null ? BaseContext.getTenantId() : 0L;
        AgentDO agentDO = agentMapper.selectOne(new LambdaQueryWrapper<AgentDO>()
                .eq(AgentDO::getAgentName, model)
                .eq(AgentDO::getTenantId, tenantId)
                .last("LIMIT 1"));
        if (agentDO == null) {
            throw new BusinessException(ErrorCode.AGENT_NOT_FOUND, "unknown model: " + model);
        }
        return agentDO.getAgentId();
    }

    /**
     * 拼装 messages → 单 task 字符串
     *
     * <p>system 消息作为前缀（不丢弃）；仅一条 user 消息时直接使用其内容；
     * 多轮历史按“用户/助手”角色逐行拼接，最后一条 user 消息即当前任务。
     */
    private String composeTask(List<ChatCompletionRequest.Message> messages) {
        StringBuilder systemPrefix = new StringBuilder();
        List<ChatCompletionRequest.Message> turns = new java.util.ArrayList<>();

        for (ChatCompletionRequest.Message message : messages) {
            if (message == null || !StringUtils.hasText(message.getContent())) {
                continue;
            }
            if ("system".equalsIgnoreCase(message.getRole())) {
                if (systemPrefix.length() > 0) {
                    systemPrefix.append("\n");
                }
                systemPrefix.append(message.getContent());
            } else {
                turns.add(message);
            }
        }

        if (turns.isEmpty()) {
            throw new BusinessException(ErrorCode.AGENT_TASK_EMPTY);
        }

        StringBuilder task = new StringBuilder();
        if (systemPrefix.length() > 0) {
            task.append("[系统指令]\n").append(systemPrefix).append("\n\n");
        }

        if (turns.size() == 1) {
            task.append(turns.get(0).getContent());
        } else {
            // 多轮历史：客户端每次携带全量历史，拼接为上下文 + 当前任务
            task.append("[对话历史]\n");
            for (int i = 0; i < turns.size() - 1; i++) {
                ChatCompletionRequest.Message turn = turns.get(i);
                String label = "assistant".equalsIgnoreCase(turn.getRole()) ? "助手" : "用户";
                task.append(label).append("：").append(turn.getContent()).append("\n");
            }
            task.append("\n[当前任务]\n").append(turns.get(turns.size() - 1).getContent());
        }
        return task.toString();
    }

    /**
     * StreamChunk → OpenAI delta chunk
     *
     * <p>FINAL/AGENT_RESULT → {@code delta.content}；REASONING 系列 → {@code delta.reasoning_content}；
     * ERROR → 错误文本放入 content；其余类型（ACTING/RAG_SOURCES 等）跳过返回 null。
     */
    private ChatCompletionChunk toDeltaChunk(StreamChunk chunk, String completionId, long created,
                                             String model, AtomicBoolean firstChunk) {
        String type = chunk.type();
        ChatCompletionChunk.Delta delta = new ChatCompletionChunk.Delta();

        if (StreamEventType.FINAL.equals(type) || StreamEventType.AGENT_RESULT.equals(type)) {
            delta.setContent(chunk.content());
        } else if (StreamEventType.REASONING_CHUNK.equals(type) || StreamEventType.REASONING.equals(type)) {
            delta.setReasoningContent(chunk.content());
        } else if (StreamEventType.ERROR.equals(type)) {
            delta.setContent("[ERROR] " + chunk.content());
        } else {
            // ACTING/RAG_SOURCES/POST_* 等过程事件不映射
            return null;
        }

        if (firstChunk.compareAndSet(true, false)) {
            delta.setRole("assistant");
        }

        ChatCompletionChunk.ChunkChoice choice = new ChatCompletionChunk.ChunkChoice();
        choice.setIndex(0);
        choice.setDelta(delta);
        return buildChunk(completionId, created, model, choice);
    }

    /**
     * 终止 chunk（空 delta + finish_reason=stop）
     */
    private ChatCompletionChunk stopChunk(String completionId, long created, String model) {
        ChatCompletionChunk.ChunkChoice choice = new ChatCompletionChunk.ChunkChoice();
        choice.setIndex(0);
        choice.setDelta(new ChatCompletionChunk.Delta());
        choice.setFinishReason("stop");
        return buildChunk(completionId, created, model, choice);
    }

    private ChatCompletionChunk buildChunk(String completionId, long created, String model,
                                           ChatCompletionChunk.ChunkChoice choice) {
        ChatCompletionChunk chunk = new ChatCompletionChunk();
        chunk.setId(completionId);
        chunk.setCreated(created);
        chunk.setModel(model);
        chunk.setChoices(List.of(choice));
        return chunk;
    }
}
