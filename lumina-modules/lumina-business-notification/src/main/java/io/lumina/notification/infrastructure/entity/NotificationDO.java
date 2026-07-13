package io.lumina.notification.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 通知数据库实体（DO）
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
@TableName("lumina_notification")
public class NotificationDO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键 ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

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
     * 是否已读（0-未读，1-已读）
     */
    private Integer isRead;

    /**
     * 租户 ID
     */
    private Long tenantId;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 阅读时间
     */
    private LocalDateTime readTime;
}
