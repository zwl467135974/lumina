package io.lumina.agent.orchestration.engine;

import io.lumina.agent.orchestration.model.WorkflowContext;
import io.lumina.agent.orchestration.model.WorkflowNode;

/**
 * 节点执行器（策略模式）
 *
 * <p>每种节点类型对应一个执行器实现，负责执行节点逻辑并返回结果。
 *
 * @author Lumina Team
 * @since 2.0.0
 */
public interface NodeExecutor {

    /**
     * 判断此执行器是否支持指定的节点类型
     */
    boolean supports(WorkflowNode node);

    /**
     * 执行节点
     *
     * @param node 节点定义
     * @param ctx  运行时上下文
     * @return 执行结果（存入 {@code outputVar}）
     */
    Object execute(WorkflowNode node, WorkflowContext ctx) throws Exception;
}
