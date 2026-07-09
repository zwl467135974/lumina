package io.lumina.base.api.dto.dict;

import io.lumina.common.core.BaseDTO;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 创建字典类型 DTO
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CreateDictTypeDTO extends BaseDTO {

    @NotBlank(message = "字典类型不能为空")
    @Size(max = 100, message = "字典类型最多100个字符")
    private String dictType;

    @NotBlank(message = "字典名称不能为空")
    @Size(max = 100, message = "字典名称最多100个字符")
    private String dictName;

    private Integer status;

    @Size(max = 255, message = "备注最多255个字符")
    private String remark;
}
