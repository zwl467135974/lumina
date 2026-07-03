package io.lumina.agent.api.dto;

import lombok.Data;

import java.util.Map;

/**
 * 执行工作流 DTO
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Data
public class ExecuteWorkflowDTO {

    /** 输入参数 */
    private Map<String, Object> inputs;
}
