package io.lumina.notification.domain.enums;

/**
 * 通知渠道枚举
 *
 * <p>标识 webhook 订阅的分发渠道，决定 {@code WebhookDispatcher} 选用哪个 Sender。
 *
 * @author Lumina Team
 * @since 3.4.0
 */
public enum NotificationChannel {

    /**
     * 通用 HTTP webhook（POST JSON + HMAC-SHA256 签名）
     */
    WEBHOOK,

    /**
     * 企业微信群机器人（markdown 消息）
     */
    WE_COM,

    /**
     * 钉钉群机器人（markdown 消息，支持加签安全设置）
     *
     * @since 3.5.0
     */
    DINGTALK,

    /**
     * 飞书群机器人（text 消息）
     *
     * @since 3.5.0
     */
    FEISHU,

    /**
     * Telegram Bot（sendMessage，Markdown parse_mode）
     *
     * @since 3.5.0
     */
    TELEGRAM;

    // 未来：EMAIL, SLACK

    /**
     * 根据名称获取枚举，未知名称返回 WEBHOOK
     */
    public static NotificationChannel fromName(String name) {
        for (NotificationChannel channel : values()) {
            if (channel.name().equalsIgnoreCase(name)) {
                return channel;
            }
        }
        return WEBHOOK;
    }
}
