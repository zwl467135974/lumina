package io.lumina.base.api.dto.dict;

import io.lumina.common.core.BaseDTO;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 更新字典类型 DTO（dictType 不可修改）
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UpdateDictTypeDTO extends BaseDTO {

    @NotBlank(message = "字典名称不能为空")
    @Size(max = 100, message = "字典名称最多100个字符")
    private String dictName;

    @Min(value = 0, message = "状态值最小为0")
    @Max(value = 1, message = "状态值最大为1")
    private Integer status;

    @Size(max = 255, message = "备注最多255个字符")
    private String remark;
}
