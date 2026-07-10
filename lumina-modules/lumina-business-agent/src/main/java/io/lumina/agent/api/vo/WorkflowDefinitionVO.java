package io.lumina.agent.api.vo;

import io.lumina.agent.infrastructure.entity.WorkflowDefinitionDO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工作流定义 VO
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowDefinitionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String description;
    private String definitionYaml;
    private Integer version;
    private Integer status;
    private Long createBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static WorkflowDefinitionVO from(WorkflowDefinitionDO do_) {
        if (do_ == null) {
            return null;
        }
        return WorkflowDefinitionVO.builder()
                .id(do_.getId())
                .name(do_.getName())
                .description(do_.getDescription())
                .definitionYaml(do_.getDefinitionYaml())
                .version(do_.getVersion())
                .status(do_.getStatus())
                .createBy(do_.getCreateBy())
                .createTime(do_.getCreateTime())
                .updateTime(do_.getUpdateTime())
                .build();
    }
}
