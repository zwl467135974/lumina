package io.lumina.agent.orchestration.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工作流边
 *
 * <p>定义节点间的连接关系，决定执行流向。
 * 无 {@code condition} 的边为默认路由，有 {@code condition} 的边需条件求值为 true 才通过。
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowEdge {

    /** 源节点 ID */
    private String from;

    /** 目标节点 ID */
    private String to;

    /** 边条件表达式（SpEL，为空表示无条件 / 默认路由） */
    private String condition;
}
