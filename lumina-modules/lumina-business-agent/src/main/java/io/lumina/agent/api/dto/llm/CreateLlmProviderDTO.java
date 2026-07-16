package io.lumina.agent.api.dto.llm;

import io.lumina.common.core.BaseDTO;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 创建 LLM 供应商配置 DTO
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CreateLlmProviderDTO extends BaseDTO {

    @NotBlank(message = "配置名称不能为空")
    @Size(max = 100, message = "配置名称最多100个字符")
    private String name;

    @NotBlank(message = "供应商不能为空")
    @Size(max = 50, message = "供应商最多50个字符")
    private String provider;

    @Size(max = 500, message = "Base URL 最多500个字符")
    private String baseUrl;

    @Size(max = 2000, message = "API Key 最多2000个字符")
    private String apiKey;

    @Size(max = 100, message = "默认模型名最多100个字符")
    private String defaultModel;

    private String defaultParams;

    @Min(value = 0, message = "状态值最小为0")
    @Max(value = 1, message = "状态值最大为1")
    private Integer status;

    /**
     * 优先级（越小越高，默认100，用于 Provider Failover 链排序）
     */
    @Min(value = 1, message = "优先级最小为1")
    private Integer priority;
}
