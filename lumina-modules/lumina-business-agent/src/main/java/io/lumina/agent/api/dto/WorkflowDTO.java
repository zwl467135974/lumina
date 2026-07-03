package io.lumina.agent.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建/更新工作流 DTO
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Data
public class WorkflowDTO {

    /** 工作流名称 */
    @NotBlank(message = "工作流名称不能为空")
    private String name;

    /** 描述 */
    private String description;

    /** YAML 定义 */
    @NotBlank(message = "工作流定义不能为空")
    private String definitionYaml;
}
