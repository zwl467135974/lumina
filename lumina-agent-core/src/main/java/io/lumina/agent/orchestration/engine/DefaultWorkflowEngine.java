package io.lumina.agent.orchestration.engine;

import io.lumina.agent.orchestration.expression.ExpressionEvaluator;
import io.lumina.agent.orchestration.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 默认工作流引擎实现
 *
 * <p>基于 DAG（有向无环图）拓扑遍历执行节点。支持：
 * <ul>
 *   <li>顺序执行（沿 edge 连接）</li>
 *   <li>条件路由（{@link ConditionNode} 返回路由指令）</li>
 *   <li>并行执行（{@link ParallelNode} 多分支 fan-out）</li>
 *   <li>循环执行（{@link LoopNode} 集合遍历 / 条件循环）</li>
 *   <li>人工暂停（{@link HumanNode} 抛异常暂停）</li>
 * </ul>
 *
 * <p>执行策略：
 * <ol>
 *   <li>找到起始节点（无入边）</li>
 *   <li>依次执行，节点结果存入 {@code outputVar} → 上下文变量</li>
 *   <li>条件节点返回 {@code "route:targetId"}，引擎跳转到指定节点</li>
 *   <li>其他节点沿默认边（无条件 edge）继续</li>
 * </ol>
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Slf4j
@Component
public class DefaultWorkflowEngine implements WorkflowEngine {

    private final List<NodeExecutor> executors;
    private final ExpressionEvaluator expressionEvaluator;
    private final List<WorkflowEventListener> listeners = new ArrayList<>();

    public DefaultWorkflowEngine(List<NodeExecutor> executors, ExpressionEvaluator expressionEvaluator) {
        this.executors = executors;
        this.expressionEvaluator = expressionEvaluator;
    }

    @Override
    public WorkflowContext execute(WorkflowDefinition definition, Map<String, Object> inputs) {
        WorkflowContext ctx = new WorkflowContext();
        ctx.setWorkflowName(definition.getName());
        ctx.setStatus(WorkflowStatus.RUNNING);

        if (inputs != null) {
            ctx.getVariables().putAll(inputs);
        }

        try {
            List<WorkflowNode> startNodes = definition.getStartNodes();
            if (startNodes.isEmpty()) {
                throw new IllegalStateException("工作流没有起始节点: " + definition.getName());
            }

            String currentNodeId = startNodes.get(0).getId();
            Set<String> visited = new HashSet<>();
            int maxSteps = definition.getNodes().size() * 10 + 100;

            while (currentNodeId != null) {
                if (visited.contains(currentNodeId)) {
                    log.warn("检测到循环，跳过已访问节点: {}", currentNodeId);
                    break;
                }
                if (maxSteps-- <= 0) {
                    log.error("工作流执行步数超限，强制终止");
                    break;
                }

                visited.add(currentNodeId);
                WorkflowNode node = definition.findNode(currentNodeId);
                if (node == null) {
                    log.error("节点不存在: {}", currentNodeId);
                    break;
                }

                currentNodeId = executeNode(definition, node, ctx);
            }

            evaluateOutputs(definition, ctx);
            ctx.setStatus(WorkflowStatus.COMPLETED);
            listeners.forEach(l -> l.onWorkflowCompleted(ctx));

        } catch (HumanNodeExecutor.HumanApprovalRequiredException e) {
            ctx.setStatus(WorkflowStatus.PAUSED);
            ctx.setVariable(e.getDecisionVar(), "__WAITING__");
            ctx.setCurrentNodeId(e.getNodeId());
            log.info("工作流暂停等待人工审批: node={}", e.getNodeId());

        } catch (Exception e) {
            ctx.setStatus(WorkflowStatus.FAILED);
            ctx.setErrorMessage(e.getMessage());
            listeners.forEach(l -> l.onWorkflowFailed(ctx, e.getMessage()));
            log.error("工作流执行失败: {}", definition.getName(), e);
        }

        return ctx;
    }

    private String executeNode(WorkflowDefinition definition, WorkflowNode node, WorkflowContext ctx) {
        ctx.setCurrentNodeId(node.getId());
        ctx.getNodeStatuses().put(node.getId(), WorkflowNodeStatus.RUNNING);
        listeners.forEach(l -> l.onNodeStarted(node.getId(), node.getName(), ctx));

        NodeExecutor executor = findExecutor(node);
        if (executor == null) {
            throw new IllegalStateException("找不到节点执行器: " + node.getClass().getSimpleName());
        }

        long start = System.currentTimeMillis();
        try {
            Object result = executor.execute(node, ctx);
            long duration = System.currentTimeMillis() - start;

            if (node.getOutputVar() != null && !node.getOutputVar().isBlank()) {
                ctx.setVariable(node.getOutputVar(), result);
            }
            ctx.setNodeResult(node.getId(), result);
            ctx.getNodeStatuses().put(node.getId(), WorkflowNodeStatus.COMPLETED);
            listeners.forEach(l -> l.onNodeCompleted(node.getId(), result, duration));

            return determineNextNode(definition, node, result, ctx);

        } catch (HumanNodeExecutor.HumanApprovalRequiredException e) {
            throw e;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            ctx.getNodeStatuses().put(node.getId(), WorkflowNodeStatus.FAILED);
            listeners.forEach(l -> l.onNodeFailed(node.getId(), e));
            log.error("节点执行失败: id={}, durationMs={}", node.getId(), duration, e);
            throw new RuntimeException("节点执行失败: " + node.getId(), e);
        }
    }

    private String determineNextNode(WorkflowDefinition definition, WorkflowNode node, Object result, WorkflowContext ctx) {
        if (result instanceof String str && str.startsWith(ConditionNodeExecutor.ROUTE_PREFIX)) {
            return str.substring(ConditionNodeExecutor.ROUTE_PREFIX.length());
        }

        List<WorkflowEdge> outgoing = definition.getOutgoingEdges(node.getId());
        for (WorkflowEdge edge : outgoing) {
            if (edge.getCondition() == null || edge.getCondition().isBlank()) {
                return edge.getTo();
            }
            if (expressionEvaluator.evaluateBoolean(edge.getCondition(), ctx.toEvaluationRoot())) {
                return edge.getTo();
            }
        }

        if (node instanceof ConditionNode condNode) {
            if (result instanceof String str && str.startsWith(ConditionNodeExecutor.ROUTE_PREFIX)) {
                return str.substring(ConditionNodeExecutor.ROUTE_PREFIX.length());
            }
        }

        return null;
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

    private NodeExecutor findExecutor(WorkflowNode node) {
        return executors.stream()
                .filter(e -> e.supports(node))
                .findFirst()
                .orElse(null);
    }

    @Override
    public void addListener(WorkflowEventListener listener) {
        listeners.add(listener);
    }
}
