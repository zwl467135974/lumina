package io.lumina.base.api.vo.dict;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 字典类型 VO
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
public class DictTypeVO {

    private Long id;
    private String dictType;
    private String dictName;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
