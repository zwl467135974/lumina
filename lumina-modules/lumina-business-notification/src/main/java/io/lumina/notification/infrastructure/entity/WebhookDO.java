package io.lumina.notification.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Webhook 订阅数据库实体（DO）
 *
 * @author Lumina Team
 * @since 3.4.0
 */
@Data
@TableName("lumina_webhook")
public class WebhookDO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键 ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 订阅用户 ID
     */
    private Long userId;

    /**
     * 租户 ID
     */
    private Long tenantId;

    /**
     * 用户自填名称
     */
    private String name;

    /**
     * webhook 接收端 URL
     */
    private String url;

    /**
     * 通知渠道（NotificationChannel.name()，默认 WEBHOOK）
     */
    private String channel;

    /**
     * HMAC 签名密钥（可选，WE_COM 渠道留空）
     */
    private String secret;

    /**
     * 订阅的 NotificationCategory 逗号分隔，* 表示全部
     */
    private String events;

    /**
     * 是否启用（0-禁用，1-启用）
     */
    private Integer enabled;

    /**
     * 最后触发时间
     */
    private LocalDateTime lastTriggeredAt;

    /**
     * 最后发送状态（SUCCESS/FAILED）
     */
    private String lastStatus;

    /**
     * 最后失败原因
     */
    private String lastError;

    /**
     * 连续失败次数，达 5 自动 disable
     */
    private Integer failCount;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 逻辑删除（0-正常，1-已删除）
     */
    @TableLogic
    private Integer deleted;
}
