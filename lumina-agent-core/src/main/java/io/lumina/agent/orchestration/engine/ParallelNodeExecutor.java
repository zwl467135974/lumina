package io.lumina.agent.orchestration.engine;

import io.lumina.agent.orchestration.model.ParallelNode;
import io.lumina.agent.orchestration.model.WorkflowContext;
import io.lumina.agent.orchestration.model.WorkflowNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 并行节点执行器
 *
 * <p>返回 {@link ParallelSignal}，由 {@link DefaultWorkflowEngine} 检测并执行 fan-out/fan-in。
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

        List<ParallelBranchInfo> branches = new ArrayList<>();
        for (ParallelNode.ParallelBranch pb : parallelNode.getBranches()) {
            branches.add(new ParallelBranchInfo(pb.getName(), pb.getStartNode()));
        }

        log.info("并行节点: id={}, branches={}, waitAll={}",
                node.getId(), branches.size(), parallelNode.isWaitAll());

        return new ParallelSignal(branches, parallelNode.isWaitAll());
    }

    /**
     * 并行执行信号（引擎接收后 fan-out 到各分支）
     *
     * @param branches 分支信息列表
     * @param waitAll  true=等待全部完成，false=任一完成即继续
     */
    public record ParallelSignal(List<ParallelBranchInfo> branches, boolean waitAll) {
    }

    /**
     * 分支信息
     */
    public record ParallelBranchInfo(String name, String startNode) {
    }
}
