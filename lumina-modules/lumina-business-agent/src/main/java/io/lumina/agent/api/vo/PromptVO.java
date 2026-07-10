package io.lumina.agent.api.vo;

import io.lumina.agent.infrastructure.entity.PromptDO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Prompt 版本管理 VO
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private Integer version;
    private String content;
    private String description;
    private String agentType;
    private String variables;
    private Integer status;
    private Integer isActive;
    private Long createBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static PromptVO from(PromptDO do_) {
        if (do_ == null) {
            return null;
        }
        return PromptVO.builder()
                .id(do_.getId())
                .name(do_.getName())
                .version(do_.getVersion())
                .content(do_.getContent())
                .description(do_.getDescription())
                .agentType(do_.getAgentType())
                .variables(do_.getVariables())
                .status(do_.getStatus())
                .isActive(do_.getIsActive())
                .createBy(do_.getCreateBy())
                .createTime(do_.getCreateTime())
                .updateTime(do_.getUpdateTime())
                .build();
    }
}
