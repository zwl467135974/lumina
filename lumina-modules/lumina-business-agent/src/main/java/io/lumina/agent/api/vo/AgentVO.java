package io.lumina.agent.api.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent VO
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
public class AgentVO implements Serializable {

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
     * LLM 配置 JSON
     */
    private String llmConfig;

    /**
     * 工具列表（逗号分隔）
     */
    private String tools;

    /**
     * 状态
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 挂载的知识库 ID 列表
     */
    private List<Long> knowledgeBaseIds;

    /**
     * 每分钟最大请求数（0=用全局默认）
     */
    private Integer rateLimit;

    /**
     * 最大并发执行数（0=不限制）
     */
    private Integer maxConcurrent;
}
