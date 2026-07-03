package io.lumina.agent.orchestration.engine;

import io.lumina.agent.orchestration.model.ParallelNode;
import io.lumina.agent.orchestration.model.WorkflowContext;
import io.lumina.agent.orchestration.model.WorkflowNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 并行节点执行器
 *
 * <p>并行节点的执行由 {@link WorkflowEngine} 内部处理（fan-out/fan-in 调度），
 * 此执行器仅做前置校验和日志。
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Slf4j
@Component
public class ParallelNodeExecutor implements NodeExecutor {

    @Override
    public boolean supports(WorkflowNode node) {
        return node instanceof ParallelNode;
    }

    @Override
    public Object execute(WorkflowNode node, WorkflowContext ctx) {
        ParallelNode parallelNode = (ParallelNode) node;
        log.info("并行节点: id={}, branches={}, waitAll={}",
                node.getId(), parallelNode.getBranches().size(), parallelNode.isWaitAll());
        return null;
    }
}
