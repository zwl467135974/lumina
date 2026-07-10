package io.lumina.agent.orchestration.flowable;

import io.lumina.agent.orchestration.expression.ExpressionEvaluator;
import io.lumina.agent.orchestration.model.LoopNode;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 循环节点 ServiceTask Delegate
 *
 * <p>两种模式：
 * <ul>
 *   <li>集合遍历 — 配合 BPMN MultiInstanceLoopCharacteristics，Flowable 自动为每个集合元素创建实例，
 *       此 delegate 每次处理一个元素</li>
 *   <li>条件循环 — delegate 内部用 while 循环重复执行，直到条件为 false 或达到 maxIterations</li>
 * </ul>
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Slf4j
@Component("loopDelegate")
public class LoopServiceTaskDelegate extends AbstractWorkflowDelegate {

    private static final String NODE_RESULT_PREFIX = "__nodeResult_";

    private final ExpressionEvaluator expressionEvaluator;

    public LoopServiceTaskDelegate(ExpressionEvaluator expressionEvaluator) {
        this.expressionEvaluator = expressionEvaluator;
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
        Map<String, Object> variables = execution.getVariables();
        Object result = resolveInput(node.getInput(), variables);

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

            Object result = resolveInput(node.getInput(), execution.getVariables());
            results.add(result);

            if (node.getOutputVar() != null && !node.getOutputVar().isBlank()) {
                execution.setVariable(node.getOutputVar(), result);
            }
            execution.setVariable("_loopIndex", iteration);
            iteration++;
        }

        log.info("条件循环完成: id={}, iterations={}", node.getId(), iteration);
        execution.setVariable(NODE_RESULT_PREFIX + node.getId(), results);
    }

    private Object resolveInput(String inputExpr, Map<String, Object> variables) {
        if (inputExpr == null || inputExpr.isBlank()) {
            return null;
        }
        return expressionEvaluator.evaluate(inputExpr, variables);
    }
}
