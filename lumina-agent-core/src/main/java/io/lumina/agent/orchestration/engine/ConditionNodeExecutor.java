package io.lumina.agent.orchestration.engine;

import io.lumina.agent.orchestration.expression.ExpressionEvaluator;
import io.lumina.agent.orchestration.model.ConditionNode;
import io.lumina.agent.orchestration.model.WorkflowContext;
import io.lumina.agent.orchestration.model.WorkflowNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 条件节点执行器
 *
 * <p>对条件表达式求值，返回 {@code "true:targetId"} 或 {@code "false:targetId"} 格式的路由指令。
 * 引擎根据返回值决定下一个执行节点。
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConditionNodeExecutor implements NodeExecutor {

    private final ExpressionEvaluator expressionEvaluator;

    /** 路由结果前缀 */
    public static final String ROUTE_PREFIX = "route:";

    @Override
    public boolean supports(WorkflowNode node) {
        return node instanceof ConditionNode;
    }

    @Override
    public Object execute(WorkflowNode node, WorkflowContext ctx) {
        ConditionNode condNode = (ConditionNode) node;

        if (condNode.getBranches() != null && !condNode.getBranches().isEmpty()) {
            for (ConditionNode.Branch branch : condNode.getBranches()) {
                if (expressionEvaluator.evaluateBoolean(branch.getCondition(), ctx.toEvaluationRoot())) {
                    log.info("条件节点多路分支匹配: id={}, to={}", node.getId(), branch.getTo());
                    return ROUTE_PREFIX + branch.getTo();
                }
            }
            log.warn("条件节点无分支匹配: id={}", node.getId());
            return null;
        }

        boolean result = expressionEvaluator.evaluateBoolean(
                condNode.getExpression(), ctx.toEvaluationRoot());

        String target = result ? condNode.getTrueBranch() : condNode.getFalseBranch();
        log.info("条件节点路由: id={}, result={}, to={}", node.getId(), result, target);

        return ROUTE_PREFIX + target;
    }
}
