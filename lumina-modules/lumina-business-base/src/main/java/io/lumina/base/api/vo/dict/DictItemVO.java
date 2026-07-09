package io.lumina.base.api.vo.dict;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 字典项 VO
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
public class DictItemVO {

    private Long id;
    private String dictType;
    private String dictLabel;
    private String dictValue;
    private Integer sortOrder;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
