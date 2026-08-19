package io.lumina.agent.service.impl;

import io.lumina.agent.config.LuminaAgentProperties;
import io.lumina.agent.service.ApprovalPendingRegistry;
import io.lumina.agent.tool.security.ToolApprovalPort;
import io.lumina.agent.tool.security.ToolExecutionContext;
import io.lumina.notification.event.NotificationEvent;
import io.lumina.notification.event.NotificationEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 通知渠道的工具审批端口（allow-once，fail-closed）
 *
 * <p>流程：向审批人发送通知（站内/企微，含审批 ID 与操作指引）→
 * 阻塞等待审批端点回填结果 → 超时/异常/无接收人一律按拒绝（fail-closed）。
 * 审批人默认为发起调用的用户，可通过
 * {@code lumina.agent.tool.security.approver-user-ids} 指定租户管理员列表。
 *
 * @author Lumina Team
 * @since 3.11.0
 */
@Slf4j
@Service
public class NotificationToolApprovalPort implements ToolApprovalPort {

    private final NotificationEventPublisher publisher;
    private final ApprovalPendingRegistry registry;
    private final LuminaAgentProperties agentProperties;

    public NotificationToolApprovalPort(NotificationEventPublisher publisher,
                                        ApprovalPendingRegistry registry,
                                        LuminaAgentProperties agentProperties) {
        this.publisher = publisher;
        this.registry = registry;
        this.agentProperties = agentProperties;
    }

    @Override
    public boolean requestApproval(ToolExecutionContext context, String reason) {
        LuminaAgentProperties.SecurityConfig security = agentProperties.getTool().getSecurity();
        String approvalId = UUID.randomUUID().toString().replace("-", "");
        CompletableFuture<Boolean> future = registry.register(approvalId);

        List<Long> receivers = security.getApproverUserIds();
        if (receivers == null || receivers.isEmpty()) {
            receivers = context.getUserId() != null ? List.of(context.getUserId()) : List.of();
        }
        if (receivers.isEmpty()) {
            log.warn("工具审批无可用接收人，按拒绝处理（fail-closed）: tool={}", context.getToolName());
            registry.complete(approvalId, false);
            return false;
        }

        String content = "工具调用审批请求\n工具: " + context.getToolName()
                + "\n原因: " + reason
                + "\n会话: " + (context.getConversationId() != null ? context.getConversationId() : "-")
                + "\n审批 ID: " + approvalId
                + "\n操作: POST /agents/tools/approvals/" + approvalId + "?approved=true|false";
        for (Long userId : receivers) {
            try {
                publisher.publish(new NotificationEvent(userId, "SYSTEM", "工具调用审批",
                        content, "WARN", "TOOL_APPROVAL", approvalId, context.getTenantId()));
            } catch (Exception e) {
                log.warn("审批通知发送失败: userId={}, error={}", userId, e.getMessage());
            }
        }

        try {
            boolean approved = future.get(security.getApprovalTimeoutSeconds(), TimeUnit.SECONDS);
            if (!approved) {
                log.info("工具审批被拒绝: tool={}, approvalId={}", context.getToolName(), approvalId);
            }
            return approved;
        } catch (TimeoutException e) {
            log.warn("工具审批超时（{}s），按拒绝处理: tool={}, approvalId={}",
                    security.getApprovalTimeoutSeconds(), context.getToolName(), approvalId);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("工具审批等待被中断，按拒绝处理: tool={}", context.getToolName());
            return false;
        } catch (Exception e) {
            log.warn("工具审批等待异常，按拒绝处理: tool={}, error={}", context.getToolName(), e.getMessage());
            return false;
        }
    }
}
