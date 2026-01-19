package io.lumina.agent.api.dto;

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
    private String agentName;

    /**
     * Agent 类型
     */
    private String agentType;

    /**
     * 描述
     */
    private String description;
}
