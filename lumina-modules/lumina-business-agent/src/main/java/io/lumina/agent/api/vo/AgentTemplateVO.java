package io.lumina.agent.api.vo;

import io.lumina.agent.infrastructure.entity.AgentTemplateDO;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Agent 模板 VO
 *
 * @author Lumina Team
 * @since 3.12.0
 */
@Data
public class AgentTemplateVO {

    private Long id;
    private String name;
    private String agentType;
    private String description;
    /** 随包技能名列表 */
    private List<String> skillNames;
    /** 来源：IMPORT / EXPORT */
    private String source;
    private Integer version;
    private LocalDateTime createTime;

    public static AgentTemplateVO from(AgentTemplateDO template) {
        AgentTemplateVO vo = new AgentTemplateVO();
        vo.setId(template.getId());
        vo.setName(template.getName());
        vo.setAgentType(template.getAgentType());
        vo.setDescription(template.getDescription());
        vo.setSkillNames(template.getSkillNames() == null || template.getSkillNames().isBlank()
                ? List.of()
                : Arrays.stream(template.getSkillNames().split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
        vo.setSource(template.getSource());
        vo.setVersion(template.getVersion());
        vo.setCreateTime(template.getCreateTime());
        return vo;
    }
}
