package io.lumina.agent.api.vo;

import io.lumina.agent.infrastructure.entity.WorkflowInstanceDO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工作流实例 VO
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowInstanceVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long definitionId;
    private String definitionName;
    private Integer definitionVersion;
    private String status;
    private String input;
    private String output;
    private String errorMessage;
    private String currentNodeId;
    private Long createBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static WorkflowInstanceVO from(WorkflowInstanceDO do_) {
        if (do_ == null) {
            return null;
        }
        return WorkflowInstanceVO.builder()
                .id(do_.getId())
                .definitionId(do_.getDefinitionId())
                .definitionName(do_.getDefinitionName())
                .definitionVersion(do_.getDefinitionVersion())
                .status(do_.getStatus())
                .input(do_.getInput())
                .output(do_.getOutput())
                .errorMessage(do_.getErrorMessage())
                .currentNodeId(do_.getCurrentNodeId())
                .createBy(do_.getCreateBy())
                .createTime(do_.getCreateTime())
                .updateTime(do_.getUpdateTime())
                .build();
    }
}
