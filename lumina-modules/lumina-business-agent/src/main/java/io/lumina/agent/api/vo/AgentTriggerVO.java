package io.lumina.agent.api.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 定时触发器 VO
 *
 * @author Lumina Team
 * @since 3.5.0
 */
@Data
public class AgentTriggerVO {

    private Long id;
    private String name;
    private Long agentId;
    private Long workflowId;
    private String cronExpr;
    private String inputText;
    private String misfirePolicy;
    private Integer enabled;
    private LocalDateTime nextFireAt;
    private LocalDateTime lastFireAt;
    private String lastStatus;
    private String lastError;
    private Integer failCount;
    private LocalDateTime createTime;

    /**
     * 人类可读的下次触发时间描述（如 "下次：2026-07-17 09:00:00"）
     */
    private String nextFireAtDescription;
}
