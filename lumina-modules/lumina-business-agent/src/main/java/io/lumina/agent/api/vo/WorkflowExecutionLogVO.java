package io.lumina.agent.api.vo;

import io.lumina.agent.infrastructure.entity.WorkflowExecutionLogDO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工作流节点执行日志 VO
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowExecutionLogVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long instanceId;
    private String nodeId;
    private String nodeType;
    private String nodeName;
    private String status;
    private String input;
    private String output;
    private Integer durationMs;
    private String errorMessage;
    private LocalDateTime createTime;

    public static WorkflowExecutionLogVO from(WorkflowExecutionLogDO do_) {
        if (do_ == null) {
            return null;
        }
        return WorkflowExecutionLogVO.builder()
                .id(do_.getId())
                .instanceId(do_.getInstanceId())
                .nodeId(do_.getNodeId())
                .nodeType(do_.getNodeType())
                .nodeName(do_.getNodeName())
                .status(do_.getStatus())
                .input(do_.getInput())
                .output(do_.getOutput())
                .durationMs(do_.getDurationMs())
                .errorMessage(do_.getErrorMessage())
                .createTime(do_.getCreateTime())
                .build();
    }
}
