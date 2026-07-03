package io.lumina.agent.orchestration.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * 并行执行节点
 *
 * <p>同时启动多个分支节点，等待全部（或任一）完成后继续。
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ParallelNode extends WorkflowNode {

    /** 并行分支列表（每个分支指定一个起始节点 ID） */
    private List<ParallelBranch> branches = new ArrayList<>();

    /** 是否等待所有分支完成（true=全部等待，false=任一完成即继续） */
    private boolean waitAll = true;

    /**
     * 并行分支定义
     */
    @Data
    public static class ParallelBranch {
        /** 分支名称 */
        private String name;
        /** 分支起始节点 ID */
        private String startNode;
    }
}
