package io.lumina.notification.api.controller;

import io.lumina.common.core.BaseContext;
import io.lumina.common.core.ErrorCode;
import io.lumina.common.core.PageResult;
import io.lumina.common.core.R;
import io.lumina.common.exception.BusinessException;
import io.lumina.framework.audit.annotation.Audit;
import io.lumina.notification.api.dto.NotificationQueryDTO;
import io.lumina.notification.api.vo.NotificationVO;
import io.lumina.notification.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * 通知中心 Controller
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/notifications")
@Validated
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * 分页查询当前用户的通知列表
     */
    @GetMapping
    public R<PageResult<NotificationVO>> list(@Valid NotificationQueryDTO query) {
        return R.success(notificationService.list(query));
    }

    /**
     * 获取当前用户的未读通知数量
     */
    @GetMapping("/unread-count")
    public R<Map<String, Long>> getUnreadCount() {
        long count = notificationService.getUnreadCount();
        return R.success(Map.of("count", count));
    }

    /**
     * 标记指定通知为已读
     */
    @PutMapping("/{id}/read")
    @Audit(module = "notification", action = "READ", description = "标记通知已读")
    public R<Void> markAsRead(@PathVariable("id") Long id) {
        log.info("标记通知已读: id={}", id);
        notificationService.markAsRead(id);
        return R.success();
    }

    /**
     * 将当前用户的所有未读通知标记为已读
     */
    @PutMapping("/read-all")
    @Audit(module = "notification", action = "READ_ALL", description = "全部标记已读")
    public R<Void> markAllAsRead() {
        log.info("全部通知标记已读");
        notificationService.markAllAsRead();
        return R.success();
    }

    /**
     * 订阅通知 SSE 流
     *
     * <p>连接后实时接收当前用户的通知推送事件（event 字段为 "notification"）。
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<NotificationVO>> stream() {
        Long userId = BaseContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "无法获取当前用户");
        }
        log.info("SSE 订阅通知流: userId={}", userId);

        return notificationService.subscribeStream(userId)
                .map(vo -> ServerSentEvent.<NotificationVO>builder()
                        .event("notification")
                        .data(vo)
                        .build());
    }
}
