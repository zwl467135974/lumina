package io.lumina.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.lumina.agent.api.dto.AgentTaskRequestDTO;
import io.lumina.agent.domain.model.Agent;
import io.lumina.agent.infrastructure.entity.AgentTaskDO;
import io.lumina.agent.infrastructure.mapper.AgentTaskMapper;
import io.lumina.agent.service.AgentService;
import io.lumina.agent.service.AgentTaskService;
import io.lumina.agent.service.TaskProgressRegistry;
import io.lumina.common.core.BaseContext;
import io.lumina.common.core.ErrorCode;
import io.lumina.common.core.LoginContext;
import io.lumina.common.core.PageResult;
import io.lumina.common.exception.BusinessException;
import io.lumina.framework.config.RocketMQConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import io.lumina.agent.mq.AgentTaskMessage;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private static final String STATUS_CANCELLED = "CANCELLED";

    private final AgentTaskMapper agentTaskMapper;
    private final AgentService agentService;
    private final Executor agentTaskExecutor;
    private final TaskProgressRegistry progressRegistry;

    @Autowired(required = false)
    private RocketMQTemplate rocketMQTemplate;

    @org.springframework.beans.factory.annotation.Value("${rocketmq.consumer.agent-task.enabled:false}")
    private boolean mqTaskEnabled;

    public AgentTaskServiceImpl(AgentTaskMapper agentTaskMapper,
                                AgentService agentService,
                                @Qualifier("agentTaskExecutor") Executor agentTaskExecutor,
                                TaskProgressRegistry progressRegistry) {
        this.agentTaskMapper = agentTaskMapper;
        this.agentService = agentService;
        this.agentTaskExecutor = agentTaskExecutor;
        this.progressRegistry = progressRegistry;
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

        progressRegistry.register(task.getTaskUuid());
        progressRegistry.emit(task.getTaskUuid(), buildEvent(task));

        dispatchTask(task.getTaskUuid(), BaseContext.current());
        log.info("Agent 异步任务已提交: taskUuid={}, agentId={}, via={}",
                task.getTaskUuid(), agentId, mqTaskEnabled && rocketMQTemplate != null ? "MQ" : "LOCAL");
        return task;
    }

    /**
     * 派发任务：优先使用 RocketMQ（分布式可靠投递），MQ 不可用时回退到本地线程池
     */
    private void dispatchTask(String taskUuid, LoginContext loginContext) {
        if (mqTaskEnabled && rocketMQTemplate != null) {
            AgentTaskMessage msg = new AgentTaskMessage(
                    taskUuid,
                    loginContext != null ? loginContext.tenantId() : null,
                    loginContext != null ? loginContext.userId() : null,
                    loginContext != null ? loginContext.username() : null
            );
            rocketMQTemplate.convertAndSend(RocketMQConfig.TOPIC_AGENT_TASK, msg);
        } else {
            agentTaskExecutor.execute(() -> executeTask(taskUuid, loginContext));
        }
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

    public void executeTask(String taskUuid, LoginContext loginContext) {
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
        task.setStatus(STATUS_RUNNING);
        progressRegistry.emit(task.getTaskUuid(), buildEvent(task));
    }

    private void markCompleted(String taskUuid, String result, long durationMs) {
        AgentTaskDO task = selectByUuid(taskUuid);
        if (task == null) return;
        if (STATUS_CANCELLED.equals(task.getStatus())) {
            log.info("任务已取消，跳过完成: {}", taskUuid);
            return;
        }
        task.setStatus(STATUS_COMPLETED);
        task.setResult(result);
        task.setDurationMs(durationMs);
        task.setUpdateTime(LocalDateTime.now());
        agentTaskMapper.updateById(task);
        progressRegistry.emit(taskUuid, buildEvent(task));
        log.info("Agent 异步任务完成: taskUuid={}, durationMs={}", taskUuid, durationMs);
    }

    private void markFailed(String taskUuid, Exception e, long durationMs) {
        AgentTaskDO task = selectByUuid(taskUuid);
        if (task == null) return;
        if (STATUS_CANCELLED.equals(task.getStatus())) {
            log.info("任务已取消，跳过失败标记: {}", taskUuid);
            return;
        }
        task.setStatus(STATUS_FAILED);
        task.setErrorMessage(e.getMessage());
        task.setDurationMs(durationMs);
        task.setUpdateTime(LocalDateTime.now());
        agentTaskMapper.updateById(task);
        progressRegistry.emit(taskUuid, buildEvent(task));
        log.error("Agent 异步任务失败: taskUuid={}", taskUuid, e);
    }

    private Map<String, Object> buildEvent(AgentTaskDO task) {
        Map<String, Object> event = new HashMap<>();
        event.put("taskUuid", task.getTaskUuid());
        event.put("status", task.getStatus());
        event.put("agentId", task.getAgentId());
        if (task.getResult() != null) {
            event.put("result", task.getResult());
        }
        if (task.getErrorMessage() != null) {
            event.put("errorMessage", task.getErrorMessage());
        }
        if (task.getDurationMs() != null) {
            event.put("durationMs", task.getDurationMs());
        }
        if (task.getTotalTokens() != null) {
            event.put("totalTokens", task.getTotalTokens());
        }
        return event;
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

    @Override
    public PageResult<AgentTaskDO> pageTasks(Long agentId, String status, int pageNum, int pageSize) {
        LambdaQueryWrapper<AgentTaskDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentTaskDO::getTenantId, currentTenant());
        wrapper.eq(AgentTaskDO::getIsDeleted, 0);
        if (agentId != null) {
            wrapper.eq(AgentTaskDO::getAgentId, agentId);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(AgentTaskDO::getStatus, status);
        }
        wrapper.orderByDesc(AgentTaskDO::getCreateTime);

        Page<AgentTaskDO> page = new Page<>(pageNum, pageSize);
        Page<AgentTaskDO> result = agentTaskMapper.selectPage(page, wrapper);

        PageResult<AgentTaskDO> pageResult = new PageResult<>();
        pageResult.setList(result.getRecords());
        pageResult.setTotal(result.getTotal());
        pageResult.setPageNum(pageNum);
        pageResult.setPageSize(pageSize);
        pageResult.setPages((int) result.getPages());
        return pageResult;
    }

    @Override
    public AgentTaskDO cancelTask(String taskUuid) {
        AgentTaskDO task = getTask(taskUuid);
        String currentStatus = task.getStatus();
        if (!STATUS_QUEUED.equals(currentStatus) && !STATUS_RUNNING.equals(currentStatus)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "任务状态 " + currentStatus + " 不可取消");
        }
        task.setStatus(STATUS_CANCELLED);
        task.setUpdateTime(LocalDateTime.now());
        agentTaskMapper.updateById(task);
        progressRegistry.emit(taskUuid, buildEvent(task));
        log.info("Agent 异步任务已取消: taskUuid={}", taskUuid);
        return task;
    }

    @Override
    public Flux<Map<String, Object>> streamTaskProgress(String taskUuid) {
        Sinks.Many<Map<String, Object>> sink = progressRegistry.getSink(taskUuid);
        if (sink != null) {
            return sink.asFlux().timeout(Duration.ofMinutes(10));
        }

        AgentTaskDO task = getTask(taskUuid);
        return Flux.just(buildEvent(task));
    }
}
