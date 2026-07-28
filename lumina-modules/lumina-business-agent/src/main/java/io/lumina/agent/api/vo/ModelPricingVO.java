package io.lumina.agent.api.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 模型价格 VO
 *
 * <p>API 出参专用，隔离数据库实体 {@code ModelPricingDO}。
 *
 * @author Lumina Team
 * @since 3.10.0
 */
@Data
public class ModelPricingVO implements Serializable {

    private static final long serialVersionUID = 1L;

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
