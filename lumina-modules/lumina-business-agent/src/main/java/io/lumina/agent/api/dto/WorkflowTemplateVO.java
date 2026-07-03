package io.lumina.agent.api.dto;

import lombok.Data;

/**
 * 工作流模板 VO
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Data
public class WorkflowTemplateVO {
    private String name;
    private String description;
    private String definitionYaml;
}
