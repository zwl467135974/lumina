package io.lumina.agent.api.controller;

import io.lumina.agent.api.dto.CreateAgentDTO;
import io.lumina.agent.api.dto.AgentTaskRequestDTO;
import io.lumina.agent.api.dto.MultimodalRequestDTO;
import io.lumina.agent.api.vo.AgentTaskVO;
import io.lumina.agent.api.vo.AgentVO;
import io.lumina.agent.domain.model.Agent;
import io.lumina.agent.infrastructure.entity.AgentTaskDO;
import io.lumina.agent.model.MultimodalImage;
import io.lumina.agent.model.StreamChunk;
import io.lumina.agent.service.AgentService;
import io.lumina.agent.service.AgentTaskService;
import io.lumina.agent.service.KnowledgeBaseService;
import io.lumina.common.core.ErrorCode;
import io.lumina.framework.audit.annotation.Audit;
import io.lumina.common.core.PageResult;
import io.lumina.common.core.R;
import io.lumina.common.exception.BusinessException;
import io.lumina.common.util.CollectionUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import jakarta.validation.Valid;
import java.util.List;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Agent Controller
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/agents")
@Validated
@RequiredArgsConstructor
public class AgentController {

    private static final int MAX_IMAGE_COUNT = 5;

    private static final long MAX_IMAGE_SIZE = 10L * 1024L * 1024L;

    private static final Set<String> SUPPORTED_IMAGE_TYPES = Set.of(
            MediaType.IMAGE_PNG_VALUE,
            MediaType.IMAGE_JPEG_VALUE,
            "image/webp"
    );

    private final AgentService agentService;

    private final AgentTaskService agentTaskService;

    private final ObjectMapper objectMapper;

    private final KnowledgeBaseService knowledgeBaseService;

    /**
     * 创建 Agent
     */
    @Audit(module = "agent", action = "CREATE", description = "创建Agent")
    @PostMapping
    public R<AgentVO> createAgent(@Valid @RequestBody CreateAgentDTO dto) {
        log.info("创建 Agent: {}", dto.getAgentName());

        Agent agent = toDomain(dto);

        Agent createdAgent = agentService.createAgent(agent);

        // 挂载知识库（中间表 lumina_agent_knowledge_base）
        if (dto.getKnowledgeBaseIds() != null && !dto.getKnowledgeBaseIds().isEmpty()) {
            for (Long kbId : dto.getKnowledgeBaseIds()) {
                try {
                    knowledgeBaseService.mountKnowledgeBase(createdAgent.getAgentId(), kbId);
                } catch (Exception e) {
                    log.warn("挂载知识库失败 agentId={}, kbId={}: {}", createdAgent.getAgentId(), kbId, e.getMessage());
                }
            }
        }

        AgentVO vo = toVO(createdAgent);
        vo.setKnowledgeBaseIds(dto.getKnowledgeBaseIds());

        return R.success(vo);
    }

    /**
     * 更新 Agent
     */
    @Audit(module = "agent", action = "UPDATE", description = "更新Agent")
    @PutMapping("/{id}")
    public R<AgentVO> updateAgent(
            @PathVariable("id") Long id,
            @Valid @RequestBody CreateAgentDTO dto) {
        log.info("更新 Agent: id={}", id);

        Agent agent = toDomain(dto);

        Agent updatedAgent = agentService.updateAgent(id, agent);

        // 知识库挂载 diff：新增的 mount、移除的 unmount
        if (dto.getKnowledgeBaseIds() != null) {
            syncKnowledgeBases(id, dto.getKnowledgeBaseIds());
        }

        AgentVO vo = toVO(updatedAgent);
        vo.setKnowledgeBaseIds(knowledgeBaseService.getAgentKnowledgeBaseIds(id));

        return R.success(vo);
    }

    /**
     * 同步 Agent 的知识库挂载（diff：新增 mount、移除 unmount）
     */
    private void syncKnowledgeBases(Long agentId, List<Long> desiredKbIds) {
        List<Long> currentIds = knowledgeBaseService.getAgentKnowledgeBaseIds(agentId);
        Set<Long> currentSet = new HashSet<>(currentIds);
        Set<Long> desiredSet = new HashSet<>(desiredKbIds);

        // 新增挂载
        for (Long kbId : desiredSet) {
            if (!currentSet.contains(kbId)) {
                try {
                    knowledgeBaseService.mountKnowledgeBase(agentId, kbId);
                } catch (Exception e) {
                    log.warn("挂载知识库失败 agentId={}, kbId={}: {}", agentId, kbId, e.getMessage());
                }
            }
        }
        // 移除挂载
        for (Long kbId : currentSet) {
            if (!desiredSet.contains(kbId)) {
                try {
                    knowledgeBaseService.unmountKnowledgeBase(agentId, kbId);
                } catch (Exception e) {
                    log.warn("卸载知识库失败 agentId={}, kbId={}: {}", agentId, kbId, e.getMessage());
                }
            }
        }
    }

    /**
     * Agent → AgentVO（含 apiKey 脱敏）
     */
    private AgentVO toVO(Agent agent) {
        AgentVO vo = new AgentVO();
        BeanUtils.copyProperties(agent, vo);
        maskApiKeyInLlmConfig(vo);
        return vo;
    }

    /**
     * DTO → Agent 领域模型转换
     *
     * <p>处理类型差异：DTO 的 tools 是 List<String>、llmConfig 是对象，
     * Agent 领域模型的 tools/llmConfig 是 String（逗号分隔 / JSON 字符串），用于 DB 存储。
     */
    private Agent toDomain(CreateAgentDTO dto) {
        Agent agent = new Agent();
        agent.setAgentName(dto.getAgentName());
        agent.setAgentType(dto.getAgentType());
        agent.setDescription(dto.getDescription());

        // tools: List<String> → 逗号分隔 String
        if (dto.getTools() != null && !dto.getTools().isEmpty()) {
            agent.setTools(String.join(",", dto.getTools()));
        }

        // llmConfig: 对象 → JSON 字符串
        if (dto.getLlmConfig() != null) {
            try {
                agent.setLlmConfig(objectMapper.writeValueAsString(dto.getLlmConfig()));
            } catch (Exception e) {
                log.warn("序列化 llmConfig 失败: {}", e.getMessage());
            }
        }

        // 透传 Per-Agent 限流/并发配置
        agent.setRateLimit(dto.getRateLimit());
        agent.setMaxConcurrent(dto.getMaxConcurrent());

        return agent;
    }

    /**
     * 脱敏 VO 中的 llmConfig JSON(移除 apiKey 明文)
     *
     * <p>前端编辑时如需修改 apiKey 会重新输入;不输入则保留原值(由 updateAgent 的 null 跳过逻辑保证)。
     */
    private void maskApiKeyInLlmConfig(AgentVO vo) {
        String llmConfig = vo.getLlmConfig();
        if (llmConfig == null || llmConfig.isBlank()) {
            return;
        }
        try {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> config = objectMapper.readValue(llmConfig, Map.class);
            if (config.containsKey("apiKey")) {
                config.put("apiKey", null); // 返回 null，前端显示空(不回显明文)
                vo.setLlmConfig(objectMapper.writeValueAsString(config));
            }
        } catch (Exception e) {
            log.debug("llmConfig 非 JSON 格式，跳过脱敏: {}", e.getMessage());
        }
    }

    /**
     * 删除 Agent
     */
    @Audit(module = "agent", action = "DELETE", description = "删除Agent")
    @DeleteMapping("/{id}")
    public R<Void> deleteAgent(@PathVariable("id") Long id) {
        log.info("删除 Agent: id={}", id);
        agentService.deleteAgent(id);
        return R.success();
    }

    /**
     * 获取 Agent 详情
     */
    @GetMapping("/{id}")
    public R<AgentVO> getAgent(@PathVariable("id") Long id) {
        log.info("查询 Agent: id={}", id);

        Agent agent = agentService.getAgentById(id);

        AgentVO vo = toVO(agent);
        vo.setKnowledgeBaseIds(knowledgeBaseService.getAgentKnowledgeBaseIds(id));

        return R.success(vo);
    }

    /**
     * 分页查询 Agent 列表
     */
    @GetMapping
    public R<PageResult<AgentVO>> pageAgents(
            @RequestParam(required = false) String agentName,
            @RequestParam(required = false) String agentType,
            @RequestParam(defaultValue = "1") @Min(1) Integer pageNum,
            @RequestParam(defaultValue = "10") @Min(1) Integer pageSize) {
        log.info("分页查询 Agent: name={}, type={}, pageNum={}, pageSize={}",
                agentName, agentType, pageNum, pageSize);

        PageResult<Agent> pageResult = agentService.pageAgents(agentName, agentType, pageNum, pageSize);

        // 转换为 VO
        PageResult<AgentVO> voPageResult = new PageResult<>();
        voPageResult.setPageNum(pageResult.getPageNum());
        voPageResult.setPageSize(pageResult.getPageSize());
        voPageResult.setTotal(pageResult.getTotal());
        voPageResult.setPages(pageResult.getPages());

        // 转换列表
        java.util.List<AgentVO> voList = pageResult.getList().stream()
                .map(agent -> {
                    AgentVO vo = new AgentVO();
                    BeanUtils.copyProperties(agent, vo);
                    return vo;
                })
                .collect(java.util.stream.Collectors.toList());

        voPageResult.setList(voList);

        return R.success(voPageResult);
    }

    /**
     * 执行 Agent
     *
     * @param conversationId 会话 UUID（可选，传入则启用多轮上下文）
     */
    @Audit(module = "agent", action = "EXECUTE", description = "执行Agent")
    @PostMapping("/{id}/execute")
    public R<String> executeAgent(
            @PathVariable("id") Long id,
            @RequestBody java.util.Map<String, String> body) {
        String task = body.get("task");
        String conversationId = body.get("conversationId");
        log.info("执行 Agent: id={}, task={}, conversationId={}", id, task, conversationId);

        if (task == null || task.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.AGENT_TASK_EMPTY);
        }

        String result = agentService.executeAgent(id, task, conversationId);

        return R.success(result);
    }

    /**
     * 提交 Agent 异步任务
     */
    @Audit(module = "agent", action = "EXECUTE_ASYNC", description = "异步执行Agent")
    @PostMapping("/{id}/execute/async")
    public R<AgentTaskVO> submitAgentTask(
            @PathVariable("id") Long id,
            @Valid @RequestBody AgentTaskRequestDTO dto) {
        log.info("提交 Agent 异步任务: agentId={}, task={}", id, dto.getTask());
        AgentTaskDO task = agentTaskService.submitTask(id, dto);
        return R.success(toTaskVO(task));
    }

    /**
     * 查询 Agent 异步任务详情
     */
    @GetMapping("/tasks/{taskUuid}")
    public R<AgentTaskVO> getAgentTask(@PathVariable("taskUuid") String taskUuid) {
        AgentTaskDO task = agentTaskService.getTask(taskUuid);
        return R.success(toTaskVO(task));
    }

    /**
     * 异步任务 SSE 进度推送
     *
     * <p>连接后实时接收任务状态变更事件（QUEUED → RUNNING → COMPLETED/FAILED/CANCELLED）。
     * 若任务已结束（sink 已清理），则立即返回当前 DB 状态后关闭连接。
     */
    @GetMapping(value = "/tasks/{taskUuid}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Map<String, Object>>> streamAgentTask(
            @PathVariable("taskUuid") String taskUuid) {
        log.info("SSE 订阅异步任务进度: taskUuid={}", taskUuid);

        return agentTaskService.streamTaskProgress(taskUuid)
                .map(event -> ServerSentEvent.<Map<String, Object>>builder()
                        .event((String) event.getOrDefault("status", "UPDATE"))
                        .data(event)
                        .build());
    }

    /**
     * 分页查询异步任务列表
     */
    @GetMapping("/tasks")
    public R<PageResult<AgentTaskVO>> pageAgentTasks(
            @RequestParam(required = false) Long agentId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") @Min(1) Integer pageNum,
            @RequestParam(defaultValue = "10") @Min(1) Integer pageSize) {
        log.info("分页查询异步任务: agentId={}, status={}, pageNum={}, pageSize={}", agentId, status, pageNum, pageSize);

        PageResult<AgentTaskDO> pageResult = agentTaskService.pageTasks(agentId, status, pageNum, pageSize);

        PageResult<AgentTaskVO> voPageResult = new PageResult<>();
        voPageResult.setPageNum(pageResult.getPageNum());
        voPageResult.setPageSize(pageResult.getPageSize());
        voPageResult.setTotal(pageResult.getTotal());
        voPageResult.setPages(pageResult.getPages());
        voPageResult.setList(pageResult.getList().stream()
                .map(this::toTaskVO)
                .collect(java.util.stream.Collectors.toList()));

        return R.success(voPageResult);
    }

    /**
     * 取消异步任务
     */
    @Audit(module = "agent", action = "UPDATE", description = "取消异步任务")
    @PostMapping("/tasks/{taskUuid}/cancel")
    public R<AgentTaskVO> cancelAgentTask(@PathVariable("taskUuid") String taskUuid) {
        log.info("取消异步任务: taskUuid={}", taskUuid);
        AgentTaskDO task = agentTaskService.cancelTask(taskUuid);
        return R.success(toTaskVO(task));
    }

    /**
     * 多模态执行 Agent（文本 + 图片）
     *
     * <p>图片需先通过 POST /api/v1/files/upload 上传，获取 fileUuid 后传入。
     *
     * @param dto 请求体（task + fileUuids + conversationId）
     */
    @Audit(module = "agent", action = "EXECUTE_MULTIMODAL", description = "多模态执行Agent")
    @PostMapping("/{id}/execute/multimodal")
    public R<String> executeAgentMultimodal(
            @PathVariable("id") Long id,
            @Valid @RequestBody MultimodalRequestDTO dto) {
        log.info("多模态执行 Agent: id={}, task={}, fileCount={}, conversationId={}",
                id, dto.getTask(), dto.getFileUuids() != null ? dto.getFileUuids().size() : 0, dto.getConversationId());

        String result = agentService.executeAgentMultimodal(id, dto.getTask(), dto.getFileUuids(), dto.getConversationId());
        return R.success(result);
    }

    /**
     * 流式执行 Agent（SSE，逐片段返回，用于打字机效果）
     *
     * <p>每个 SSE 事件的 event 字段为片段类型（REASONING_CHUNK/ACTING_CHUNK/FINAL/ERROR），
     * data 字段为 {@link StreamChunk} JSON。
     *
     * @param conversationId 会话 UUID（可选，传入则启用多轮上下文）
     */
    @PostMapping(value = "/{id}/execute/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<StreamChunk>> executeAgentStream(
            @PathVariable("id") Long id,
            @RequestParam String task,
            @RequestParam(required = false) String conversationId) {
        log.info("流式执行 Agent: id={}, task={}, conversationId={}", id, task, conversationId);

        if (task == null || task.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.AGENT_TASK_EMPTY);
        }

        return agentService.executeAgentStream(id, task, conversationId)
                .map(chunk -> ServerSentEvent.<StreamChunk>builder()
                        .id(String.valueOf(System.nanoTime()))
                        .event(chunk.type())
                        .data(chunk)
                        .build());
    }

    /**
     * 流式多模态执行 Agent（SSE，文本 + 图片，逐片段返回）
     *
     * <p>请求体为 JSON（task + fileUuids + conversationId），响应为 SSE 流。
     *
     * @param dto 请求体（task + fileUuids + conversationId）
     */
    @Audit(module = "agent", action = "EXECUTE_MULTIMODAL_STREAM", description = "流式多模态执行Agent")
    @PostMapping(value = "/{id}/execute/multimodal/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<StreamChunk>> executeAgentMultimodalStream(
            @PathVariable("id") Long id,
            @Valid @RequestBody MultimodalRequestDTO dto) {
        log.info("流式多模态执行 Agent: id={}, task={}, fileCount={}, conversationId={}",
                id, dto.getTask(), dto.getFileUuids() != null ? dto.getFileUuids().size() : 0, dto.getConversationId());

        return agentService.executeAgentMultimodalStream(id, dto.getTask(), dto.getFileUuids(), dto.getConversationId())
                .map(chunk -> ServerSentEvent.<StreamChunk>builder()
                        .id(String.valueOf(System.nanoTime()))
                        .event(chunk.type())
                        .data(chunk)
                        .build());
    }

    private AgentTaskVO toTaskVO(AgentTaskDO task) {
        AgentTaskVO vo = new AgentTaskVO();
        BeanUtils.copyProperties(task, vo);
        return vo;
    }
}
