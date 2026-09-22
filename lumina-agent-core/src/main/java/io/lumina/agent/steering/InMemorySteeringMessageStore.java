package io.lumina.agent.steering;

import io.lumina.agent.config.LuminaAgentProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 内存版转向消息存储（默认实现，单实例语义）
 *
 * <p>会话 → 队列的并发 Map。消费点轮询极轻（队列头 peek 即可判空），
 * 未消费的遗留消息按会话惰性清理（见 {@link #drain}）。
 *
 * <p>多实例部署的限制与替换方式见 {@link SteeringMessageStore}。
 *
 * @author Lumina Team
 * @since 3.14.0
 */
@Slf4j
@Component
public class InMemorySteeringMessageStore implements SteeringMessageStore {

    private static final int MAX_TRACKED_CONVERSATIONS = 10_000;

    private final LuminaAgentProperties agentProperties;

    private final ConcurrentHashMap<String, Queue<String>> queues = new ConcurrentHashMap<>();

    public InMemorySteeringMessageStore(LuminaAgentProperties agentProperties) {
        this.agentProperties = agentProperties;
    }

    @Override
    public void offer(String conversationId, String message) {
        if (conversationId == null || conversationId.isBlank()) {
            log.warn("转向消息缺少会话标识，拒绝: 长度={}", message != null ? message.length() : 0);
            return;
        }
        if (message == null || message.isBlank()) {
            return;
        }
        String capped = message;
        int maxChars = agentProperties.getSteering().getMaxMessageChars();
        if (message.length() > maxChars) {
            capped = message.substring(0, maxChars) + "…[转向消息超长已截断]";
            log.warn("转向消息超长（{} -> {} 字符）已截断: conversationId={}", message.length(), maxChars, conversationId);
        }
        if (queues.size() >= MAX_TRACKED_CONVERSATIONS && !queues.containsKey(conversationId)) {
            log.warn("转向消息会话数超上限（{}），拒绝新会话队列: conversationId={}",
                    MAX_TRACKED_CONVERSATIONS, conversationId);
            return;
        }
        queues.computeIfAbsent(conversationId, k -> new ConcurrentLinkedQueue<>()).add(capped);
        log.info("转向消息已入队: conversationId={}, 长度={}", conversationId, capped.length());
    }

    @Override
    public List<String> drain(String conversationId, int max) {
        if (conversationId == null || max <= 0) {
            return List.of();
        }
        Queue<String> queue = queues.get(conversationId);
        if (queue == null) {
            return List.of();
        }
        List<String> drained = new ArrayList<>();
        String item;
        while (drained.size() < max && (item = queue.poll()) != null) {
            drained.add(item);
        }
        if (queue.isEmpty()) {
            queues.remove(conversationId, queue);
        }
        return drained;
    }
}
