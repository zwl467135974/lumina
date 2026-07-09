package io.lumina.base.api.dto.dict;

import io.lumina.common.core.BaseDTO;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 创建字典项 DTO
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CreateDictItemDTO extends BaseDTO {

    @NotBlank(message = "字典类型不能为空")
    @Size(max = 100, message = "字典类型最多100个字符")
    private String dictType;

    @NotBlank(message = "字典标签不能为空")
    @Size(max = 100, message = "字典标签最多100个字符")
    private String dictLabel;

    @NotBlank(message = "字典值不能为空")
    @Size(max = 100, message = "字典值最多100个字符")
    private String dictValue;

    private Integer sortOrder;

    private Integer status;

    @Size(max = 255, message = "备注最多255个字符")
    private String remark;
}
