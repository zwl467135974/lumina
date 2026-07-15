package io.lumina.agent.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.Map;

/**
 * 从模板创建工作流 DTO
 *
 * @author Lumina Team
 * @since 3.3.0
 */
@Data
public class CreateFromTemplateDTO {

    /** 模板名称（如 plan-execute、group-chat、supervisor-worker） */
    @NotBlank(message = "模板名称不能为空")
    private String templateName;

    /** 新工作流名称 */
    @NotBlank(message = "工作流名称不能为空")
    private String workflowName;

    /** 占位符 → Agent ID 映射（如 {"agent1": 1, "agent2": 2}） */
    @NotEmpty(message = "Agent 映射不能为空")
    private Map<String, Long> agentMapping;
}
