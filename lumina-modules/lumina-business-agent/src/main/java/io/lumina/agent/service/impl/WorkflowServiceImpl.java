package io.lumina.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lumina.agent.api.dto.ExecuteWorkflowDTO;
import io.lumina.agent.api.dto.WorkflowDTO;
import io.lumina.agent.api.dto.WorkflowTemplateVO;
import io.lumina.agent.infrastructure.entity.WorkflowDefinitionDO;
import io.lumina.agent.infrastructure.entity.WorkflowExecutionLogDO;
import io.lumina.agent.infrastructure.entity.WorkflowInstanceDO;
import io.lumina.agent.infrastructure.mapper.WorkflowDefinitionMapper;
import io.lumina.agent.infrastructure.mapper.WorkflowExecutionLogMapper;
import io.lumina.agent.infrastructure.mapper.WorkflowInstanceMapper;
import io.lumina.agent.orchestration.engine.WorkflowEngine;
import io.lumina.agent.orchestration.engine.WorkflowEventListener;
import io.lumina.agent.orchestration.loader.WorkflowLoader;
import io.lumina.agent.orchestration.model.WorkflowContext;
import io.lumina.agent.orchestration.model.WorkflowDefinition;
import io.lumina.agent.orchestration.model.WorkflowNode;
import io.lumina.agent.orchestration.model.WorkflowStatus;
import io.lumina.agent.service.WorkflowService;
import io.lumina.common.core.BaseContext;
import io.lumina.common.core.PageResult;
import io.lumina.common.exception.BusinessException;
import io.lumina.common.core.ErrorCode;
import io.lumina.framework.config.RocketMQConfig;
import io.lumina.notification.event.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工作流管理服务实现
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowServiceImpl implements WorkflowService {

    private final WorkflowDefinitionMapper definitionMapper;
    private final WorkflowInstanceMapper instanceMapper;
    private final WorkflowExecutionLogMapper logMapper;
    private final WorkflowLoader workflowLoader;
    private final WorkflowEngine workflowEngine;

    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private RocketMQTemplate rocketMQTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowDefinitionDO create(WorkflowDTO dto) {
        workflowLoader.load(dto.getDefinitionYaml());

        WorkflowDefinitionDO entity = new WorkflowDefinitionDO();
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setDefinitionYaml(dto.getDefinitionYaml());
        entity.setVersion(1);
        entity.setStatus(0);
        entity.setTenantId(BaseContext.getTenantId() != null ? BaseContext.getTenantId() : 0L);
        entity.setCreateBy(BaseContext.getUserId());
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        entity.setIsDeleted(0);
        definitionMapper.insert(entity);

        log.info("工作流创建: id={}, name={}", entity.getId(), entity.getName());
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowDefinitionDO update(Long id, WorkflowDTO dto) {
        WorkflowDefinitionDO entity = getById(id);
        if (Integer.valueOf(1).equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "已发布的工作流不能修改，请新建版本");
        }

        workflowLoader.load(dto.getDefinitionYaml());

        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setDefinitionYaml(dto.getDefinitionYaml());
        entity.setUpdateTime(LocalDateTime.now());
        definitionMapper.updateById(entity);

        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id) {
        WorkflowDefinitionDO entity = getById(id);
        entity.setStatus(1);
        entity.setUpdateTime(LocalDateTime.now());
        definitionMapper.updateById(entity);
        log.info("工作流已发布: id={}, name={}", id, entity.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        getById(id);
        definitionMapper.deleteById(id);
    }

    @Override
    public PageResult<WorkflowDefinitionDO> list(String name, Integer status, int pageNum, int pageSize) {
        Long tenantId = BaseContext.getTenantId() != null ? BaseContext.getTenantId() : 0L;
        LambdaQueryWrapper<WorkflowDefinitionDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WorkflowDefinitionDO::getTenantId, tenantId);
        if (StringUtils.hasText(name)) {
            wrapper.like(WorkflowDefinitionDO::getName, name);
        }
        if (status != null) {
            wrapper.eq(WorkflowDefinitionDO::getStatus, status);
        }
        wrapper.orderByDesc(WorkflowDefinitionDO::getCreateTime);

        Page<WorkflowDefinitionDO> page = definitionMapper.selectPage(
                new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    @Override
    public WorkflowDefinitionDO getById(Long id) {
        WorkflowDefinitionDO entity = definitionMapper.selectById(id);
        if (entity == null || entity.getIsDeleted() == 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "工作流不存在");
        }
        Long currentTenantId = BaseContext.getTenantId() != null ? BaseContext.getTenantId() : 0L;
        if (!currentTenantId.equals(entity.getTenantId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return entity;
    }

    @Override
    public WorkflowInstanceDO execute(Long definitionId, ExecuteWorkflowDTO dto) {
        WorkflowDefinitionDO defDO = getById(definitionId);
        if (defDO.getStatus() != 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "工作流未发布，无法执行");
        }

        WorkflowDefinition definition = workflowLoader.load(defDO.getDefinitionYaml());

        WorkflowInstanceDO instance = new WorkflowInstanceDO();
        instance.setDefinitionId(definitionId);
        instance.setDefinitionName(defDO.getName());
        instance.setDefinitionVersion(defDO.getVersion());
        instance.setStatus(WorkflowStatus.PENDING.name());
        instance.setTenantId(BaseContext.getTenantId() != null ? BaseContext.getTenantId() : 0L);
        instance.setCreateBy(BaseContext.getUserId());
        instance.setCreateTime(LocalDateTime.now());
        instance.setUpdateTime(LocalDateTime.now());
        try {
            instance.setInput(objectMapper.writeValueAsString(dto.getInputs() != null ? dto.getInputs() : "{}"));
        } catch (Exception e) {
            instance.setInput("{}");
        }
        instanceMapper.insert(instance);

        executeWorkflow(definition, instance, dto.getInputs());

        return instance;
    }

    @Override
    public reactor.core.publisher.Flux<java.util.Map<String, Object>> executeStream(Long definitionId, ExecuteWorkflowDTO dto) {
        WorkflowDefinitionDO defDO = getById(definitionId);
        if (defDO.getStatus() != 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "工作流未发布，无法执行");
        }

        WorkflowDefinition definition = workflowLoader.load(defDO.getDefinitionYaml());

        WorkflowInstanceDO instance = new WorkflowInstanceDO();
        instance.setDefinitionId(definitionId);
        instance.setDefinitionName(defDO.getName());
        instance.setDefinitionVersion(defDO.getVersion());
        instance.setStatus(WorkflowStatus.PENDING.name());
        instance.setTenantId(BaseContext.getTenantId() != null ? BaseContext.getTenantId() : 0L);
        instance.setCreateBy(BaseContext.getUserId());
        instance.setCreateTime(LocalDateTime.now());
        instance.setUpdateTime(LocalDateTime.now());
        try {
            instance.setInput(objectMapper.writeValueAsString(dto.getInputs() != null ? dto.getInputs() : "{}"));
        } catch (Exception e) {
            instance.setInput("{}");
        }
        instanceMapper.insert(instance);

        final Long instanceId = instance.getId();
        final java.util.Map<String, Object> inputs = dto.getInputs();
        final io.lumina.common.core.LoginContext loginCtx = BaseContext.current();

        return reactor.core.publisher.Flux.<java.util.Map<String, Object>>create(sink -> {
            WorkflowEventListener sseListener = new WorkflowEventListener() {
                @Override
                public void onNodeStarted(String nodeId, String nodeName, WorkflowContext ctx) {
                    java.util.Map<String, Object> event = new java.util.HashMap<>();
                    event.put("event", "NODE_STARTED");
                    event.put("instanceId", instanceId);
                    event.put("nodeId", nodeId);
                    event.put("nodeName", nodeName != null ? nodeName : "");
                    enrichWithNodeInfo(event, definition, nodeId);
                    sink.next(event);
                }

                @Override
                public void onNodeCompleted(String nodeId, Object result, long durationMs) {
                    java.util.Map<String, Object> event = new java.util.HashMap<>();
                    event.put("event", "NODE_COMPLETED");
                    event.put("instanceId", instanceId);
                    event.put("nodeId", nodeId);
                    event.put("durationMs", durationMs);
                    enrichWithNodeInfo(event, definition, nodeId);
                    try {
                        event.put("result", objectMapper.writeValueAsString(result));
                    } catch (Exception e) {
                        event.put("result", String.valueOf(result));
                    }
                    sink.next(event);
                }

                @Override
                public void onNodeFailed(String nodeId, Throwable error) {
                    java.util.Map<String, Object> event = new java.util.HashMap<>();
                    event.put("event", "NODE_FAILED");
                    event.put("instanceId", instanceId);
                    event.put("nodeId", nodeId);
                    event.put("error", error.getMessage() != null ? error.getMessage() : error.toString());
                    enrichWithNodeInfo(event, definition, nodeId);
                    sink.next(event);
                }

                @Override
                public void onWorkflowCompleted(WorkflowContext ctx) {
                    sink.next(java.util.Map.of(
                            "event", "WORKFLOW_COMPLETED",
                            "instanceId", instanceId,
                            "status", ctx.getStatus().name()
                    ));
                    sink.complete();
                }

                @Override
                public void onWorkflowFailed(WorkflowContext ctx, String error) {
                    sink.next(java.util.Map.of(
                            "event", "WORKFLOW_FAILED",
                            "instanceId", instanceId,
                            "status", ctx.getStatus().name(),
                            "error", error != null ? error : "unknown"
                    ));
                    sink.complete();
                }
            };

            workflowEngine.addListener(sseListener);

            java.util.concurrent.CompletableFuture.runAsync(() -> {
                io.lumina.common.core.BaseContext.setCurrent(loginCtx);
                try {
                    executeWorkflow(definition, instance, inputs);
                } catch (Exception e) {
                    sink.next(java.util.Map.of(
                            "event", "WORKFLOW_FAILED",
                            "instanceId", instanceId,
                            "error", e.getMessage() != null ? e.getMessage() : e.toString()
                    ));
                    sink.complete();
                } finally {
                    workflowEngine.removeListener(sseListener);
                    io.lumina.common.core.BaseContext.clear();
                }
            });
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    /**
     * 从工作流定义中查找节点信息，填充 nodeType 和 agentId 到 SSE 事件中
     */
    @Override
    public WorkflowInstanceDO resumeInstance(Long instanceId, String decision) {
        WorkflowInstanceDO instance = instanceMapper.selectById(instanceId);
        if (instance == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "工作流实例不存在");
        }
        Long currentTenantId = BaseContext.getTenantId() != null ? BaseContext.getTenantId() : 0L;
        if (!currentTenantId.equals(instance.getTenantId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (!"PAUSED".equals(instance.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "工作流实例不在暂停状态，无法恢复");
        }

        WorkflowDefinitionDO defDO = getById(instance.getDefinitionId());
        WorkflowDefinition definition = workflowLoader.load(defDO.getDefinitionYaml());

        WorkflowContext ctx = new WorkflowContext();
        ctx.setWorkflowName(definition.getName());
        ctx.setStatus(WorkflowStatus.RUNNING);
        ctx.setCurrentNodeId(instance.getCurrentNodeId());

        try {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> savedVars = objectMapper.readValue(
                    instance.getOutput() != null ? instance.getOutput() : "{}",
                    java.util.Map.class
            );
            ctx.getVariables().putAll(savedVars);
        } catch (Exception e) {
            log.warn("解析实例变量失败，使用空上下文: {}", e.getMessage());
        }

        instance.setStatus(WorkflowStatus.RUNNING.name());
        instance.setUpdateTime(LocalDateTime.now());
        instanceMapper.updateById(instance);

        ExecutionLogCollector logCollector = new ExecutionLogCollector(instance.getId(), logMapper, objectMapper);

        try {
            workflowEngine.addListener(logCollector);
            WorkflowContext resultCtx = workflowEngine.resume(definition, ctx, decision);

            instance.setStatus(resultCtx.getStatus().name());
            instance.setCurrentNodeId(resultCtx.getCurrentNodeId());

            if (resultCtx.getStatus() == WorkflowStatus.COMPLETED) {
                try {
                    instance.setOutput(objectMapper.writeValueAsString(resultCtx.getVariables()));
                } catch (Exception e) {
                    instance.setOutput("{}");
                }
            } else if (resultCtx.getStatus() == WorkflowStatus.FAILED) {
                instance.setErrorMessage(resultCtx.getErrorMessage());
            }
        } catch (Exception e) {
            log.error("工作流恢复失败: instanceId={}", instanceId, e);
            instance.setStatus(WorkflowStatus.FAILED.name());
            instance.setErrorMessage(e.getMessage());
        } finally {
            instance.setUpdateTime(LocalDateTime.now());
            instanceMapper.updateById(instance);
            logCollector.flush();
            workflowEngine.removeListener(logCollector);
        }

        return instance;
    }

    private void enrichWithNodeInfo(java.util.Map<String, Object> event,
                                     WorkflowDefinition definition, String nodeId) {
        if (definition == null) return;
        try {
            WorkflowNode node = definition.findNode(nodeId);
            if (node != null) {
                event.put("nodeType", resolveNodeType(node));
                if (node instanceof io.lumina.agent.orchestration.model.AgentNode agentNode) {
                    event.put("agentId", agentNode.getAgentId());
                }
            }
        } catch (Exception e) {
            log.debug("节点信息查找失败: nodeId={}", nodeId);
        }
    }

    private String resolveNodeType(WorkflowNode node) {
        String className = node.getClass().getSimpleName();
        if (className.endsWith("Node")) {
            return className.substring(0, className.length() - 4).toLowerCase();
        }
        return className.toLowerCase();
    }

    private void sendWorkflowNotification(WorkflowInstanceDO instance, String category,
                                            String title, String severity) {
        sendWorkflowNotification(instance, category, title, null, severity);
    }

    private void sendWorkflowNotification(WorkflowInstanceDO instance, String category,
                                            String title, String content, String severity) {
        try {
            if (rocketMQTemplate == null) {
                return;
            }
            rocketMQTemplate.convertAndSend(RocketMQConfig.TOPIC_NOTIFICATION,
                    new NotificationEvent(instance.getCreateBy(), category, title,
                            content != null ? content : title, severity,
                            "workflow_instance", String.valueOf(instance.getId()),
                            instance.getTenantId()));
        } catch (Exception ex) {
            log.warn("发送通知失败(不影响主流程): {}", ex.getMessage());
        }
    }

    private void executeWorkflow(WorkflowDefinition definition, WorkflowInstanceDO instance,
                                  java.util.Map<String, Object> inputs) {
        instance.setStatus(WorkflowStatus.RUNNING.name());
        instance.setUpdateTime(LocalDateTime.now());
        instanceMapper.updateById(instance);

        ExecutionLogCollector logCollector = new ExecutionLogCollector(instance.getId(), logMapper, objectMapper);

        try {
            workflowEngine.addListener(logCollector);

            WorkflowContext ctx = workflowEngine.execute(definition, inputs);

            instance.setStatus(ctx.getStatus().name());
            instance.setCurrentNodeId(ctx.getCurrentNodeId());

            if (ctx.getStatus() == WorkflowStatus.COMPLETED) {
                try {
                    instance.setOutput(objectMapper.writeValueAsString(ctx.getVariables()));
                } catch (Exception e) {
                    instance.setOutput("{}");
                }
                sendWorkflowNotification(instance, "WORKFLOW", "工作流执行完成", "INFO");
            } else if (ctx.getStatus() == WorkflowStatus.FAILED) {
                instance.setErrorMessage(ctx.getErrorMessage());
                sendWorkflowNotification(instance, "WORKFLOW", "工作流执行失败",
                        ctx.getErrorMessage() != null ? ctx.getErrorMessage() : "unknown", "ERROR");
            }

        } catch (Exception e) {
            log.error("工作流执行异常: instanceId={}", instance.getId(), e);
            instance.setStatus(WorkflowStatus.FAILED.name());
            instance.setErrorMessage(e.getMessage());
            sendWorkflowNotification(instance, "WORKFLOW", "工作流执行失败",
                    e.getMessage() != null ? e.getMessage() : e.toString(), "ERROR");
        } finally {
            instance.setUpdateTime(LocalDateTime.now());
            instanceMapper.updateById(instance);
            logCollector.flush();
            workflowEngine.removeListener(logCollector);
        }
    }

    @Override
    public PageResult<WorkflowInstanceDO> listInstances(Long definitionId, String status, int pageNum, int pageSize) {
        Long tenantId = BaseContext.getTenantId() != null ? BaseContext.getTenantId() : 0L;
        LambdaQueryWrapper<WorkflowInstanceDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WorkflowInstanceDO::getTenantId, tenantId);
        if (definitionId != null) {
            wrapper.eq(WorkflowInstanceDO::getDefinitionId, definitionId);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(WorkflowInstanceDO::getStatus, status);
        }
        wrapper.orderByDesc(WorkflowInstanceDO::getCreateTime);

        Page<WorkflowInstanceDO> page = instanceMapper.selectPage(
                new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    @Override
    public List<WorkflowExecutionLogDO> getInstanceLogs(Long instanceId) {
        WorkflowInstanceDO instance = instanceMapper.selectById(instanceId);
        if (instance == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "工作流实例不存在");
        }
        Long currentTenantId = BaseContext.getTenantId() != null ? BaseContext.getTenantId() : 0L;
        if (!currentTenantId.equals(instance.getTenantId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        LambdaQueryWrapper<WorkflowExecutionLogDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WorkflowExecutionLogDO::getInstanceId, instanceId);
        wrapper.orderByAsc(WorkflowExecutionLogDO::getCreateTime);
        return logMapper.selectList(wrapper);
    }

    @Override
    public List<WorkflowTemplateVO> getTemplates() {
        String[] templateNames = {
                "supervisor-worker", "pipeline", "router", "human-in-the-loop", "debate",
                "plan-execute", "group-chat"
        };

        List<WorkflowTemplateVO> templates = new java.util.ArrayList<>();
        for (String name : templateNames) {
            try {
                String path = "workflow-templates/" + name + ".yaml";
                org.springframework.core.io.ClassPathResource resource =
                        new org.springframework.core.io.ClassPathResource(path);
                if (resource.exists()) {
                    try (var is = resource.getInputStream()) {
                        String yaml = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                        // 含占位符的模板需先替换为临时值才能通过 YAML 校验
                        String yamlForValidation = yaml.replaceAll("\\$\\{\\w+\\}", "1");
                        io.lumina.agent.orchestration.model.WorkflowDefinition def = workflowLoader.load(yamlForValidation);
                        WorkflowTemplateVO vo = new WorkflowTemplateVO();
                        vo.setName(def.getName());
                        vo.setDescription(def.getDescription());
                        vo.setDefinitionYaml(yaml);
                        vo.setRequiredAgents(extractRequiredAgents(yaml));
                        templates.add(vo);
                    }
                }
            } catch (Exception e) {
                log.warn("加载模板失败: {}", name, e);
            }
        }
        return templates;
    }

    /**
     * 从 YAML 中提取占位符 agent 角色信息
     */
    private List<WorkflowTemplateVO.AgentRole> extractRequiredAgents(String yaml) {
        List<WorkflowTemplateVO.AgentRole> roles = new java.util.ArrayList<>();
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\$\\{(\\w+)\\}");
        java.util.Set<String> seen = new java.util.HashSet<>();
        java.util.regex.Matcher matcher = pattern.matcher(yaml);
        while (matcher.find()) {
            String placeholder = matcher.group(1);
            if (seen.add(placeholder)) {
                WorkflowTemplateVO.AgentRole role = new WorkflowTemplateVO.AgentRole();
                role.setPlaceholder(placeholder);
                role.setDescription(getAgentRoleDescription(placeholder));
                roles.add(role);
            }
        }
        return roles;
    }

    private String getAgentRoleDescription(String placeholder) {
        return switch (placeholder) {
            case "agent1" -> "主 Agent（Planner / 主管 / 发起者）";
            case "agent2" -> "执行 Agent（Worker / 讨论者 B）";
            case "agent3" -> "辅助 Agent（汇总 / 主持人）";
            default -> "Agent 角色";
        };
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowDefinitionDO createFromTemplate(String templateName, String workflowName,
                                                    java.util.Map<String, Long> agentMapping) {
        log.info("从模板创建工作流: template={}, name={}, agentMapping={}", templateName, workflowName, agentMapping);

        // 读取模板 YAML
        String path = "workflow-templates/" + templateName + ".yaml";
        org.springframework.core.io.ClassPathResource resource =
                new org.springframework.core.io.ClassPathResource(path);
        if (!resource.exists()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "模板不存在: " + templateName);
        }

        String yaml;
        try (var is = resource.getInputStream()) {
            yaml = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "读取模板失败: " + templateName, e);
        }

        // 替换占位符 ${agent1} → 实际 agentId
        if (agentMapping != null) {
            for (var entry : agentMapping.entrySet()) {
                if (entry.getValue() == null) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST,
                            "Agent 映射缺少 ID: " + entry.getKey());
                }
                yaml = yaml.replace("${" + entry.getKey() + "}", String.valueOf(entry.getValue()));
            }
        }

        // 校验：检查是否还有未替换的占位符
        java.util.regex.Pattern leftover = java.util.regex.Pattern.compile("\\$\\{\\w+\\}");
        java.util.regex.Matcher m = leftover.matcher(yaml);
        if (m.find()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "模板中有未映射的占位符: " + m.group() + "，请补全 agentMapping");
        }

        // 校验 YAML 合法性
        try {
            workflowLoader.load(yaml);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "模板 YAML 校验失败: " + e.getMessage(), e);
        }

        // 创建工作流（草稿）
        WorkflowDTO dto = new WorkflowDTO();
        dto.setName(workflowName);
        io.lumina.agent.orchestration.model.WorkflowDefinition def = workflowLoader.load(yaml);
        dto.setDescription(def.getDescription());
        dto.setDefinitionYaml(yaml);

        WorkflowDefinitionDO entity = create(dto);

        // 自动发布
        publish(entity.getId());

        log.info("从模板创建并发布工作流成功: id={}, name={}", entity.getId(), workflowName);
        return entity;
    }

    /**
     * 执行日志收集器（作为 WorkflowEventListener 收集节点执行日志）
     */
    @Slf4j
    @RequiredArgsConstructor
    static class ExecutionLogCollector implements WorkflowEventListener {

        private final Long instanceId;
        private final WorkflowExecutionLogMapper logMapper;
        private final ObjectMapper objectMapper;
        private final List<WorkflowExecutionLogDO> pending = new java.util.ArrayList<>();

        @Override
        public void onNodeStarted(String nodeId, String nodeName, WorkflowContext ctx) {
            WorkflowExecutionLogDO logDO = new WorkflowExecutionLogDO();
            logDO.setInstanceId(instanceId);
            logDO.setNodeId(nodeId);
            logDO.setNodeName(nodeName);
            logDO.setStatus("RUNNING");
            logDO.setCreateTime(LocalDateTime.now());
            pending.add(logDO);
        }

        @Override
        public void onNodeCompleted(String nodeId, Object result, long durationMs) {
            WorkflowExecutionLogDO logDO = findPending(nodeId);
            if (logDO != null) {
                logDO.setStatus("COMPLETED");
                logDO.setDurationMs((int) durationMs);
                try {
                    logDO.setOutput(objectMapper.writeValueAsString(result));
                } catch (Exception e) {
                    logDO.setOutput(result != null ? result.toString() : "null");
                }
            }
        }

        @Override
        public void onNodeFailed(String nodeId, Throwable error) {
            WorkflowExecutionLogDO logDO = findPending(nodeId);
            if (logDO != null) {
                logDO.setStatus("FAILED");
                logDO.setErrorMessage(error.getMessage());
            }
        }

        private WorkflowExecutionLogDO findPending(String nodeId) {
            return pending.stream()
                    .filter(l -> l.getNodeId().equals(nodeId) && "RUNNING".equals(l.getStatus()))
                    .reduce((first, second) -> second)
                    .orElse(null);
        }

        void flush() {
            for (WorkflowExecutionLogDO logDO : pending) {
                if ("RUNNING".equals(logDO.getStatus())) {
                    logDO.setStatus("SKIPPED");
                }
                try {
                    logMapper.insert(logDO);
                } catch (Exception e) {
                    log.error("保存执行日志失败: nodeId={}", logDO.getNodeId(), e);
                }
            }
        }
    }
}
