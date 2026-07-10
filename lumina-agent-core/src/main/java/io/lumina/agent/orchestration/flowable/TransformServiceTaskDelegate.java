package io.lumina.agent.orchestration.flowable;

import io.lumina.agent.orchestration.expression.ExpressionEvaluator;
import io.lumina.agent.orchestration.model.TransformNode;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 数据转换 ServiceTask Delegate
 *
 * <p>支持两种模式：
 * <ul>
 *   <li>{@code transformExpr} — SpEL 表达式直接求值</li>
 *   <li>{@code template} — {@code ${var}} 占位符模板替换</li>
 * </ul>
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Slf4j
@Component("transformDelegate")
public class TransformServiceTaskDelegate extends AbstractWorkflowDelegate {

    private final ExpressionEvaluator expressionEvaluator;

    public TransformServiceTaskDelegate(ExpressionEvaluator expressionEvaluator) {
        this.expressionEvaluator = expressionEvaluator;
    }

    @Override
    public void execute(DelegateExecution execution) {
        TransformNode node = getNode(execution, TransformNode.class);

        Map<String, Object> variables = execution.getVariables();
        Object result;

        if (node.getTransformExpr() != null && !node.getTransformExpr().isBlank()) {
            result = expressionEvaluator.evaluate(node.getTransformExpr(), variables);
            log.info("Flowable 数据转换(SpEL): id={}", node.getId());
        } else if (node.getTemplate() != null && !node.getTemplate().isBlank()) {
            result = resolveTemplate(node.getTemplate(), variables);
            log.info("Flowable 数据转换(模板): id={}", node.getId());
        } else {
            log.warn("Flowable 数据转换节点无表达式和模板: id={}", node.getId());
            result = null;
        }

        if (node.getOutputVar() != null && !node.getOutputVar().isBlank()) {
            execution.setVariable(node.getOutputVar(), result);
        }
        execution.setVariable("__nodeResult_" + node.getId(), result);
    }

    private String resolveTemplate(String template, Map<String, Object> variables) {
        String result = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            result = result.replace("${" + entry.getKey() + "}",
                    entry.getValue() != null ? entry.getValue().toString() : "");
        }
        return result;
    }
}
