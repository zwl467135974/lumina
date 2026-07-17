package io.lumina.agent.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 评估回归规则数据库实体（DO）
 *
 * <p>对应 V29 创建的 {@code lumina_evaluation_regression_rule} 表，
 * 每个数据集可配一条基线 + 告警阈值 + 告警 webhook。
 *
 * @author Lumina Team
 * @since 3.4.0
 */
@Data
@TableName("lumina_evaluation_regression_rule")
public class EvaluationRegressionRuleDO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键 ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 规则名称
     */
    private String name;

    /**
     * 关联数据集 ID
     */
    private Long datasetId;

    /**
     * 基线 run ID
     */
    private Long baselineRunId;

    /**
     * 允许的最大回归用例数
     */
    private Integer maxRegressed;

    /**
     * 告警 webhook URL（可选）
     */
    private String alertWebhook;

    /**
     * 是否启用（0-禁用，1-启用）
     */
    private Integer enabled;

    /**
     * 租户 ID
     */
    private Long tenantId;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
