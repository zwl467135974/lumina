package io.lumina.notification.service;

import io.lumina.common.core.PageResult;
import io.lumina.notification.api.dto.NotificationQueryDTO;
import io.lumina.notification.api.vo.NotificationVO;
import io.lumina.notification.event.NotificationEvent;
import reactor.core.publisher.Flux;

/**
 * 通知中心服务
 *
 * <p>提供站内通知的查询、已读管理、MQ 事件处理与 SSE 实时推送能力。
 *
 * @author Lumina Team
 * @since 1.0.0
 */
public interface NotificationService {

    /**
     * 分页查询当前用户的通知列表
     *
     * @param query 查询条件（分类、已读、分页）
     * @return 分页通知列表
     */
    PageResult<NotificationVO> list(NotificationQueryDTO query);

    /**
     * 获取当前用户的未读通知数量
     *
     * @return 未读数量
     */
    long getUnreadCount();

    /**
     * 标记指定通知为已读
     *
     * @param id 通知 ID
     */
    void markAsRead(Long id);

    /**
     * 将当前用户的所有未读通知标记为已读
     */
    void markAllAsRead();

    /**
     * 处理通知事件（MQ Consumer 调用）
     *
     * <p>持久化通知并推送到在线用户的 SSE 流。
     *
     * @param event 通知事件
     */
    void handleEvent(NotificationEvent event);

    /**
     * 订阅指定用户的通知 SSE 流
     *
     * @param userId 用户 ID
     * @return 通知事件流
     */
    Flux<NotificationVO> subscribeStream(Long userId);
}
