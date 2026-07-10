package io.lumina.agent.orchestration.flowable;

import io.lumina.agent.orchestration.engine.NodeExecutor;
import io.lumina.agent.orchestration.expression.ExpressionEvaluator;
import io.lumina.agent.orchestration.model.LoopNode;
import io.lumina.agent.orchestration.model.WorkflowContext;
import io.lumina.agent.orchestration.model.WorkflowDefinition;
import io.lumina.agent.orchestration.model.WorkflowEdge;
import io.lumina.agent.orchestration.model.WorkflowNode;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 循环节点 ServiceTask Delegate
 *
 * <p>两种模式：
 * <ul>
 *   <li>集合遍历 — 配合 BPMN MultiInstanceLoopCharacteristics，Flowable 自动为每个集合元素创建实例，
 *       此 delegate 每次处理一个元素并执行 loopTarget 子链</li>
 *   <li>条件循环 — delegate 内部用 while 循环重复执行 loopTarget 子链，直到条件为 false 或达到 maxIterations</li>
 * </ul>
 *
 * <p>每轮迭代通过 {@link NodeExecutor} 执行 loopTarget 指向的子图节点链，
 * 变量在 Flowable 流程变量与 {@link WorkflowContext} 之间桥接。
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Slf4j
@Component("loopDelegate")
public class LoopServiceTaskDelegate extends AbstractWorkflowDelegate {

    private static final String NODE_RESULT_PREFIX = "__nodeResult_";

    private final ExpressionEvaluator expressionEvaluator;
    private final List<NodeExecutor> executors;

    public LoopServiceTaskDelegate(ExpressionEvaluator expressionEvaluator,
                                    List<NodeExecutor> executors) {
        this.expressionEvaluator = expressionEvaluator;
        this.executors = executors != null ? executors : List.of();
    }

    @Override
    public void execute(DelegateExecution execution) {
        LoopNode node = getNode(execution, LoopNode.class);

        if (node.getIterateVar() != null && !node.getIterateVar().isBlank()) {
            executeIteration(node, execution);
        } else if (node.getConditionExpr() != null && !node.getConditionExpr().isBlank()) {
            executeConditionLoop(node, execution);
        } else {
            log.warn("循环节点无遍历变量和条件表达式: id={}", node.getId());
        }
    }

    private void executeIteration(LoopNode node, DelegateExecution execution) {
        Object result = executeLoopTargetChain(node, execution);

        if (node.getOutputVar() != null && !node.getOutputVar().isBlank()) {
            execution.setVariableLocal(node.getOutputVar(), result);
        }
        execution.setVariableLocal(NODE_RESULT_PREFIX + node.getId(), result);

        log.debug("循环迭代执行: id={}, itemVar={}", node.getId(), node.getItemVar());
    }

    private void executeConditionLoop(LoopNode node, DelegateExecution execution) {
        List<Object> results = new ArrayList<>();
        int iteration = 0;

        while (iteration < node.getMaxIterations()) {
            boolean shouldContinue = expressionEvaluator.evaluateBoolean(
                    node.getConditionExpr(), execution.getVariables());
            if (!shouldContinue) {
                break;
            }

            execution.setVariableLocal("_loopIndex", iteration);
            Object result = executeLoopTargetChain(node, execution);
            results.add(result);

            if (node.getOutputVar() != null && !node.getOutputVar().isBlank()) {
                execution.setVariableLocal(node.getOutputVar(), result);
            }
            iteration++;
        }

        log.info("条件循环完成: id={}, iterations={}", node.getId(), iteration);
        execution.setVariableLocal(NODE_RESULT_PREFIX + node.getId(), results);
    }

    private Object executeLoopTargetChain(LoopNode node, DelegateExecution execution) {
        if (node.getLoopTarget() == null || node.getLoopTarget().isBlank()) {
            Object result = resolveInput(node.getInput(), execution.getVariables());
            if (node.getOutputVar() != null && !node.getOutputVar().isBlank()) {
                execution.setVariableLocal(node.getOutputVar(), result);
            }
            execution.setVariableLocal(NODE_RESULT_PREFIX + node.getId(), result);
            return result;
        }

        WorkflowDefinition definition = getWorkflowDefinition(execution);
        if (definition == null) {
            log.warn("无法获取工作流定义，跳过 loopTarget 子链执行: id={}", node.getId());
            Object result = resolveInput(node.getInput(), execution.getVariables());
            execution.setVariableLocal(NODE_RESULT_PREFIX + node.getId(), result);
            return result;
        }

        WorkflowContext ctx = new WorkflowContext();
        ctx.setWorkflowName(definition.getName());
        ctx.getVariables().putAll(execution.getVariables());

        String currentNodeId = node.getLoopTarget();
        Set<String> visited = new HashSet<>();
        Object lastResult = null;

        while (currentNodeId != null && !visited.contains(currentNodeId)) {
            visited.add(currentNodeId);
            WorkflowNode chainNode = definition.findNode(currentNodeId);
            if (chainNode == null) {
                break;
            }

            NodeExecutor executor = findExecutor(chainNode);
            if (executor != null) {
                try {
                    lastResult = executor.execute(chainNode, ctx);
                } catch (Exception e) {
                    throw new RuntimeException("循环节点子链执行失败: " + chainNode.getId(), e);
                }
                if (chainNode.getOutputVar() != null && !chainNode.getOutputVar().isBlank()) {
                    ctx.setVariable(chainNode.getOutputVar(), lastResult);
                }
                ctx.setNodeResult(chainNode.getId(), lastResult);
            }

            currentNodeId = determineNextInChain(definition, currentNodeId, ctx);
        }

        for (Map.Entry<String, Object> entry : ctx.getVariables().entrySet()) {
            execution.setVariable(entry.getKey(), entry.getValue());
        }

        execution.setVariableLocal(NODE_RESULT_PREFIX + node.getId(), lastResult);

        return lastResult;
    }

    private String determineNextInChain(WorkflowDefinition definition, String nodeId, WorkflowContext ctx) {
        List<WorkflowEdge> outgoing = definition.getOutgoingEdges(nodeId);
        for (WorkflowEdge edge : outgoing) {
            if (edge.getCondition() == null || edge.getCondition().isBlank()) {
                return edge.getTo();
            }
            if (expressionEvaluator.evaluateBoolean(edge.getCondition(), ctx.toEvaluationRoot())) {
                return edge.getTo();
            }
        }
        return null;
    }

    private NodeExecutor findExecutor(WorkflowNode node) {
        return executors.stream()
                .filter(e -> e.supports(node))
                .findFirst()
                .orElse(null);
    }

    private Object resolveInput(String inputExpr, Map<String, Object> variables) {
        if (inputExpr == null || inputExpr.isBlank()) {
            return null;
        }
        return expressionEvaluator.evaluate(inputExpr, variables);
    }
}
