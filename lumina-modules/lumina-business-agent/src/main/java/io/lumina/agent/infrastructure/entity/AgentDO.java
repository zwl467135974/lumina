package io.lumina.agent.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Agent 数据库实体（DO）
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
@TableName("lumina_agent")
public class AgentDO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Agent ID
     */
    @TableId(value = "agent_id", type = IdType.AUTO)
    private Long agentId;

    /**
     * Agent 名称
     */
    @TableField("agent_name")
    private String agentName;

    /**
     * Agent 类型
     */
    @TableField("agent_type")
    private String agentType;

    /**
     * 描述
     */
    @TableField("description")
    private String description;

    /**
     * LLM 配置 JSON
     */
    @TableField("llm_config")
    private String llmConfig;

    /**
     * 工具列表（逗号分隔）
     */
    @TableField("tools")
    private String tools;

    /**
     * 租户 ID
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 状态（0-禁用，1-启用）
     */
    @TableField("status")
    private Integer status;

    /**
     * 每分钟最大请求数，0=用全局默认
     */
    @TableField("rate_limit")
    private Integer rateLimit;

    /**
     * 最大并发执行数，0=不限制
     */
    @TableField("max_concurrent")
    private Integer maxConcurrent;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 逻辑删除（0-未删除，1-已删除）
     */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
