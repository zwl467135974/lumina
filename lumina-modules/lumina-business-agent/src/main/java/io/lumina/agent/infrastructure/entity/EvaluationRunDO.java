package io.lumina.agent.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 评估运行记录 DO
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Data
@TableName("lumina_evaluation_run")
public class EvaluationRunDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long datasetId;
    private String datasetName;
    private Long agentId;
    private String agentType;
    private String modelName;
    private String provider;
    private String scoringMethod;
    private BigDecimal thresholdValue;
    private Integer totalCases;
    private Integer passedCases;
    private BigDecimal passRate;
    private BigDecimal avgScore;
    private Long avgLatencyMs;
    private Integer totalTokens;
    private String resultsJson;
    private String status;
    private Long tenantId;
    private Long createBy;
    private LocalDateTime createTime;
}
