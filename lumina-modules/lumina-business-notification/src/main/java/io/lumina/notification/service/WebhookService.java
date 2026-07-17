package io.lumina.notification.service;

import io.lumina.notification.api.dto.CreateWebhookDTO;
import io.lumina.notification.api.vo.WebhookVO;
import io.lumina.notification.infrastructure.entity.WebhookDO;

import java.util.List;

/**
 * Webhook 订阅服务
 *
 * @author Lumina Team
 * @since 3.4.0
 */
public interface WebhookService {

    /**
     * 创建 webhook 订阅（secret 不填自动生成，仅创建响应返回一次）
     *
     * @param dto 创建参数
     * @return webhook VO（含 secret 明文）
     */
    WebhookVO createWebhook(CreateWebhookDTO dto);

    /**
     * 查询当前用户的 webhook 订阅列表
     *
     * @return webhook 列表（不含 secret）
     */
    List<WebhookVO> listWebhooks();

    /**
     * 删除 webhook 订阅（校验归属当前用户）
     *
     * @param id webhook ID
     */
    void deleteWebhook(Long id);

    /**
     * 发送一条测试消息（category=TEST）
     *
     * @param id webhook ID
     * @return true 发送成功
     */
    boolean testWebhook(Long id);

    /**
     * 查询某用户某事件类别的启用 webhook（Redis 缓存 5 分钟，写时 evict）
     *
     * @param userId 用户 ID
     * @param event  事件类别（NotificationCategory.name()）
     * @return 启用的 webhook 列表
     */
    List<WebhookDO> findEnabledForEvent(Long userId, String event);
}
