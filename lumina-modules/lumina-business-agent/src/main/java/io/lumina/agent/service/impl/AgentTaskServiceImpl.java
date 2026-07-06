package io.lumina.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.lumina.agent.api.dto.AgentTaskRequestDTO;
import io.lumina.agent.domain.model.Agent;
import io.lumina.agent.infrastructure.entity.AgentTaskDO;
import io.lumina.agent.infrastructure.mapper.AgentTaskMapper;
import io.lumina.agent.service.AgentService;
import io.lumina.agent.service.AgentTaskService;
import io.lumina.common.core.BaseContext;
import io.lumina.common.core.ErrorCode;
import io.lumina.common.core.LoginContext;
import io.lumina.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Agent 异步任务服务实现
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Slf4j
@Service
public class AgentTaskServiceImpl implements AgentTaskService {

    private static final String STATUS_QUEUED = "QUEUED";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED = "FAILED";

    private final AgentTaskMapper agentTaskMapper;
    private final AgentService agentService;
    private final Executor agentTaskExecutor;

    public AgentTaskServiceImpl(AgentTaskMapper agentTaskMapper,
                                AgentService agentService,
                                @Qualifier("agentTaskExecutor") Executor agentTaskExecutor) {
        this.agentTaskMapper = agentTaskMapper;
        this.agentService = agentService;
        this.agentTaskExecutor = agentTaskExecutor;
    }

    @Override
    public AgentTaskDO submitTask(Long agentId, AgentTaskRequestDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getTask())) {
            throw new BusinessException(ErrorCode.AGENT_TASK_EMPTY);
        }

        // 提交阶段先校验 Agent 存在且启用，避免无效任务进入队列。
        Agent agent = agentService.getAgentById(agentId);
        if (!agent.isActive()) {
            throw new BusinessException(ErrorCode.AGENT_NOT_ACTIVE);
        }

        AgentTaskDO task = new AgentTaskDO();
        task.setTaskUuid(UUID.randomUUID().toString());
        task.setAgentId(agentId);
        task.setConversationUuid(dto.getConversationId());
        task.setInputText(dto.getTask());
        task.setFileIds(toFileIdsJson(dto));
        task.setStatus(STATUS_QUEUED);
        task.setPromptTokens(0);
        task.setCompletionTokens(0);
        task.setTotalTokens(0);
        task.setTenantId(currentTenant());
        task.setCreateBy(BaseContext.getUserId());
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        task.setIsDeleted(0);
        agentTaskMapper.insert(task);

        agentTaskExecutor.execute(() -> executeTask(task.getTaskUuid(), BaseContext.current()));
        log.info("Agent 异步任务已提交: taskUuid={}, agentId={}", task.getTaskUuid(), agentId);
        return task;
    }

    @Override
    public AgentTaskDO getTask(String taskUuid) {
        LambdaQueryWrapper<AgentTaskDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentTaskDO::getTaskUuid, taskUuid);
        wrapper.eq(AgentTaskDO::getTenantId, currentTenant());
        wrapper.eq(AgentTaskDO::getIsDeleted, 0);
        wrapper.last("LIMIT 1");
        AgentTaskDO task = agentTaskMapper.selectOne(wrapper);
        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Agent 任务不存在: " + taskUuid);
        }
        return task;
    }

    void executeTask(String taskUuid, LoginContext loginContext) {
        BaseContext.setCurrent(loginContext);
        long start = System.currentTimeMillis();
        try {
            AgentTaskDO task = selectByUuid(taskUuid);
            if (task == null) {
                log.warn("Agent 异步任务不存在: {}", taskUuid);
                return;
            }

            markRunning(task);
            String result;
            List<String> fileUuids = parseFileUuids(task.getFileIds());
            if (fileUuids.isEmpty()) {
                result = agentService.executeAgent(task.getAgentId(), task.getInputText(), task.getConversationUuid());
            } else {
                result = agentService.executeAgentMultimodal(
                        task.getAgentId(), task.getInputText(), fileUuids, task.getConversationUuid());
            }
            markCompleted(taskUuid, result, System.currentTimeMillis() - start);
        } catch (Exception e) {
            markFailed(taskUuid, e, System.currentTimeMillis() - start);
        } finally {
            BaseContext.clear();
        }
    }

    private AgentTaskDO selectByUuid(String taskUuid) {
        LambdaQueryWrapper<AgentTaskDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentTaskDO::getTaskUuid, taskUuid);
        wrapper.eq(AgentTaskDO::getIsDeleted, 0);
        wrapper.last("LIMIT 1");
        return agentTaskMapper.selectOne(wrapper);
    }

    private void markRunning(AgentTaskDO task) {
        AgentTaskDO update = new AgentTaskDO();
        update.setId(task.getId());
        update.setStatus(STATUS_RUNNING);
        update.setUpdateTime(LocalDateTime.now());
        agentTaskMapper.updateById(update);
    }

    private void markCompleted(String taskUuid, String result, long durationMs) {
        AgentTaskDO task = selectByUuid(taskUuid);
        if (task == null) return;
        task.setStatus(STATUS_COMPLETED);
        task.setResult(result);
        task.setDurationMs(durationMs);
        task.setUpdateTime(LocalDateTime.now());
        agentTaskMapper.updateById(task);
        log.info("Agent 异步任务完成: taskUuid={}, durationMs={}", taskUuid, durationMs);
    }

    private void markFailed(String taskUuid, Exception e, long durationMs) {
        AgentTaskDO task = selectByUuid(taskUuid);
        if (task == null) return;
        task.setStatus(STATUS_FAILED);
        task.setErrorMessage(e.getMessage());
        task.setDurationMs(durationMs);
        task.setUpdateTime(LocalDateTime.now());
        agentTaskMapper.updateById(task);
        log.error("Agent 异步任务失败: taskUuid={}", taskUuid, e);
    }

    private String toFileIdsJson(AgentTaskRequestDTO dto) {
        if (dto.getFileUuids() == null || dto.getFileUuids().isEmpty()) {
            return null;
        }
        return String.join(",", dto.getFileUuids());
    }

    private List<String> parseFileUuids(String fileIds) {
        if (!StringUtils.hasText(fileIds)) {
            return List.of();
        }
        return Arrays.stream(fileIds.split(","))
                .filter(StringUtils::hasText)
                .toList();
    }

    private Long currentTenant() {
        return BaseContext.getTenantId() != null ? BaseContext.getTenantId() : 0L;
    }
}
