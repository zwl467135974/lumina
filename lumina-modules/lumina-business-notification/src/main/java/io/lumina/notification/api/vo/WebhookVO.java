package io.lumina.notification.api.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Webhook 订阅 VO
 *
 * <p>secret 仅在创建响应中返回一次，列表查询不返回。
 *
 * @author Lumina Team
 * @since 3.4.0
 */
@Data
public class WebhookVO {

    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 名称
     */
    private String name;

    /**
     * webhook 接收端 URL
     */
    private String url;

    /**
     * 通知渠道（WEBHOOK/WE_COM）
     */
    private String channel;

    /**
     * HMAC 签名密钥（仅创建响应返回）
     */
    private String secret;

    /**
     * 订阅的事件类别（逗号分隔，* 表示全部）
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
     * 连续失败次数
     */
    private Integer failCount;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
