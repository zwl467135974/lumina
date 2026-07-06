package io.lumina.agent.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * Agent 异步任务请求 DTO
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Data
public class AgentTaskRequestDTO {

    @NotBlank(message = "任务描述不能为空")
    private String task;

    private String conversationId;

    private List<String> fileUuids;
}
