package io.lumina.notification.service;

import io.lumina.notification.api.vo.NotificationVO;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.MessageListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 通知 SSE 注册中心（按用户 ID 隔离 + Redis Pub/Sub 跨实例广播）
 *
 * <p>基于 Reactor {@link Sinks.Many} 管理每个用户的通知事件流，连接 MQ 事件源与 SSE 响应式推送。
 *
 * <p>策略说明：
 * <ul>
 *   <li>采用 {@code unicast()} 策略（非 replay）——通知无需补放历史，持久化层已保证未在线用户可在重连后查询历史</li>
 *   <li>按 {@code userId} 隔离 sink，用户不在线时 sink 不存在，{@link #push} 直接跳过</li>
 *   <li>跨实例广播：{@link #push} 先 publish 到 Redis Topic，其他实例的 listener 收到后调用 {@link #pushLocal} 投递给本地 sink；
 *       本地实例直接调用 {@link #pushLocal}，避免重复广播形成环路</li>
 * </ul>
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Slf4j
@Component
public class NotificationSseRegistry {

    /**
     * Redis Pub/Sub Topic 名称
     */
    private static final String TOPIC_NOTIFICATION = "lumina:notification";

    /**
     * 按用户 ID 隔离的通知事件 sink
     */
    private final ConcurrentHashMap<Long, Sinks.Many<NotificationVO>> sinks = new ConcurrentHashMap<>();

    private final RedissonClient redissonClient;

    /**
     * Redis Topic 订阅监听器引用（用于销毁时注销）
     */
    private final MessageListener<NotificationVO> topicListener;

    /**
     * Redis Topic 句柄（用于销毁时移除监听器）
     */
    private final RTopic topic;

    /**
     * 构造时注入 RedissonClient 并立即注册跨实例广播监听器。
     *
     * <p>listener 收到远端实例发布的通知后，调用 {@link #pushLocal} 投递到本地 sink，
     * 不再 publish，避免广播环路。
     */
    public NotificationSseRegistry(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
        this.topic = redissonClient.getTopic(TOPIC_NOTIFICATION);

        MessageListener<NotificationVO> listener = (channel, vo) -> {
            if (vo == null || vo.getUserId() == null) {
                return;
            }
            pushLocal(vo.getUserId(), vo);
        };
        this.topicListener = listener;
        this.topic.addListener(NotificationVO.class, listener);
        log.info("通知 SSE Redis Pub/Sub 监听器已注册: topic={}", TOPIC_NOTIFICATION);
    }

    /**
     * 订阅指定用户的通知事件流
     *
     * <p>若该用户已有 sink 则复用，否则新建（unicast 策略，不补放历史）。
     *
     * @param userId 用户 ID
     * @return 通知事件流
     */
    public Flux<NotificationVO> subscribe(Long userId) {
        Sinks.Many<NotificationVO> sink = sinks.computeIfAbsent(userId, k -> {
            Sinks.Many<NotificationVO> s = Sinks.many().unicast().onBackpressureBuffer();
            log.debug("用户 SSE 流创建: userId={}", userId);
            return s;
        });
        return sink.asFlux();
    }

    /**
     * 推送通知给指定用户（发布到 Redis Topic，所有实例共享）
     *
     * <p>持久化已由调用方保证；用户不在线时（本地无 sink）仅记录跳过，
     * 远端实例若也无可跳过，不影响数据一致性。
     *
     * @param userId 用户 ID
     * @param vo     通知 VO
     */
    public void push(Long userId, NotificationVO vo) {
        // 先投递到本地 sink，再广播到 Redis（避免远端监听器延迟）
        pushLocal(userId, vo);
        topic.publish(vo);
    }

    /**
     * 仅投递到本地 sink（不广播），供 Redis 监听器与本地推送共用
     */
    private void pushLocal(Long userId, NotificationVO vo) {
        Sinks.Many<NotificationVO> sink = sinks.get(userId);
        if (sink == null) {
            // 用户不在线，持久化已保证不丢，直接跳过
            return;
        }
        sink.tryEmitNext(vo);
        log.debug("通知已推送至本地 SSE: userId={}, id={}", userId, vo.getId());
    }

    /**
     * 取消订阅并关闭指定用户的 SSE 流（SSE 断开时调用）
     *
     * @param userId 用户 ID
     */
    public void unsubscribe(Long userId) {
        Sinks.Many<NotificationVO> sink = sinks.remove(userId);
        if (sink != null) {
            sink.tryEmitComplete();
            log.debug("用户 SSE 流已关闭: userId={}", userId);
        }
    }

    /**
     * 销毁时清理所有 sink 并注销 Redis 监听器
     */
    @PreDestroy
    public void shutdown() {
        sinks.values().forEach(Sinks.Many::tryEmitComplete);
        sinks.clear();
        if (topicListener != null) {
            try {
                topic.removeListener(topicListener);
            } catch (Exception e) {
                log.warn("注销 Redis Pub/Sub 监听器失败", e);
            }
        }
        log.info("通知 SSE 注册中心已关闭");
    }
}
