package io.lumina.agent.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建/更新 Prompt DTO
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Data
public class PromptDTO {

    @NotBlank(message = "Prompt 名称不能为空")
    private String name;

    private String content;

    private String description;

    /** 关联的 Agent 类型（如 assistant / customer-service） */
    private String agentType;

    /** 变量列表（逗号分隔） */
    private String variables;
}
