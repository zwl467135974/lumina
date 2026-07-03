package io.lumina.agent.orchestration.model;

/**
 * 工作流状态
 *
 * @author Lumina Team
 * @since 2.0.0
 */
public enum WorkflowStatus {

    /** 待执行 */
    PENDING,

    /** 执行中 */
    RUNNING,

    /** 已暂停（等待人工审批） */
    PAUSED,

    /** 已完成 */
    COMPLETED,

    /** 执行失败 */
    FAILED,

    /** 已取消 */
    CANCELLED
}
