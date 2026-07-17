package io.lumina.notification.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 创建 Webhook 订阅 DTO
 *
 * @author Lumina Team
 * @since 3.4.0
 */
@Data
public class CreateWebhookDTO {

    /**
     * 名称（便于识别用途）
     */
    @NotBlank(message = "webhook 名称不能为空")
    @Size(max = 64, message = "webhook 名称不能超过 64 字符")
    private String name;

    /**
     * webhook 接收端 URL（WE_COM 渠道为企微群机器人完整地址）
     */
    @NotBlank(message = "webhook URL 不能为空")
    @Size(max = 512, message = "webhook URL 不能超过 512 字符")
    @Pattern(regexp = "^https?://.+", message = "webhook URL 必须以 http:// 或 https:// 开头")
    private String url;

    /**
     * 通知渠道（WEBHOOK/WE_COM，默认 WEBHOOK）
     */
    @Pattern(regexp = "^(WEBHOOK|WE_COM)$", message = "channel 仅支持 WEBHOOK 或 WE_COM")
    private String channel;

    /**
     * HMAC 签名密钥（可选，不填则自动生成；WE_COM 渠道忽略）
     */
    @Size(max = 128, message = "secret 不能超过 128 字符")
    private String secret;

    /**
     * 订阅的事件类别（NotificationCategory 名称列表，空或含 * 表示全部）
     */
    private List<String> events;
}
