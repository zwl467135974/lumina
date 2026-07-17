package io.lumina.agent.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 定时触发器 DO
 *
 * @author Lumina Team
 * @since 3.5.0
 */
@Data
@TableName("lumina_agent_trigger")
public class AgentTriggerDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private Long agentId;

    /**
     * 目标工作流 ID（预留，首版仅支持 agentId）
     */
    private Long workflowId;

    /**
     * Spring 6 字段 cron：秒 分 时 日 月 周
     */
    private String cronExpr;

    private String inputText;

    /**
     * 错过策略：FIRE_ONCE=补触发一次 / SKIP=跳过
     */
    private String misfirePolicy;

    private Integer enabled;
    private LocalDateTime nextFireAt;
    private LocalDateTime lastFireAt;
    private String lastStatus;
    private String lastError;

    /**
     * 连续失败次数，达 5 自动禁用
     */
    private Integer failCount;

    private Long tenantId;
    private Long createBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableField("is_deleted")
    private Integer isDeleted;
}
