package io.lumina.agent.mq;

import java.io.Serializable;

/**
 * Agent 异步任务消息
 *
 * <p>由 AgentTaskServiceImpl 在提交任务时发送，AgentTaskConsumer 异步消费执行。
 *
 * @author Lumina Team
 * @since 2.0.0
 */
public class AgentTaskMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private String taskUuid;
    private Long tenantId;
    private Long userId;
    private String username;

    public AgentTaskMessage() {
    }

    public AgentTaskMessage(String taskUuid, Long tenantId, Long userId, String username) {
        this.taskUuid = taskUuid;
        this.tenantId = tenantId;
        this.userId = userId;
        this.username = username;
    }

    public String getTaskUuid() { return taskUuid; }
    public void setTaskUuid(String taskUuid) { this.taskUuid = taskUuid; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    @Override
    public String toString() {
        return "AgentTaskMessage{taskUuid='" + taskUuid + "', tenantId=" + tenantId + ", userId=" + userId + "}";
    }
}
