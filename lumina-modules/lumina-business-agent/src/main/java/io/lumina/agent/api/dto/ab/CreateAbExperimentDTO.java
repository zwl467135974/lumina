package io.lumina.agent.api.dto.ab;

import io.lumina.common.core.BaseDTO;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 创建 A/B 测试实验 DTO
 *
 * @author Lumina Team
 * @since 3.3.1
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CreateAbExperimentDTO extends BaseDTO {

    @NotBlank(message = "实验名称不能为空")
    @Size(max = 100)
    private String name;

    @Size(max = 500)
    private String description;

    @NotNull(message = "Agent ID 不能为空")
    private Long agentId;

    @Min(0) @Max(100)
    private Integer trafficPercent = 100;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    @NotEmpty(message = "至少需要 2 个变体")
    private List<VariantDTO> variants;

    @Data
    public static class VariantDTO {
        @NotBlank
        @Size(max = 50)
        private String name;

        @Min(0) @Max(100)
        private Integer weight = 50;

        private String llmConfig;

        private String promptName;

        @Size(max = 500)
        private String description;
    }
}
