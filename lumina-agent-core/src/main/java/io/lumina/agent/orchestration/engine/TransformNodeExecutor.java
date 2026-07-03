package io.lumina.agent.orchestration.engine;

import io.lumina.agent.orchestration.expression.ExpressionEvaluator;
import io.lumina.agent.orchestration.model.TransformNode;
import io.lumina.agent.orchestration.model.WorkflowContext;
import io.lumina.agent.orchestration.model.WorkflowNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 数据转换节点执行器
 *
 * <p>支持两种模式：
 * <ul>
 *   <li>{@code transformExpr} — SpEL 表达式，直接对上下文变量求值</li>
 *   <li>{@code template} — 模板字符串，替换 {@code ${var}} 占位符</li>
 * </ul>
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransformNodeExecutor implements NodeExecutor {

    private final ExpressionEvaluator expressionEvaluator;

    @Override
    public boolean supports(WorkflowNode node) {
        return node instanceof TransformNode;
    }

    @Override
    public Object execute(WorkflowNode node, WorkflowContext ctx) {
        TransformNode transformNode = (TransformNode) node;

        if (transformNode.getTransformExpr() != null && !transformNode.getTransformExpr().isBlank()) {
            Object result = expressionEvaluator.evaluate(
                    transformNode.getTransformExpr(), ctx.toEvaluationRoot());
            log.info("数据转换(SpEL): id={}, result={}", node.getId(),
                    result != null ? result.toString().substring(0, Math.min(80, result.toString().length())) : "null");
            return result;
        }

        if (transformNode.getTemplate() != null && !transformNode.getTemplate().isBlank()) {
            String result = resolveTemplate(transformNode.getTemplate(), ctx.toEvaluationRoot());
            log.info("数据转换(模板): id={}", node.getId());
            return result;
        }

        log.warn("数据转换节点无表达式和模板: id={}", node.getId());
        return null;
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
