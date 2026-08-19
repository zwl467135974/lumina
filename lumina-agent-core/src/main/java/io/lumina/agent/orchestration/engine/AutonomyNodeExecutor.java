package io.lumina.agent.orchestration.engine;

import io.lumina.agent.orchestration.expression.ExpressionEvaluator;
import io.lumina.agent.orchestration.model.AutonomyNode;
import io.lumina.agent.orchestration.model.WorkflowContext;
import io.lumina.agent.orchestration.model.WorkflowNode;
import io.lumina.agent.orchestration.script.AutonomyScriptEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * 自主编排节点执行器（默认工作流引擎路径）
 *
 * @author Lumina Team
 * @since 3.11.0
 */
@Slf4j
@Component
@ConditionalOnBean(AgentExecutionHandler.class)
@RequiredArgsConstructor
public class AutonomyNodeExecutor implements NodeExecutor {

    private final AutonomyScriptEngine scriptEngine;
    private final ExpressionEvaluator expressionEvaluator;

    @Override
    public boolean supports(WorkflowNode node) {
        return node instanceof AutonomyNode;
    }

    @Override
    public Object execute(WorkflowNode node, WorkflowContext ctx) {
        AutonomyNode autonomyNode = (AutonomyNode) node;

        String input = resolveInput(autonomyNode, ctx);
        log.info("执行 autonomy 节点: id={}, agentId={}, scriptLen={}",
                autonomyNode.getId(), autonomyNode.getAgentId(),
                autonomyNode.getScript() != null ? autonomyNode.getScript().length() : 0);

        Object result = scriptEngine.run(autonomyNode, input);

        log.info("autonomy 节点完成: id={}, resultType={}", autonomyNode.getId(),
                result != null ? result.getClass().getSimpleName() : "null");
        return result;
    }

    private String resolveInput(AutonomyNode node, WorkflowContext ctx) {
        if (node.getInput() == null || node.getInput().isBlank()) {
            return "";
        }
        Object resolved = expressionEvaluator.evaluate(node.getInput(), ctx.toEvaluationRoot());
        return resolved != null ? resolved.toString() : "";
    }
}
