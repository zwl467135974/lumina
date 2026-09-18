package io.lumina.agent.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import io.lumina.agent.api.dto.AgentTaskRequestDTO;
import io.lumina.agent.api.dto.a2a.A2aAgentCard;
import io.lumina.agent.api.dto.a2a.A2aTask;
import io.lumina.agent.domain.model.Agent;
import io.lumina.agent.infrastructure.entity.AgentTaskDO;
import io.lumina.agent.service.A2aService;
import io.lumina.agent.service.AgentService;
import io.lumina.agent.service.AgentTaskService;
import io.lumina.common.core.ErrorCode;
import io.lumina.common.core.PageResult;
import io.lumina.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import reactor.core.publisher.Flux;

/**
 * A2A 协议服务实现
 *
 * <p>任务生命周期映射：QUEUED→submitted、RUNNING→working、COMPLETED→completed
 * （result 作为文本 Artifact）、FAILED→failed（errorMessage）、CANCELLED→canceled。
 * 多轮上下文：message.contextId（或顶层 contextId）透传为 conversationId。
 *
 * @author Lumina Team
 * @since 3.12.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class A2aServiceImpl implements A2aService {

    /** A2A 卡片列表上限 */
    private static final int CARD_LIST_MAX_SIZE = 100;

    private static final String TRANSPORT_JSONRPC = "JSONRPC";

    private final AgentService agentService;

    private final AgentTaskService agentTaskService;

    @Override
    public A2aAgentCard getAgentCard(Long agentId, String baseUrl) {
        Agent agent = requireAgent(agentId);
        return toCard(agent, baseUrl);
    }

    @Override
    public List<A2aAgentCard> listAgentCards(String baseUrl) {
        PageResult<Agent> page = agentService.pageAgents(null, null, 1, CARD_LIST_MAX_SIZE);
        List<A2aAgentCard> cards = new ArrayList<>();
        for (Agent agent : page.getList()) {
            if (agent.isActive()) {
                cards.add(toCard(agent, baseUrl));
            }
        }
        return cards;
    }

    @Override
    public A2aTask messageSend(Long agentId, JsonNode params) {
        AgentTaskDO task = submitA2aTask(agentId, params);
        log.info("A2A message/send: agentId={}, agentName={}, taskUuid={}",
                agentId, task.getAgentId(), task.getTaskUuid());
        return A2aTask.of(task.getTaskUuid(),
                contextIdOf(task),
                "submitted", null);
    }

    @Override
    public Flux<A2aTask> messageStream(Long agentId, JsonNode params) {
        AgentTaskDO task = submitA2aTask(agentId, params);
        String taskUuid = task.getTaskUuid();
        String contextId = contextIdOf(task);
        log.info("A2A message/stream: agentId={}, taskUuid={}", agentId, taskUuid);

        A2aTask initial = A2aTask.of(taskUuid, contextId, "submitted", null);
        return agentTaskService.streamTaskProgress(taskUuid)
                .map(event -> a2aTaskFromEvent(taskUuid, contextId, event))
                .takeUntil(a2aTask -> isTerminal(a2aTask.getStatus().getState()))
                .startWith(initial)
                .onErrorResume(e -> {
                    log.warn("A2A message/stream 中断: taskUuid={}, error={}", taskUuid, e.getMessage());
                    return Flux.just(A2aTask.of(taskUuid, contextId, "failed",
                            "stream interrupted: " + e.getMessage()));
                });
    }

    @Override
    public A2aTask taskGet(JsonNode params) {
        AgentTaskDO task = agentTaskService.getTask(requireTaskId(params));
        return toA2aTask(task);
    }

    @Override
    public A2aTask taskCancel(JsonNode params) {
        AgentTaskDO task = agentTaskService.cancelTask(requireTaskId(params));
        return toA2aTask(task);
    }

    // ==================== 私有方法 ====================

    /** message/send 与 message/stream 共用：解析入参并提交异步任务 */
    private AgentTaskDO submitA2aTask(Long agentId, JsonNode params) {
        Agent agent = requireAgent(agentId);
        String text = extractText(params);
        if (text.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "message.parts 中无文本内容");
        }

        AgentTaskRequestDTO dto = new AgentTaskRequestDTO();
        dto.setTask(text);
        String contextId = firstNonBlank(
                textOf(params, "contextId"),
                textOf(params.path("message"), "contextId"));
        if (contextId != null) {
            dto.setConversationId(contextId);
        }
        return agentTaskService.submitTask(agent.getAgentId(), dto);
    }

    private static String contextIdOf(AgentTaskDO task) {
        return task.getConversationUuid() != null ? task.getConversationUuid() : task.getTaskUuid();
    }

    /** 任务进度事件（Map）→ A2A Task（状态映射与 toA2aTask 一致） */
    private A2aTask a2aTaskFromEvent(String taskUuid, String contextId, java.util.Map<String, Object> event) {
        String status = String.valueOf(event.get("status"));
        String state;
        String message = null;
        switch (status) {
            case "QUEUED" -> state = "submitted";
            case "RUNNING" -> state = "working";
            case "COMPLETED" -> state = "completed";
            case "FAILED" -> {
                state = "failed";
                message = event.get("errorMessage") != null ? String.valueOf(event.get("errorMessage")) : null;
            }
            case "CANCELLED" -> state = "canceled";
            default -> state = "working";
        }
        A2aTask a2aTask = A2aTask.of(taskUuid, contextId, state, message);
        if ("completed".equals(state) && event.get("result") != null) {
            String result = String.valueOf(event.get("result"));
            a2aTask.setArtifacts(List.of(new A2aTask.Artifact(
                    UUID.randomUUID().toString(), "response",
                    List.of(new A2aTask.Part("text", result)))));
        }
        return a2aTask;
    }

    private static boolean isTerminal(String state) {
        return "completed".equals(state) || "failed".equals(state) || "canceled".equals(state);
    }

    private Agent requireAgent(Long agentId) {
        Agent agent = agentService.getAgentById(agentId);
        if (!agent.isActive()) {
            throw new BusinessException(ErrorCode.AGENT_NOT_ACTIVE);
        }
        return agent;
    }

    private String requireTaskId(JsonNode params) {
        if (params == null || params.path("id").asText("").isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "params.id 不能为空");
        }
        return params.path("id").asText();
    }

    /** message.parts 中全部文本段拼接（type=text 或带 text 字段的段） */
    private String extractText(JsonNode params) {
        JsonNode message = params == null ? null : params.path("message");
        if (message.isMissingNode() || message.isNull()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "params.message 不能为空");
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode part : message.path("parts")) {
            String partText = part.path("text").asText("");
            if (!partText.isEmpty() && ("text".equals(part.path("type").asText("text")) || part.has("text"))) {
                if (!text.isEmpty()) {
                    text.append('\n');
                }
                text.append(partText);
            }
        }
        return text.toString().trim();
    }

    private String textOf(JsonNode node, String field) {
        String value = node.path(field).asText("");
        return value.isBlank() ? null : value;
    }

    private String firstNonBlank(String a, String b) {
        return a != null ? a : b;
    }

    private A2aTask toA2aTask(AgentTaskDO task) {
        String state;
        String message = null;
        switch (task.getStatus() == null ? "" : task.getStatus()) {
            case "QUEUED" -> state = "submitted";
            case "RUNNING" -> state = "working";
            case "COMPLETED" -> state = "completed";
            case "FAILED" -> {
                state = "failed";
                message = task.getErrorMessage();
            }
            case "CANCELLED" -> state = "canceled";
            default -> state = "working";
        }
        A2aTask a2aTask = A2aTask.of(task.getTaskUuid(),
                task.getConversationUuid() != null ? task.getConversationUuid() : task.getTaskUuid(),
                state, message);
        if ("completed".equals(state)) {
            String result = task.getResult();
            a2aTask.setArtifacts(List.of(new A2aTask.Artifact(
                    UUID.randomUUID().toString(),
                    "response",
                    List.of(new A2aTask.Part("text", result != null ? result : "")))));
        }
        return a2aTask;
    }

    private A2aAgentCard toCard(Agent agent, String baseUrl) {
        A2aAgentCard card = new A2aAgentCard();
        card.setAgentId(agent.getAgentId());
        card.setName(agent.getAgentName());
        card.setDescription(agent.getDescription() != null ? agent.getDescription() : "");
        card.setUrl(baseUrl + "/v1/a2a/agents/" + agent.getAgentId());
        card.setVersion("1.0.0");
        card.setProtocolVersion("0.3.0");
        card.setPreferredTransport(TRANSPORT_JSONRPC);
        A2aAgentCard.Capabilities capabilities = new A2aAgentCard.Capabilities();
        capabilities.setStreaming(true);
        card.setCapabilities(capabilities);
        card.setDefaultInputModes(List.of("text/plain"));
        card.setDefaultOutputModes(List.of("text/plain"));

        A2aAgentCard.Skill skill = new A2aAgentCard.Skill();
        skill.setId(agent.getAgentName());
        skill.setName(agent.getAgentName());
        skill.setDescription(card.getDescription());
        skill.setTags(agent.getAgentType() != null ? List.of(agent.getAgentType()) : List.of());
        card.setSkills(List.of(skill));
        return card;
    }
}
