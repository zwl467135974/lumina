package io.lumina.agent.orchestration.engine;

import io.lumina.agent.orchestration.expression.ExpressionEvaluator;
import io.lumina.agent.orchestration.model.LoopNode;
import io.lumina.agent.orchestration.model.WorkflowContext;
import io.lumina.agent.orchestration.model.WorkflowNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 循环节点执行器
 *
 * <p>两种模式：
 * <ul>
 *   <li>集合遍历 — {@code iterateVar} 引用集合，逐元素执行 {@code loopTarget}</li>
 *   <li>条件循环 — {@code conditionExpr} 为 true 时重复执行</li>
 * </ul>
 *
 * <p>循环体（{@code loopTarget} 指向的子图）由 {@link WorkflowEngine} 内部递归执行。
 * 此执行器负责初始化循环上下文，返回循环控制信号 {@link LoopSignal}。
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoopNodeExecutor implements NodeExecutor {

    private final ExpressionEvaluator expressionEvaluator;

    @Override
    public boolean supports(WorkflowNode node) {
        return node instanceof LoopNode;
    }

    @Override
    public Object execute(WorkflowNode node, WorkflowContext ctx) {
        LoopNode loopNode = (LoopNode) node;

        if (loopNode.getIterateVar() != null && !loopNode.getIterateVar().isBlank()) {
            return handleIteration(loopNode, ctx);
        }

        if (loopNode.getConditionExpr() != null && !loopNode.getConditionExpr().isBlank()) {
            return handleConditionLoop(loopNode, ctx);
        }

        log.warn("循环节点无遍历变量和条件表达式: id={}", node.getId());
        return null;
    }

    @SuppressWarnings("unchecked")
    private Object handleIteration(LoopNode loopNode, WorkflowContext ctx) {
        Object collection = expressionEvaluator.evaluate(loopNode.getIterateVar(), ctx.toEvaluationRoot());

        List<Object> items = new ArrayList<>();
        if (collection instanceof Collection<?> coll) {
            items.addAll(coll);
        } else if (collection != null) {
            items.add(collection);
        }

        log.info("循环节点遍历: id={}, items={}", loopNode.getId(), items.size());

        List<Object> results = new ArrayList<>();
        for (int i = 0; i < items.size() && i < loopNode.getMaxIterations(); i++) {
            ctx.setVariable(loopNode.getItemVar(), items.get(i));
            ctx.setVariable("_loopIndex", i);
        }

        return new LoopSignal(items.size(), loopNode.getLoopTarget(), loopNode.getExitTarget());
    }

    private Object handleConditionLoop(LoopNode loopNode, WorkflowContext ctx) {
        boolean shouldLoop = expressionEvaluator.evaluateBoolean(
                loopNode.getConditionExpr(), ctx.toEvaluationRoot());

        if (!shouldLoop) {
            log.info("条件循环初始即为 false: id={}", loopNode.getId());
            return new LoopSignal(0, loopNode.getLoopTarget(), loopNode.getExitTarget());
        }

        log.info("条件循环启动: id={}", loopNode.getId());
        return new LoopSignal(loopNode.getMaxIterations(), loopNode.getLoopTarget(), loopNode.getExitTarget());
    }

    /**
     * 循环控制信号（引擎接收后执行循环体）
     */
    public record LoopSignal(int iterations, String loopTarget, String exitTarget) {
    }
}
