package io.lumina.notification.service;

import io.lumina.framework.cache.RedisCacheManager;
import io.lumina.notification.infrastructure.entity.WebhookDO;
import io.lumina.notification.infrastructure.mapper.WebhookMapper;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * IM 渠道发送器抽象基类
 *
 * <p>抽取各渠道 sender 的公共逻辑：
 * <ul>
 *   <li>{@link #recordResult}：发送结果写回 webhook 状态（连续失败 5 次自动禁用）</li>
 *   <li>{@link #acquireRateQuota}：Redis 计数限频（超限丢弃，不计失败）</li>
 *   <li>{@link #truncate}：正文截断</li>
 *   <li>{@link #chunkByBytes}：按 UTF-8 字节分片（保证不截断多字节字符）</li>
 * </ul>
 *
 * <p>子类只需实现渠道相关部分：限频数、限频 key 前缀、消息构造、响应判断。
 * 子类通过 {@link #webhookMapper} / {@link #redisCacheManager} 访问基础设施。
 *
 * @author Lumina Team
 * @since 3.5.0
 */
@Slf4j
public abstract class AbstractNotificationSender {

    /**
     * 连续失败自动禁用阈值（所有渠道一致）
     */
    protected static final int MAX_FAIL_COUNT = 5;

    /**
     * 限频窗口：默认 60 秒（分钟级限频）；秒级限频子类覆盖 {@link #rateWindowSeconds()} 返回 1
     */
    protected static final long RATE_WINDOW_SECONDS_DEFAULT = 60;

    protected final WebhookMapper webhookMapper;
    protected final RedisCacheManager redisCacheManager;

    protected AbstractNotificationSender(WebhookMapper webhookMapper, RedisCacheManager redisCacheManager) {
        this.webhookMapper = webhookMapper;
        this.redisCacheManager = redisCacheManager;
    }

    /**
     * 本渠道限频阈值（每窗口最大条数）
     */
    protected abstract int rateLimitPerWindow();

    /**
     * 限频计数 key 前缀（如 "wecom:rate:" / "dingtalk:rate:"）
     */
    protected abstract String rateKeyPrefix();

    /**
     * 限频窗口秒数（默认 60 秒；飞书 5/s 等秒级限频返回 1）
     */
    protected long rateWindowSeconds() {
        return RATE_WINDOW_SECONDS_DEFAULT;
    }

    /**
     * 本渠道名称（用于日志，如 "企微" / "钉钉"）
     */
    protected abstract String channelName();

    // ==================== 公共方法 ====================

    /**
     * 发送结果写回 webhook 状态（连续失败达 {@value MAX_FAIL_COUNT} 次自动禁用）
     */
    protected void recordResult(WebhookDO webhook, boolean success, String error) {
        if (success) {
            webhookMapper.updateStatus(webhook.getId(), "SUCCESS", null, 0, null);
            return;
        }
        int newFailCount = (webhook.getFailCount() != null ? webhook.getFailCount() : 0) + 1;
        boolean autoDisable = newFailCount >= MAX_FAIL_COUNT;
        webhookMapper.updateStatus(webhook.getId(), "FAILED", error,
                autoDisable ? 0 : newFailCount, autoDisable ? 0 : null);
        if (autoDisable) {
            log.warn("{} webhook [{}] 连续失败 {} 次已自动禁用", channelName(), webhook.getId(), MAX_FAIL_COUNT);
        }
    }

    /**
     * Redis 计数限频：窗口内累加，超限拒绝。Redis 异常时降级放行（软限频）。
     *
     * @param rateKey 限频维度 key（如机器人 access_token / chat_id）
     * @return true 允许发送，false 触发限频
     */
    protected boolean acquireRateQuota(String rateKey) {
        try {
            String fullKey = rateKeyPrefix() + rateKey;
            long count = redisCacheManager.incrementAndGet(fullKey);
            if (count == 1) {
                redisCacheManager.expire(fullKey, Duration.ofSeconds(rateWindowSeconds()));
            }
            return count <= rateLimitPerWindow();
        } catch (Exception e) {
            // Redis 故障时不阻断通知（软限频降级放行）
            log.warn("{}限频计数失败，降级放行: {}", channelName(), e.getMessage());
            return true;
        }
    }

    /**
     * 正文截断（超过 maxLength 加 "..." 后缀）
     */
    protected String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    /**
     * 按 UTF-8 字节数分片（保证不截断多字节字符）
     *
     * <p>用于企微 4096 字节限制等场景。
     */
    protected List<String> chunkByBytes(String text, int maxBytes) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int currentBytes = 0;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            String ch = new String(Character.toChars(codePoint));
            int chBytes = ch.getBytes(StandardCharsets.UTF_8).length;
            if (currentBytes + chBytes > maxBytes && current.length() > 0) {
                chunks.add(current.toString());
                current = new StringBuilder();
                currentBytes = 0;
            }
            current.append(ch);
            currentBytes += chBytes;
            i += Character.charCount(codePoint);
        }
        if (current.length() > 0) {
            chunks.add(current.toString());
        }
        return chunks;
    }
}
