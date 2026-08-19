package io.lumina.agent.orchestration.flowable;

import io.lumina.agent.orchestration.engine.AgentExecutionHandler;
import io.lumina.agent.orchestration.expression.ExpressionEvaluator;
import io.lumina.agent.orchestration.model.AutonomyNode;
import io.lumina.agent.orchestration.script.AutonomyScriptEngine;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 自主编排节点 ServiceTask Delegate（Flowable 路径）
 *
 * <p>从 BPMN 扩展元素取出 {@link AutonomyNode} 定义，解析输入表达式，
 * 委托 {@link AutonomyScriptEngine} 沙箱执行 JS 编排脚本，物化结果写入流程变量。
 *
 * @author Lumina Team
 * @since 3.11.0
 */
@Slf4j
@Component("autonomyDelegate")
@ConditionalOnBean(AgentExecutionHandler.class)
public class AutonomyServiceTaskDelegate extends AbstractWorkflowDelegate {

    private final AutonomyScriptEngine scriptEngine;
    private final ExpressionEvaluator expressionEvaluator;

    public AutonomyServiceTaskDelegate(AutonomyScriptEngine scriptEngine,
                                       ExpressionEvaluator expressionEvaluator) {
        this.scriptEngine = scriptEngine;
        this.expressionEvaluator = expressionEvaluator;
    }

    @Override
    public void execute(DelegateExecution execution) {
        AutonomyNode node = getNode(execution, AutonomyNode.class);

        Map<String, Object> variables = execution.getVariables();
        String input = resolveInput(node.getInput(), variables);

        log.info("Flowable autonomy 节点执行: id={}, agentId={}", node.getId(), node.getAgentId());

        Object result = scriptEngine.run(node, input);

        log.info("Flowable autonomy 节点完成: id={}, resultType={}", node.getId(),
                result != null ? result.getClass().getSimpleName() : "null");

        if (node.getOutputVar() != null && !node.getOutputVar().isBlank()) {
            execution.setVariable(node.getOutputVar(), result);
        }
        execution.setVariable("__nodeResult_" + node.getId(), result);
    }

    private String resolveInput(String inputExpr, Map<String, Object> variables) {
        if (inputExpr == null || inputExpr.isBlank()) {
            return "";
        }
        Object resolved = expressionEvaluator.evaluate(inputExpr, variables);
        return resolved != null ? resolved.toString() : "";
    }
}
