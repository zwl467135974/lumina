package io.lumina.agent.orchestration.flowable;

import io.lumina.agent.orchestration.engine.AgentExecutionHandler;
import io.lumina.agent.orchestration.expression.ExpressionEvaluator;
import io.lumina.agent.orchestration.model.AgentNode;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Agent 执行 ServiceTask Delegate
 *
 * <p>Flowable ServiceTask 以 {@code delegateExpression="${agentDelegate}"} 引用此 Bean。
 * 执行时从 BPMN 扩展元素取出 {@link AgentNode} 定义，解析输入表达式，
 * 委托 {@link AgentExecutionHandler} 执行 Agent，结果写入流程变量。
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Slf4j
@Component("agentDelegate")
@ConditionalOnBean(AgentExecutionHandler.class)
public class AgentServiceTaskDelegate extends AbstractWorkflowDelegate {

    private final AgentExecutionHandler agentHandler;
    private final ExpressionEvaluator expressionEvaluator;

    public AgentServiceTaskDelegate(AgentExecutionHandler agentHandler,
                                    ExpressionEvaluator expressionEvaluator) {
        this.agentHandler = agentHandler;
        this.expressionEvaluator = expressionEvaluator;
    }

    @Override
    public void execute(DelegateExecution execution) {
        AgentNode node = getNode(execution, AgentNode.class);

        Map<String, Object> variables = execution.getVariables();
        String task = resolveInput(node.getInput(), variables);

        log.info("Flowable Agent 节点执行: id={}, agentId={}, agentType={}",
                node.getId(), node.getAgentId(), node.getAgentType());

        String result = agentHandler.executeAgent(
                node.getAgentId(),
                task,
                node.getConversationUuid()
        );

        log.info("Flowable Agent 节点完成: id={}, resultLen={}",
                node.getId(), result != null ? result.length() : 0);

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
