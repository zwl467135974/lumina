package io.lumina.agent.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 创建 Agent DTO
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
public class CreateAgentDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Agent 名称
     */
    @NotBlank(message = "Agent 名称不能为空")
    @Size(max = 100, message = "Agent 名称不能超过 100 个字符")
    private String agentName;

    /**
     * Agent 类型
     */
    @NotBlank(message = "Agent 类型不能为空")
    @Size(max = 50, message = "Agent 类型不能超过 50 个字符")
    private String agentType;

    /**
     * 描述
     */
    @Size(max = 500, message = "描述不能超过 500 个字符")
    private String description;

    /**
     * LLM 配置 JSON（modelType/modelName/temperature 等）
     */
    private String llmConfig;

    /**
     * 工具列表（逗号分隔）
     */
    private String tools;
}
