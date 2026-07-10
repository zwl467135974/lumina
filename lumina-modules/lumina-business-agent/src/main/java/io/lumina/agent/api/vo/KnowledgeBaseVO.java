package io.lumina.agent.api.vo;

import io.lumina.agent.infrastructure.entity.KnowledgeBaseDO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识库 VO
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeBaseVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String description;
    private String visibility;
    private Long createBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static KnowledgeBaseVO from(KnowledgeBaseDO do_) {
        if (do_ == null) {
            return null;
        }
        return KnowledgeBaseVO.builder()
                .id(do_.getId())
                .name(do_.getName())
                .description(do_.getDescription())
                .visibility(do_.getVisibility())
                .createBy(do_.getCreateBy())
                .createTime(do_.getCreateTime())
                .updateTime(do_.getUpdateTime())
                .build();
    }
}
