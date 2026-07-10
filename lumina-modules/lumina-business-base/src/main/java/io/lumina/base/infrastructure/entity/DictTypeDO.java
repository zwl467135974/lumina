package io.lumina.base.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 字典类型 DO
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
@TableName("lumina_dict_type")
public class DictTypeDO implements Serializable {
    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO)
    private Long id;
    private String dictType;
    private String dictName;
    private Integer status;
    private String remark;
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
