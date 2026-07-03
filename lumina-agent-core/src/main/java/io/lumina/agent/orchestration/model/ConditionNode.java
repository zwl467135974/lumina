package io.lumina.agent.orchestration.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * 条件分支节点
 *
 * <p>根据 SpEL 表达式求值结果，路由到不同的后续节点。
 * 求值结果为 {@code true} 时走 {@code trueBranch}，否则走 {@code falseBranch}。
 *
 * <p>也支持多路分支（{@code branches}），按顺序匹配第一个条件为 true 的分支。
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ConditionNode extends WorkflowNode {

    /** SpEL 条件表达式（如 {@code #category == 'refund'}） */
    private String expression;

    /** 条件为 true 时路由到的目标节点 ID */
    private String trueBranch;

    /** 条件为 false 时路由到的目标节点 ID */
    private String falseBranch;

    /** 多路分支列表（优先于 trueBranch/falseBranch） */
    private List<Branch> branches = new ArrayList<>();

    /**
     * 多路分支定义
     */
    @Data
    public static class Branch {
        /** 分支条件表达式 */
        private String condition;
        /** 目标节点 ID */
        private String to;
    }
}
