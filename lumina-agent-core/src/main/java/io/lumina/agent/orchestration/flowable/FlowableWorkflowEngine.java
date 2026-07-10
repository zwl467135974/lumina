package io.lumina.agent.orchestration.flowable;

import io.lumina.agent.orchestration.engine.WorkflowEngine;
import io.lumina.agent.orchestration.engine.WorkflowEventListener;
import io.lumina.agent.orchestration.expression.ExpressionEvaluator;
import io.lumina.agent.orchestration.model.HumanNode;
import io.lumina.agent.orchestration.model.WorkflowContext;
import io.lumina.agent.orchestration.model.WorkflowDefinition;
import io.lumina.agent.orchestration.model.WorkflowNode;
import io.lumina.agent.orchestration.model.WorkflowNodeStatus;
import io.lumina.agent.orchestration.model.WorkflowStatus;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.FlowNode;
import org.flowable.bpmn.model.FlowableListener;
import org.flowable.bpmn.model.Process;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.delegate.ExecutionListener;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.variable.api.history.HistoricVariableInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 基于 Flowable BPMN 引擎的工作流执行器
 *
 * <p>实现 {@link WorkflowEngine} 接口，将 Lumina YAML 工作流定义转换为 BPMN 模型，
 * 部署到 Flowable 引擎同步执行。相比 {@link io.lumina.agent.orchestration.engine.DefaultWorkflowEngine}，
 * Flowable 提供成熟的流程 semantics（网关、并行、多实例）和持久化能力。
 *
 * <h3>执行流程</h3>
 * <ol>
 *   <li>{@link FlowableBpmnConverter} 将 {@link WorkflowDefinition} 转为 {@link BpmnModel}</li>
 *   <li>给每个节点元素附加 {@link WorkflowEventBridge} 执行监听器</li>
 *   <li>部署模型并按 process key 启动流程实例</li>
 *   <li>同步执行 ServiceTask（Agent / Transform）；遇到 UserTask 自动暂停</li>
 *   <li>收集流程变量映射回 {@link WorkflowContext}</li>
 * </ol>
 *
 * <h3>变量管理</h3>
 * <ul>
 *   <li>输入变量 → 流程启动参数</li>
 *   <li>节点结果 → {@code __nodeResult_{nodeId}} 流程变量</li>
 *   <li>输出映射 → 流程完成后 SpEL 求值</li>
 *   <li>{@code __} 前缀的内部变量在映射回上下文时自动过滤</li>
 * </ul>
 *
 * <h3>人工审批暂停 / 恢复</h3>
 * <p>遇到 UserTask 时流程暂停，{@link WorkflowContext#getStatus()} 设为 {@link WorkflowStatus#PAUSED}。
 * 流程实例 ID 和任务 ID 存入上下文变量（{@code __flowable_pid} / {@code __flowable_taskId}），
 * {@link #resume} 方法据此完成 UserTask 并恢复执行。
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Slf4j
@Component
@Primary
public class FlowableWorkflowEngine implements WorkflowEngine {

    private static final String VAR_PROCESS_INSTANCE_ID = "__flowable_pid";
    private static final String VAR_TASK_ID = "__flowable_taskId";
    private static final String NODE_RESULT_PREFIX = "__nodeResult_";
    private static final String INTERNAL_PREFIX = "__";

    private final RepositoryService repositoryService;
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;
    private final ExpressionEvaluator expressionEvaluator;
    private final FlowableBpmnConverter converter;
    private final List<WorkflowEventListener> listeners = new CopyOnWriteArrayList<>();

    @Autowired(required = false)
    private io.micrometer.core.instrument.MeterRegistry meterRegistry;

    public FlowableWorkflowEngine(
            RepositoryService repositoryService,
            RuntimeService runtimeService,
            TaskService taskService,
            HistoryService historyService,
            ExpressionEvaluator expressionEvaluator) {
        this.repositoryService = repositoryService;
        this.runtimeService = runtimeService;
        this.taskService = taskService;
        this.historyService = historyService;
        this.expressionEvaluator = expressionEvaluator;
        this.converter = new FlowableBpmnConverter();
    }

    @Override
    public WorkflowContext execute(WorkflowDefinition definition, Map<String, Object> inputs) {
        long startTime = System.currentTimeMillis();
        WorkflowContext ctx = new WorkflowContext();
        ctx.setWorkflowName(definition.getName());
        ctx.setStatus(WorkflowStatus.RUNNING);

        if (inputs != null) {
            ctx.getVariables().putAll(inputs);
        }

        try {
            BpmnModel model = converter.convert(definition);
            attachEventListeners(model);

            repositoryService.createDeployment()
                    .name(definition.getName())
                    .addBpmnModel(definition.getName() + ".bpmn", model)
                    .deploy();

            Map<String, Object> processVars = new HashMap<>();
            if (inputs != null) {
                processVars.putAll(inputs);
            }

            setupBridge(ctx);

            String processKey = sanitizeId(definition.getName());
            ProcessInstance instance = runtimeService.startProcessInstanceByKey(
                    processKey, processVars);

            ctx.setVariable(VAR_PROCESS_INSTANCE_ID, instance.getId());
            handleProcessOutcome(definition, ctx, instance.getId());

        } catch (Exception e) {
            handleFailure(definition, ctx, e);
        } finally {
            cleanupBridge();
        }

        recordTimer(definition.getName(), ctx.getStatus().name(),
                System.currentTimeMillis() - startTime);
        return ctx;
    }

    @Override
    public WorkflowContext resume(WorkflowDefinition definition, WorkflowContext pausedCtx, String decision) {
        long startTime = System.currentTimeMillis();
        String processInstanceId = pausedCtx.getVariable(VAR_PROCESS_INSTANCE_ID);
        String taskId = pausedCtx.getVariable(VAR_TASK_ID);

        if (processInstanceId == null || taskId == null) {
            throw new IllegalStateException("上下文缺少 Flowable 流程实例信息，无法恢复");
        }

        WorkflowNode node = definition.findNode(pausedCtx.getCurrentNodeId());
        String decisionVar = "decision";
        if (node instanceof HumanNode humanNode && humanNode.getDecisionVar() != null) {
            decisionVar = humanNode.getDecisionVar();
        }

        pausedCtx.setStatus(WorkflowStatus.RUNNING);

        try {
            setupBridge(pausedCtx);
            taskService.complete(taskId, Map.of(decisionVar, decision));
            handleProcessOutcome(definition, pausedCtx, processInstanceId);

        } catch (Exception e) {
            handleFailure(definition, pausedCtx, e);
        } finally {
            cleanupBridge();
        }

        recordTimer(definition.getName(), pausedCtx.getStatus().name(),
                System.currentTimeMillis() - startTime);
        return pausedCtx;
    }

    private void handleProcessOutcome(WorkflowDefinition definition, WorkflowContext ctx, String processInstanceId) {
        List<Task> tasks = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .list();

        if (!tasks.isEmpty()) {
            Task task = tasks.get(0);
            ctx.setVariable(VAR_TASK_ID, task.getId());
            ctx.setCurrentNodeId(task.getTaskDefinitionKey());
            ctx.setStatus(WorkflowStatus.PAUSED);
            ctx.getNodeStatuses().put(task.getTaskDefinitionKey(), WorkflowNodeStatus.WAITING);
            collectVariablesFromRuntime(ctx, processInstanceId);
            log.info("工作流暂停等待人工审批: pid={}, taskId={}, node={}",
                    processInstanceId, task.getId(), task.getTaskDefinitionKey());
        } else {
            collectVariablesFromHistory(ctx, processInstanceId);
            evaluateOutputs(definition, ctx);
            ctx.setStatus(WorkflowStatus.COMPLETED);
            listeners.forEach(l -> l.onWorkflowCompleted(ctx));
        }
    }

    private void collectVariablesFromRuntime(WorkflowContext ctx, String processInstanceId) {
        Map<String, Object> vars = runtimeService.getVariables(processInstanceId);
        mergeVariables(ctx, vars);
    }

    private void collectVariablesFromHistory(WorkflowContext ctx, String processInstanceId) {
        List<HistoricVariableInstance> historicVars = historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(processInstanceId)
                .list();

        Map<String, Object> vars = new LinkedHashMap<>();
        for (HistoricVariableInstance hv : historicVars) {
            vars.put(hv.getVariableName(), hv.getValue());
        }
        mergeVariables(ctx, vars);
    }

    private void mergeVariables(WorkflowContext ctx, Map<String, Object> vars) {
        for (Map.Entry<String, Object> entry : vars.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(NODE_RESULT_PREFIX)) {
                String nodeId = key.substring(NODE_RESULT_PREFIX.length());
                ctx.setNodeResult(nodeId, entry.getValue());
                ctx.getNodeStatuses().putIfAbsent(nodeId, WorkflowNodeStatus.COMPLETED);
            } else if (!key.startsWith(INTERNAL_PREFIX)) {
                ctx.setVariable(key, entry.getValue());
            }
        }
    }

    private void evaluateOutputs(WorkflowDefinition definition, WorkflowContext ctx) {
        if (definition.getOutputs() == null) return;
        for (WorkflowDefinition.MapEntry entry : definition.getOutputs()) {
            if (entry.getValue() != null && !entry.getValue().isBlank()) {
                Object value = expressionEvaluator.evaluate(entry.getValue(), ctx.toEvaluationRoot());
                ctx.setVariable(entry.getKey(), value);
            }
        }
    }

    private void handleFailure(WorkflowDefinition definition, WorkflowContext ctx, Exception e) {
        Map<String, Long> startTimes = WorkflowEventBridge.START_TIMES.get();
        if (startTimes != null && !startTimes.isEmpty()) {
            for (String nodeId : new ArrayList<>(startTimes.keySet())) {
                ctx.getNodeStatuses().put(nodeId, WorkflowNodeStatus.FAILED);
                listeners.forEach(l -> l.onNodeFailed(nodeId, e));
            }
        }
        ctx.setStatus(WorkflowStatus.FAILED);
        ctx.setErrorMessage(e.getMessage());
        listeners.forEach(l -> l.onWorkflowFailed(ctx, e.getMessage()));
        log.error("工作流执行失败: {}", definition.getName(), e);
    }

    private void attachEventListeners(BpmnModel model) {
        for (Process process : model.getProcesses()) {
            for (FlowElement element : process.getFlowElements()) {
                if (!(element instanceof FlowNode flowNode)) {
                    continue;
                }
                Map<String, List<ExtensionElement>> exts = flowNode.getExtensionElements();
                if (exts == null || !exts.containsKey(FlowableBpmnConverter.NODE_DEFINITION_EXT)) {
                    continue;
                }

                FlowableListener startListener = new FlowableListener();
                startListener.setEvent(ExecutionListener.EVENTNAME_START);
                startListener.setImplementation("${workflowEventBridge}");
                startListener.setImplementationType("delegateExpression");
                flowNode.getExecutionListeners().add(startListener);

                FlowableListener endListener = new FlowableListener();
                endListener.setEvent(ExecutionListener.EVENTNAME_END);
                endListener.setImplementation("${workflowEventBridge}");
                endListener.setImplementationType("delegateExpression");
                flowNode.getExecutionListeners().add(endListener);
            }
        }
    }

    private void setupBridge(WorkflowContext ctx) {
        WorkflowEventBridge.CTX_HOLDER.set(ctx);
        WorkflowEventBridge.LISTENERS_HOLDER.set(new ArrayList<>(listeners));
        WorkflowEventBridge.START_TIMES.set(new HashMap<>());
    }

    private void cleanupBridge() {
        WorkflowEventBridge.CTX_HOLDER.remove();
        WorkflowEventBridge.LISTENERS_HOLDER.remove();
        WorkflowEventBridge.START_TIMES.remove();
    }

    private String sanitizeId(String raw) {
        if (raw == null || raw.isBlank()) return "process";
        return raw.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private void recordTimer(String name, String result, long durationMs) {
        if (meterRegistry != null) {
            meterRegistry.timer("workflow.execution.duration", "name", name, "result", result)
                    .record(Duration.ofMillis(durationMs));
        }
    }

    @Override
    public void addListener(WorkflowEventListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(WorkflowEventListener listener) {
        listeners.remove(listener);
    }
}
