package io.lumina.agent.api.vo;

import io.lumina.agent.infrastructure.entity.SkillDO;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 技能 VO
 *
 * @author Lumina Team
 * @since 3.11.0
 */
@Data
public class SkillVO {

    private Long id;
    private String name;
    private String description;
    private String whenToUse;
    private String content;
    private Boolean enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static SkillVO from(SkillDO skill) {
        SkillVO vo = new SkillVO();
        vo.setId(skill.getId());
        vo.setName(skill.getName());
        vo.setDescription(skill.getDescription());
        vo.setWhenToUse(skill.getWhenToUse());
        vo.setContent(skill.getContent());
        vo.setEnabled(skill.getEnabled() != null && skill.getEnabled() == 1);
        vo.setCreateTime(skill.getCreateTime());
        vo.setUpdateTime(skill.getUpdateTime());
        return vo;
    }
}
