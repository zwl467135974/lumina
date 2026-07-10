package io.lumina.agent.api.dto.llm;

import io.lumina.common.core.BaseDTO;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 查询 LLM 供应商配置 DTO
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QueryLlmProviderDTO extends BaseDTO {
    private String name;
    private String provider;
    private Integer status;
    @Min(value = 1, message = "页码最小为1")
    private Integer pageNum = 1;
    @Min(value = 1, message = "每页条数最小为1")
    @Max(value = 100, message = "每页条数最大为100")
    private Integer pageSize = 20;
}
