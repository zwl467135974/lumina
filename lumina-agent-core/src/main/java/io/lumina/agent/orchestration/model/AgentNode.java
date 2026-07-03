package io.lumina.agent.orchestration.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Agent 执行节点
 *
 * <p>调用已注册的 Agent 执行任务，是最常用的工作流节点类型。
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AgentNode extends WorkflowNode {

    /** Agent ID（数据库主键） */
    private Long agentId;

    /** Agent 业务类型（与 Agent 二选一，用于从配置文件加载） */
    private String agentType;

    /** 会话 UUID（可选，支持多轮对话上下文） */
    private String conversationUuid;
}
