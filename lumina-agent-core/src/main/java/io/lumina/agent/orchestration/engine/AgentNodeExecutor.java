package io.lumina.agent.orchestration.engine;

import io.lumina.agent.orchestration.expression.ExpressionEvaluator;
import io.lumina.agent.orchestration.model.AgentNode;
import io.lumina.agent.orchestration.model.WorkflowContext;
import io.lumina.agent.orchestration.model.WorkflowNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * Agent 节点执行器
 *
 * <p>从上下文中解析输入，委托 {@link AgentExecutionHandler} 执行 Agent，将结果存入上下文。
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Slf4j
@Component
@ConditionalOnBean(AgentExecutionHandler.class)
@RequiredArgsConstructor
public class AgentNodeExecutor implements NodeExecutor {

    private final AgentExecutionHandler agentHandler;
    private final ExpressionEvaluator expressionEvaluator;

    @Override
    public boolean supports(WorkflowNode node) {
        return node instanceof AgentNode;
    }

    @Override
    public Object execute(WorkflowNode node, WorkflowContext ctx) {
        AgentNode agentNode = (AgentNode) node;

        String task = resolveInput(agentNode, ctx);

        log.info("执行 Agent 节点: id={}, agentId={}, task={}", agentNode.getId(), agentNode.getAgentId(),
                task != null && task.length() > 80 ? task.substring(0, 80) + "..." : task);

        String result = agentHandler.executeAgent(
                agentNode.getAgentId(),
                task,
                agentNode.getConversationUuid()
        );

        log.info("Agent 节点完成: id={}, resultLen={}", agentNode.getId(),
                result != null ? result.length() : 0);

        return result;
    }

    private String resolveInput(AgentNode node, WorkflowContext ctx) {
        if (node.getInput() == null || node.getInput().isBlank()) {
            return "";
        }
        Object resolved = expressionEvaluator.evaluate(node.getInput(), ctx.toEvaluationRoot());
        return resolved != null ? resolved.toString() : "";
    }
}
