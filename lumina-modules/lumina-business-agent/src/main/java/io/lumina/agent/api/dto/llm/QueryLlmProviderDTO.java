package io.lumina.agent.api.dto.llm;

import io.lumina.common.core.BaseDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class QueryLlmProviderDTO extends BaseDTO {
    private String name;
    private String provider;
    private Integer status;
    private Integer pageNum = 1;
    private Integer pageSize = 20;
}
