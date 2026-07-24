package io.lumina.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.lumina.agent.api.dto.AgentTaskRequestDTO;
import io.lumina.agent.domain.model.Agent;
import io.lumina.agent.infrastructure.entity.AgentTaskDO;
import io.lumina.agent.model.ExecuteResult;
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
import io.lumina.notification.event.NotificationEvent;
import io.lumina.notification.event.NotificationEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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

    @Autowired
    private NotificationEventPublisher notificationEventPublisher;

    @Autowired
    private io.lumina.agent.infrastructure.mapper.AgentMapper agentMapper;

    @Autowired
    private io.lumina.agent.config.LuminaAgentProperties agentProperties;

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

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
    @Transactional(rollbackFor = Exception.class)
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

        LoginContext loginContext = BaseContext.current();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            dispatchTask(task.getTaskUuid(), loginContext);
                        }
                    });
        } else {
            dispatchTask(task.getTaskUuid(), loginContext);
        }
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

    @Override
    public void executeTask(String taskUuid, LoginContext loginContext) {
        BaseContext.setCurrent(loginContext);
        BaseContext.setTaskUuid(taskUuid);
        long start = System.currentTimeMillis();
        try {
            AgentTaskDO task = selectByUuid(taskUuid);
            if (task == null) {
                log.warn("Agent 异步任务不存在: {}", taskUuid);
                return;
            }

            markRunning(task);
            String result;
            ExecuteResult.TokenUsage tokenUsage = null;
            List<String> fileUuids = parseFileUuids(task.getFileIds());
            if (fileUuids.isEmpty()) {
                ExecuteResult execResult = agentService.executeAgentForResult(
                        task.getAgentId(), task.getInputText(), task.getConversationUuid());
                result = execResult.getResult();
                tokenUsage = execResult.getTokenUsage();
            } else {
                ExecuteResult execResult = agentService.executeAgentMultimodalForResult(
                        task.getAgentId(), task.getInputText(), fileUuids, task.getConversationUuid());
                result = execResult.getResult();
                tokenUsage = execResult.getTokenUsage();
            }
            String[] modelInfo = resolveModelInfo(task.getAgentId());
            int promptTokens = tokenUsage != null && tokenUsage.getPromptTokens() != null ? tokenUsage.getPromptTokens() : 0;
            int completionTokens = tokenUsage != null && tokenUsage.getCompletionTokens() != null ? tokenUsage.getCompletionTokens() : 0;
            int totalTokens = tokenUsage != null && tokenUsage.getTotalTokens() != null ? tokenUsage.getTotalTokens() : 0;
            markCompleted(taskUuid, result, System.currentTimeMillis() - start, modelInfo[0], modelInfo[1],
                    promptTokens, completionTokens, totalTokens);
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

    /**
     * 从 Agent 的 llmConfig 或全局配置解析模型信息
     * @return [modelName, provider]
     */
    private String[] resolveModelInfo(Long agentId) {
        try {
            io.lumina.agent.infrastructure.entity.AgentDO agent = agentMapper.selectById(agentId);
            if (agent != null && agent.getLlmConfig() != null && !agent.getLlmConfig().isBlank()) {
                var config = objectMapper.readTree(agent.getLlmConfig());
                String modelName = config.has("modelName") ? config.get("modelName").asText() : null;
                String modelType = config.has("modelType") ? config.get("modelType").asText() : null;
                if (modelName != null) return new String[]{modelName, modelType != null ? modelType : "default"};
            }
        } catch (Exception e) {
            log.debug("解析 Agent LLM 配置失败，使用全局默认: {}", e.getMessage());
        }
        if (agentProperties != null && agentProperties.getLlm() != null) {
            return new String[]{agentProperties.getLlm().getModel(), agentProperties.getLlm().getType()};
        }
        return new String[]{"default", "default"};
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

    private void markCompleted(String taskUuid, String result, long durationMs, String modelName, String provider,
                               int promptTokens, int completionTokens, int totalTokens) {
        AgentTaskDO task = selectByUuid(taskUuid);
        if (task == null) return;
        if (STATUS_CANCELLED.equals(task.getStatus())) {
            log.info("任务已取消，跳过完成: {}", taskUuid);
            return;
        }
        task.setStatus(STATUS_COMPLETED);
        task.setResult(result);
        task.setDurationMs(durationMs);
        task.setModelName(modelName);
        task.setProvider(provider);
        task.setPromptTokens(promptTokens);
        task.setCompletionTokens(completionTokens);
        task.setTotalTokens(totalTokens);
        task.setUpdateTime(LocalDateTime.now());
        agentTaskMapper.updateById(task);
        progressRegistry.emit(taskUuid, buildEvent(task));
        log.info("Agent 异步任务完成: taskUuid={}, durationMs={}, totalTokens={}", taskUuid, durationMs, totalTokens);
        try {
            notificationEventPublisher.publish(
                    new NotificationEvent(task.getCreateBy(), "TASK",
                            "任务完成: " + taskUuid, "Agent 任务已完成",
                            "INFO", "agent_task", taskUuid, task.getTenantId()));
        } catch (Exception ex) {
            log.warn("发送通知失败(不影响主流程): {}", ex.getMessage());
        }
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
        try {
            String errorMessage = e.getMessage();
            notificationEventPublisher.publish(
                    new NotificationEvent(task.getCreateBy(), "TASK",
                            "任务失败: " + taskUuid, "Agent 任务失败: " + errorMessage,
                            "ERROR", "agent_task", taskUuid, task.getTenantId()));
        } catch (Exception ex) {
            log.warn("发送通知失败(不影响主流程): {}", ex.getMessage());
        }
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
    @Transactional(rollbackFor = Exception.class)
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
        AgentTaskDO task = getTask(taskUuid);

        Sinks.Many<Map<String, Object>> sink = progressRegistry.getSink(taskUuid);
        if (sink != null) {
            return sink.asFlux().timeout(Duration.ofMinutes(10));
        }

        return Flux.just(buildEvent(task));
    }
}
