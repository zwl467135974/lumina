package io.lumina.agent.memory;

import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.State;
import io.lumina.framework.cache.RedisCacheManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 基于 Redis 的 AgentStateStore——跨实例共享 Agent 状态
 *
 * <p>AgentScope 2.0 的 AgentState 有 toJson()/fromJsonString()，
 * 直接存到 Redis，任何实例都能读取。实现多实例部署的会话状态共享。
 *
 * <p>Key 规范：lumina:agent:state:{userId}:{sessionId}:{stateKey}
 * TTL：7 天（对话记忆有效期）
 *
 * @author Lumina Team
 * @since 3.7.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnClass(name = "io.agentscope.core.state.AgentStateStore")
public class RedisAgentStateStore implements AgentStateStore {

    private final RedisCacheManager redisCacheManager;

    private static final String KEY_PREFIX = "lumina:agent:state:";
    private static final Duration TTL = Duration.ofDays(7);
    private static final String DEFAULT_STATE_KEY = "agent_state";

    /**
     * 构建 Redis Key
     */
    private String buildKey(String userId, String sessionId, String key) {
        return KEY_PREFIX + userId + ":" + sessionId + ":" + (key != null ? key : DEFAULT_STATE_KEY);
    }

    @Override
    public void save(String userId, String sessionId, String key, State state) {
        if (state == null) return;
        String redisKey = buildKey(userId, sessionId, key);
        try {
            if (state instanceof AgentState agentState) {
                String json = agentState.toJson();
                redisCacheManager.set(redisKey, json, TTL);
                log.info("AgentState 已保存: userId={}, sessionId={}, key={}, bytes={}",
                        userId, sessionId, key, json.length());
            } else {
                log.warn("AgentState 保存跳过: state 类型不是 AgentState ({}), userId={}, sessionId={}",
                        state.getClass().getSimpleName(), userId, sessionId);
            }
        } catch (Exception e) {
            log.warn("AgentState 保存失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public void save(String userId, String sessionId, String key, List<? extends State> states) {
        // AgentState 是整体存储的（包含 context 列表），不支持拆分存储单条 State
        // 这个方法在 Lumina 场景下不会单独调用（AgentState 整体存取）
        if (states == null || states.isEmpty()) return;
        // 取第一条作为整体 AgentState 存储
        save(userId, sessionId, key, states.get(0));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends State> Optional<T> get(String userId, String sessionId, String key, Class<T> type) {
        String redisKey = buildKey(userId, sessionId, key);
        try {
            String json = redisCacheManager.get(redisKey);
            if (json == null) {
                return Optional.empty();
            }
            AgentState agentState = AgentState.fromJsonString(json);
            if (type.isInstance(agentState)) {
                return Optional.of((T) agentState);
            }
            return Optional.empty();
        } catch (Exception e) {
            log.warn("AgentState 读取失败: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends State> List<T> getList(String userId, String sessionId, String key, Class<T> type) {
        // AgentState 整体存储，返回单元素列表
        Optional<T> opt = get(userId, sessionId, key, type);
        return opt.map(List::of).orElse(Collections.emptyList());
    }

    @Override
    public boolean exists(String userId, String sessionId) {
        // 检查是否有任意 key 存在（用默认 key）
        String redisKey = buildKey(userId, sessionId, DEFAULT_STATE_KEY);
        return redisCacheManager.exists(redisKey);
    }

    @Override
    public void delete(String userId, String sessionId) {
        // 删除该会话的所有状态（用 pattern 匹配）
        String pattern = KEY_PREFIX + userId + ":" + sessionId + ":*";
        redisCacheManager.deleteByPattern(pattern);
        log.debug("AgentState 已删除: userId={}, sessionId={}", userId, sessionId);
    }

    @Override
    public void delete(String userId, String sessionId, String key) {
        String redisKey = buildKey(userId, sessionId, key);
        redisCacheManager.delete(redisKey);
    }

    @Override
    public Set<String> listSessionIds(String userId) {
        // 扫描该用户的所有 session（pattern 匹配 key 的中间段）
        String pattern = KEY_PREFIX + userId + ":*";
        // RedisCacheManager 没有 keys/scan 方法，这里返回空集
        // 后续可以加 scan 方法或用 Redisson 的 getKeys()
        log.debug("listSessionIds 暂未实现（需要 Redis scan 支持）");
        return Collections.emptySet();
    }
}
