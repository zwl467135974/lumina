package io.lumina.agent.api.dto.llm;

import io.lumina.common.core.BaseDTO;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

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

    private Integer status;
}
