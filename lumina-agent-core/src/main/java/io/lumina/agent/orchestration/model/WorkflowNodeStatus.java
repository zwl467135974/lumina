package io.lumina.agent.orchestration.model;

/**
 * 工作流节点状态
 *
 * @author Lumina Team
 * @since 2.0.0
 */
public enum WorkflowNodeStatus {

    /** 待执行 */
    PENDING,

    /** 执行中 */
    RUNNING,

    /** 已完成 */
    COMPLETED,

    /** 执行失败 */
    FAILED,

    /** 已跳过（条件不满足等） */
    SKIPPED,

    /** 已暂停（等待人工输入） */
    WAITING
}
