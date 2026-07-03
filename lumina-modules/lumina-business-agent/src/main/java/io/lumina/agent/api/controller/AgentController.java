package io.lumina.agent.api.controller;

import io.lumina.agent.api.dto.CreateAgentDTO;
import io.lumina.agent.api.dto.MultimodalRequestDTO;
import io.lumina.agent.api.vo.AgentVO;
import io.lumina.agent.domain.model.Agent;
import io.lumina.agent.model.MultimodalImage;
import io.lumina.agent.model.StreamChunk;
import io.lumina.agent.service.AgentService;
import io.lumina.common.core.ErrorCode;
import io.lumina.framework.audit.annotation.Audit;
import io.lumina.common.core.PageResult;
import io.lumina.common.core.R;
import io.lumina.common.exception.BusinessException;
import io.lumina.common.util.CollectionUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
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
public class AgentController {

    private static final int MAX_IMAGE_COUNT = 5;

    private static final long MAX_IMAGE_SIZE = 10L * 1024L * 1024L;

    private static final Set<String> SUPPORTED_IMAGE_TYPES = Set.of(
            MediaType.IMAGE_PNG_VALUE,
            MediaType.IMAGE_JPEG_VALUE,
            "image/webp"
    );

    @Autowired
    private AgentService agentService;

    /**
     * 创建 Agent
     */
    @Audit(module = "agent", action = "CREATE", description = "创建Agent")
    @PostMapping
    public R<AgentVO> createAgent(@Valid @RequestBody CreateAgentDTO dto) {
        log.info("创建 Agent: {}", dto.getAgentName());

        // DTO 转领域模型
        Agent agent = new Agent();
        BeanUtils.copyProperties(dto, agent);

        // 调用服务
        Agent createdAgent = agentService.createAgent(agent);

        // 领域模型转 VO
        AgentVO vo = new AgentVO();
        BeanUtils.copyProperties(createdAgent, vo);

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

        // DTO 转领域模型
        Agent agent = new Agent();
        BeanUtils.copyProperties(dto, agent);

        // 调用服务
        Agent updatedAgent = agentService.updateAgent(id, agent);

        // 领域模型转 VO
        AgentVO vo = new AgentVO();
        BeanUtils.copyProperties(updatedAgent, vo);

        return R.success(vo);
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

        // 领域模型转 VO
        AgentVO vo = new AgentVO();
        BeanUtils.copyProperties(agent, vo);

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
            @RequestParam String task,
            @RequestParam(required = false) String conversationId) {
        log.info("执行 Agent: id={}, task={}, conversationId={}", id, task, conversationId);

        if (task == null || task.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.AGENT_TASK_EMPTY);
        }

        String result = agentService.executeAgent(id, task, conversationId);

        return R.success(result);
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
            @RequestBody MultimodalRequestDTO dto) {
        log.info("多模态执行 Agent: id={}, task={}, fileCount={}, conversationId={}",
                id, dto.getTask(), dto.getFileUuids() != null ? dto.getFileUuids().size() : 0, dto.getConversationId());

        if (dto.getTask() == null || dto.getTask().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.AGENT_TASK_EMPTY);
        }

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
}
