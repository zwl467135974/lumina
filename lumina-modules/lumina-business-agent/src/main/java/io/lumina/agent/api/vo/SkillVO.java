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
    /** 来源：MANUAL / IMPORT */
    private String source;
    /** 体检状态：NONE / PASSED / FLAGGED */
    private String scanStatus;
    /** 体检报告 JSON（可空） */
    private String scanReport;
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
        vo.setSource(skill.getSource());
        vo.setScanStatus(skill.getScanStatus());
        vo.setScanReport(skill.getScanReport());
        vo.setCreateTime(skill.getCreateTime());
        vo.setUpdateTime(skill.getUpdateTime());
        return vo;
    }
}
