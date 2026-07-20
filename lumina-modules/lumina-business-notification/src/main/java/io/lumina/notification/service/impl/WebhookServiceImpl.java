package io.lumina.notification.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.lumina.common.core.BaseContext;
import io.lumina.common.core.ErrorCode;
import io.lumina.common.exception.BusinessException;
import io.lumina.framework.cache.RedisCacheManager;
import io.lumina.notification.api.dto.CreateWebhookDTO;
import io.lumina.notification.api.vo.WebhookVO;
import io.lumina.notification.domain.enums.NotificationCategory;
import io.lumina.notification.domain.enums.NotificationChannel;
import io.lumina.notification.event.NotificationEvent;
import io.lumina.notification.infrastructure.entity.WebhookDO;
import io.lumina.notification.infrastructure.mapper.WebhookMapper;
import io.lumina.notification.service.DingTalkSender;
import io.lumina.notification.service.FeishuSender;
import io.lumina.notification.service.TelegramSender;
import io.lumina.notification.service.WeComSender;
import io.lumina.notification.service.WebhookSender;
import io.lumina.notification.service.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Webhook 订阅服务实现
 *
 * @author Lumina Team
 * @since 3.4.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookServiceImpl implements WebhookService {

    /**
     * 启用 webhook 查询缓存 TTL
     */
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    /**
     * 缓存 key 前缀（完整格式 webhook:user:{uid}:event:{evt}）
     */
    private static final String CACHE_KEY_PREFIX = "webhook:user:";

    /**
     * 自动生成 secret 的随机字节数（24 字节 Base64 后为 32 字符）
     */
    private static final int SECRET_RANDOM_BYTES = 24;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final WebhookMapper webhookMapper;
    private final RedisCacheManager redisCacheManager;
    private final WebhookSender webhookSender;
    private final WeComSender weComSender;
    private final DingTalkSender dingTalkSender;
    private final FeishuSender feishuSender;
    private final TelegramSender telegramSender;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WebhookVO createWebhook(CreateWebhookDTO dto) {
        Long userId = currentUserId();
        NotificationChannel channel = NotificationChannel.fromName(dto.getChannel());
        String events = normalizeEvents(dto.getEvents());

        WebhookDO webhook = new WebhookDO();
        webhook.setUserId(userId);
        webhook.setTenantId(currentTenantId());
        webhook.setName(dto.getName());
        webhook.setUrl(dto.getUrl());
        webhook.setChannel(channel.name());
        // WE_COM 不需要签名；WEBHOOK 渠道未提供 secret 时自动生成
        if (channel == NotificationChannel.WEBHOOK) {
            webhook.setSecret(dto.getSecret() != null && !dto.getSecret().isBlank()
                    ? dto.getSecret() : generateSecret());
        }
        webhook.setEvents(events);
        webhook.setEnabled(1);
        webhook.setFailCount(0);
        webhook.setCreateTime(LocalDateTime.now());
        webhookMapper.insert(webhook);

        evictCache(userId);
        log.info("Webhook 已创建: id={}, userId={}, channel={}, events={}", webhook.getId(), userId, channel, events);

        // 创建响应返回 secret 明文（仅此一次）
        WebhookVO vo = toVO(webhook);
        vo.setSecret(webhook.getSecret());
        return vo;
    }

    @Override
    public List<WebhookVO> listWebhooks() {
        Long userId = currentUserId();
        List<WebhookDO> webhooks = webhookMapper.selectList(new LambdaQueryWrapper<WebhookDO>()
                .eq(WebhookDO::getUserId, userId)
                .orderByDesc(WebhookDO::getCreateTime));
        return webhooks.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteWebhook(Long id) {
        WebhookDO webhook = getOwnedWebhook(id);
        webhookMapper.deleteById(webhook.getId());
        evictCache(webhook.getUserId());
        log.info("Webhook 已删除: id={}, userId={}", id, webhook.getUserId());
    }

    @Override
    public boolean testWebhook(Long id) {
        WebhookDO webhook = getOwnedWebhook(id);
        NotificationEvent testEvent = new NotificationEvent(
                webhook.getUserId(), "TEST",
                "Webhook 测试", "这是一条来自 Lumina 通知中心的测试消息",
                "INFO", "webhook", String.valueOf(id), webhook.getTenantId());
        return switch (NotificationChannel.fromName(webhook.getChannel())) {
            case WE_COM -> weComSender.send(webhook, testEvent);
            case DINGTALK -> dingTalkSender.send(webhook, testEvent);
            case FEISHU -> feishuSender.send(webhook, testEvent);
            case TELEGRAM -> telegramSender.send(webhook, testEvent);
            case WEBHOOK -> webhookSender.send(webhook, testEvent);
        };
    }

    @Override
    public List<WebhookDO> findEnabledForEvent(Long userId, String event) {
        String cacheKey = cacheKey(userId, event);
        List<WebhookDO> cached = redisCacheManager.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        List<WebhookDO> webhooks = webhookMapper.selectEnabledByUserAndEvent(userId, event);
        redisCacheManager.set(cacheKey, webhooks, CACHE_TTL);
        return webhooks;
    }

    // ==================== 辅助方法 ====================

    /**
     * 查询并校验 webhook 归属当前用户
     */
    private WebhookDO getOwnedWebhook(Long id) {
        WebhookDO webhook = webhookMapper.selectById(id);
        if (webhook == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Webhook 不存在: " + id);
        }
        if (!currentUserId().equals(webhook.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return webhook;
    }

    /**
     * 校验并归一化事件列表（空或含 * 归一为 *，其余必须是合法 NotificationCategory）
     */
    private String normalizeEvents(List<String> events) {
        if (events == null || events.isEmpty() || events.contains("*")) {
            return "*";
        }
        return events.stream()
                .map(e -> NotificationCategory.fromName(e).name())
                .distinct()
                .collect(Collectors.joining(","));
    }

    /**
     * 生成随机 secret（24 随机字节 → 32 字符 Base64）
     */
    private String generateSecret() {
        byte[] bytes = new byte[SECRET_RANDOM_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String cacheKey(Long userId, String event) {
        return CACHE_KEY_PREFIX + userId + ":event:" + event;
    }

    /**
     * 写操作后 evict 该用户所有事件类别的缓存
     */
    private void evictCache(Long userId) {
        redisCacheManager.deleteByPattern(CACHE_KEY_PREFIX + userId + ":event:*");
    }

    private WebhookVO toVO(WebhookDO webhook) {
        WebhookVO vo = new WebhookVO();
        BeanUtils.copyProperties(webhook, vo);
        // secret 不在列表/详情中返回（仅创建响应单独设置）
        vo.setSecret(null);
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
