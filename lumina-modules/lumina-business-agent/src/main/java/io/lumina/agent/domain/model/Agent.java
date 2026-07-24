package io.lumina.agent.domain.model;

import io.lumina.common.core.ErrorCode;
import io.lumina.common.exception.BusinessException;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Agent 领域实体
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
public class Agent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Agent ID
     */
    private Long agentId;

    /**
     * Agent 名称
     */
    private String agentName;

    /**
     * Agent 类型
     */
    private String agentType;

    /**
     * 描述
     */
    private String description;

    /**
     * LLM 配置 JSON（modelType/modelName/temperature 等）
     */
    private String llmConfig;

    /**
     * 子 Agent 配置 JSON（MultiAgent 模式）
     */
    private String subAgents;

    /**
     * 工具列表（逗号分隔）
     */
    private String tools;

    /**
     * 租户 ID
     */
    private Long tenantId;

    /**
     * 状态（0-禁用，1-启用）
     */
    private Integer status;

    /**
     * 每分钟最大请求数，0=用全局默认
     */
    private Integer rateLimit;

    /**
     * 最大并发执行数，0=不限制
     */
    private Integer maxConcurrent;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 激活 Agent
     */
    public void activate() {
        if (this.status == 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Agent 已是启用状态");
        }
        this.status = 1;
    }

    /**
     * 禁用 Agent
     */
    public void deactivate() {
        if (this.status == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Agent 已是禁用状态");
        }
        this.status = 0;
    }

    /**
     * 判断是否启用
     */
    public boolean isActive() {
        return this.status == 1;
    }

    /**
     * 验证 Agent 名称
     */
    public void validateName() {
        if (agentName == null || agentName.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Agent 名称不能为空");
        }
        if (agentName.length() > 100) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Agent 名称长度不能超过100个字符");
        }
    }

    /**
     * 验证 Agent 类型
     */
    public void validateType() {
        if (agentType == null || agentType.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Agent 类型不能为空");
        }
    }
}
