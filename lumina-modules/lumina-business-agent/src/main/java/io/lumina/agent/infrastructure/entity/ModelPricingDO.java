package io.lumina.agent.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 模型价格配置 DO
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Data
@TableName("lumina_model_pricing")
public class ModelPricingDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String provider;
    private String modelName;
    private BigDecimal inputPrice;
    private BigDecimal outputPrice;
    private String currency;
    private Integer isActive;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
