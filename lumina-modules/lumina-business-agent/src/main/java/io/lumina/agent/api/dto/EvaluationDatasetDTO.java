package io.lumina.agent.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 评估数据集请求 DTO
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Data
public class EvaluationDatasetDTO {

    @NotBlank(message = "数据集名称不能为空")
    @Size(max = 200, message = "数据集名称长度不能超过200")
    private String name;

    @Size(max = 500, message = "描述长度不能超过500")
    private String description;

    private String agentType;

    @NotBlank(message = "测试用例 YAML 不能为空")
    private String casesYaml;
}
