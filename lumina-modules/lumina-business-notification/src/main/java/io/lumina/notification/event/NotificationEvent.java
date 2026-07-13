package io.lumina.notification.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 通知事件（MQ 消息载体，跨服务传递）
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 接收通知的用户 ID
     */
    private Long userId;

    /**
     * 通知分类（BUDGET/TASK/WORKFLOW/DOCUMENT/EVALUATION/SYSTEM）
     */
    private String category;

    /**
     * 标题
     */
    private String title;

    /**
     * 内容
     */
    private String content;

    /**
     * 严重程度（INFO/WARN/ERROR）
     */
    private String severity;

    /**
     * 关联资源类型
     */
    private String refType;

    /**
     * 关联资源 ID
     */
    private String refId;

    /**
     * 租户 ID
     */
    private Long tenantId;
}
