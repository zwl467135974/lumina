package io.lumina.notification.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.lumina.common.core.BaseContext;
import io.lumina.common.core.ErrorCode;
import io.lumina.common.core.PageResult;
import io.lumina.common.exception.BusinessException;
import io.lumina.notification.api.dto.NotificationQueryDTO;
import io.lumina.notification.api.vo.NotificationVO;
import io.lumina.notification.event.NotificationEvent;
import io.lumina.notification.infrastructure.entity.NotificationDO;
import io.lumina.notification.infrastructure.mapper.NotificationMapper;
import io.lumina.notification.service.NotificationService;
import io.lumina.notification.service.NotificationSseRegistry;
import io.lumina.notification.service.WebhookDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 通知中心服务实现
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;
    private final NotificationSseRegistry sseRegistry;

    /**
     * webhook 分发器（可通过 lumina.notification.webhook.enabled=false 关闭，关闭时为 null）
     */
    @Autowired(required = false)
    private WebhookDispatcher webhookDispatcher;

    @Override
    public PageResult<NotificationVO> list(NotificationQueryDTO query) {
        Long userId = currentUserId();
        Long tenantId = currentTenantId();

        LambdaQueryWrapper<NotificationDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(NotificationDO::getUserId, userId);
        wrapper.eq(NotificationDO::getTenantId, tenantId);
        if (query.getCategory() != null && !query.getCategory().isEmpty()) {
            wrapper.eq(NotificationDO::getCategory, query.getCategory());
        }
        if (query.getIsRead() != null) {
            wrapper.eq(NotificationDO::getIsRead, query.getIsRead());
        }
        wrapper.orderByDesc(NotificationDO::getCreateTime);

        int pageNum = query.getPageNum() != null ? query.getPageNum() : 1;
        int pageSize = query.getPageSize() != null ? query.getPageSize() : 20;
        Page<NotificationDO> page = new Page<>(pageNum, pageSize);
        Page<NotificationDO> result = notificationMapper.selectPage(page, wrapper);

        List<NotificationVO> voList = result.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());

        return PageResult.of(voList, result.getTotal(), pageNum, pageSize);
    }

    @Override
    public long getUnreadCount() {
        Long userId = currentUserId();
        Long tenantId = currentTenantId();
        Long count = notificationMapper.selectCount(new LambdaQueryWrapper<NotificationDO>()
                .eq(NotificationDO::getUserId, userId)
                .eq(NotificationDO::getTenantId, tenantId)
                .eq(NotificationDO::getIsRead, 0));
        return count != null ? count : 0L;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAsRead(Long id) {
        Long userId = currentUserId();
        Long tenantId = currentTenantId();

        // 查询确认归属当前用户（租户隔离）
        NotificationDO notif = notificationMapper.selectById(id);
        if (notif == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "通知不存在: " + id);
        }
        if (!userId.equals(notif.getUserId()) || !tenantId.equals(notif.getTenantId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (notif.getIsRead() != null && notif.getIsRead() == 1) {
            return; // 已读不重复标记
        }

        notificationMapper.update(null, new LambdaUpdateWrapper<NotificationDO>()
                .eq(NotificationDO::getId, id)
                .eq(NotificationDO::getUserId, userId)
                .eq(NotificationDO::getTenantId, tenantId)
                .set(NotificationDO::getIsRead, 1)
                .set(NotificationDO::getReadTime, LocalDateTime.now()));
        log.info("通知已标记已读: id={}, userId={}", id, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAllAsRead() {
        Long userId = currentUserId();
        Long tenantId = currentTenantId();

        int affected = notificationMapper.update(null, new LambdaUpdateWrapper<NotificationDO>()
                .eq(NotificationDO::getUserId, userId)
                .eq(NotificationDO::getTenantId, tenantId)
                .eq(NotificationDO::getIsRead, 0)
                .set(NotificationDO::getIsRead, 1)
                .set(NotificationDO::getReadTime, LocalDateTime.now()));
        log.info("全部通知已标记已读: userId={}, affected={}", userId, affected);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleEvent(NotificationEvent event) {
        if (event == null || event.getUserId() == null) {
            log.warn("通知事件缺少必要字段，跳过: {}", event);
            return;
        }

        // 兜底 tenantId（MQ 消费场景上下文可能缺失）
        Long tenantId = event.getTenantId() != null ? event.getTenantId() : currentTenantId();

        NotificationDO notif = new NotificationDO();
        notif.setUserId(event.getUserId());
        notif.setCategory(event.getCategory());
        notif.setTitle(event.getTitle());
        notif.setContent(event.getContent());
        notif.setSeverity(event.getSeverity());
        notif.setRefType(event.getRefType());
        notif.setRefId(event.getRefId());
        notif.setTenantId(tenantId);
        notif.setIsRead(0);
        notif.setCreateTime(LocalDateTime.now());

        notificationMapper.insert(notif);
        log.info("通知已持久化: id={}, userId={}, category={}", notif.getId(), notif.getUserId(), notif.getCategory());

        // 推送到在线用户的 SSE 流
        NotificationVO vo = toVO(notif);
        sseRegistry.push(event.getUserId(), vo);

        // 异步分发到订阅的外部 webhook（失败不影响通知主链）
        if (webhookDispatcher != null) {
            try {
                webhookDispatcher.dispatch(event);
            } catch (Throwable t) {
                log.warn("Webhook 分发失败（不影响通知主链）: {}", t.getMessage());
            }
        }
    }

    @Override
    public Flux<NotificationVO> subscribeStream(Long userId) {
        return sseRegistry.subscribe(userId);
    }

    // ==================== 辅助方法 ====================

    private NotificationVO toVO(NotificationDO notif) {
        NotificationVO vo = new NotificationVO();
        BeanUtils.copyProperties(notif, vo);
        return vo;
    }

    private Long currentUserId() {
        Long userId = BaseContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "无法获取当前用户");
        }
        return userId;
    }

    private Long currentTenantId() {
        Long tenantId = BaseContext.getTenantId();
        return tenantId != null ? tenantId : 0L;
    }
}
